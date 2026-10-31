package a6a.dr.si.auth;

import a6a.dr.Si;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家账号存储：config/si/accounts.json
 *
 * <p>密码使用 PBKDF2-HMAC-SHA256（随机盐 + 15 万次迭代）保存，不存明文。</p>
 */
public final class AccountStore {
	public static final class Account {
		public String name;
		public String uuid;
		public String salt;
		public String hash;
		public long createdAt;
		public long lastLogin;

		public Account() {
		}
	}

	private static final int ITERATIONS = 150_000;
	private static final int KEY_BITS = 256;
	private static final int SALT_BYTES = 16;
	private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Account>>() {
	}.getType();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Path file;
	private final Map<String, Account> accounts = new ConcurrentHashMap<>();

	public AccountStore(Path file) {
		this.file = file;
		load();
	}

	private static String key(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	public static String validatePassword(String password) {
		if (password == null || password.length() < 6) return "密码至少 6 位";
		if (password.length() > 64) return "密码最长 64 位";
		return null;
	}

	public boolean exists(String name) {
		return accounts.containsKey(key(name));
	}

	public Account get(String name) {
		return accounts.get(key(name));
	}

	public Account getByUuid(UUID uuid) {
		if (uuid == null) return null;
		String id = uuid.toString();
		for (Account account : accounts.values()) {
			if (id.equals(account.uuid)) return account;
		}
		return null;
	}

	public int size() {
		return accounts.size();
	}

	/**
	 * 创建账号。
	 *
	 * @return null 表示成功，否则返回错误提示
	 */
	public synchronized String create(String name, UUID uuid, String password) {
		String cleanName = name == null ? "" : name.trim();
		if (cleanName.isEmpty()) return "玩家名不能为空";
		String error = validatePassword(password);
		if (error != null) return error;
		if (exists(cleanName)) return "该玩家已经设置过密码";

		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		Account account = new Account();
		account.name = cleanName;
		account.uuid = uuid == null ? "" : uuid.toString();
		account.salt = Base64.getEncoder().encodeToString(salt);
		account.hash = Base64.getEncoder().encodeToString(hash(password, salt));
		account.createdAt = System.currentTimeMillis();
		account.lastLogin = 0L;
		accounts.put(key(cleanName), account);
		save();
		return null;
	}

	public synchronized String resetPassword(String name, String password) {
		Account account = get(name);
		if (account == null) return "该玩家还没有设置密码";
		String error = validatePassword(password);
		if (error != null) return error;
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		account.salt = Base64.getEncoder().encodeToString(salt);
		account.hash = Base64.getEncoder().encodeToString(hash(password, salt));
		save();
		return null;
	}

	public synchronized boolean verify(String name, String password) {
		Account account = get(name);
		if (account == null || password == null) return false;
		byte[] salt;
		byte[] expected;
		try {
			salt = Base64.getDecoder().decode(account.salt);
			expected = Base64.getDecoder().decode(account.hash);
		} catch (Exception e) {
			return false;
		}
		byte[] actual = hash(password, salt);
		if (!MessageDigest.isEqual(expected, actual)) return false;
		account.lastLogin = System.currentTimeMillis();
		save();
		return true;
	}

	public synchronized void remove(String name) {
		if (accounts.remove(key(name)) != null) save();
	}

	private static byte[] hash(String password, byte[] salt) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		} catch (Exception e) {
			throw new IllegalStateException("密码哈希失败", e);
		}
	}

	private void load() {
		if (!Files.exists(file)) return;
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			Map<String, Account> loaded = GSON.fromJson(json, MAP_TYPE);
			if (loaded != null) {
				accounts.clear();
				loaded.forEach((k, v) -> {
					if (v != null && v.name != null) accounts.put(key(v.name), v);
				});
			}
		} catch (Exception e) {
			Si.LOGGER.warn("读取账号数据失败: {}", file, e);
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			Map<String, Account> snapshot = new LinkedHashMap<>(accounts);
			Path tmp = file.resolveSibling("accounts.json.tmp");
			Files.writeString(tmp, GSON.toJson(snapshot, MAP_TYPE), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			Si.LOGGER.warn("保存账号数据失败", e);
		}
	}
}

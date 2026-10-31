package a6a.dr.si.music;

import a6a.dr.Si;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家绑定的网易云登录态（MUSIC_U Cookie）。
 *
 * <p>存到 config/si/netease.json，和游戏账号一一对应；
 * 只是会话票据，不是密码。</p>
 */
public final class NeteaseStore {
	public static final class Entry {
		public String musicU;
		public String nickname;
		public long uid;
		public long savedAt;
	}

	private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Entry>>() {
	}.getType();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	public NeteaseStore(Path file) {
		this.file = file;
		load();
	}

	private static String key(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	public Entry get(String mcName) {
		return entries.get(key(mcName));
	}

	public String cookie(String mcName) {
		Entry entry = get(mcName);
		return entry == null || entry.musicU == null || entry.musicU.isBlank() ? null : entry.musicU;
	}

	public void put(String mcName, Entry entry) {
		if (entry != null) {
			entries.put(key(mcName), entry);
			save();
		}
	}

	public void remove(String mcName) {
		if (entries.remove(key(mcName)) != null) save();
	}

	private void load() {
		if (!Files.exists(file)) return;
		try {
			Map<String, Entry> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
			if (loaded != null) {
				entries.clear();
				entries.putAll(loaded);
			}
		} catch (Exception e) {
			Si.LOGGER.warn("读取网易云登录态失败", e);
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("netease.json.tmp");
			Files.writeString(tmp, GSON.toJson(new LinkedHashMap<>(entries), MAP_TYPE), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			Si.LOGGER.warn("保存网易云登录态失败", e);
		}
	}
}

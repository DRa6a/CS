package a6a.dr.si;

import a6a.dr.Si;
import a6a.dr.si.auth.AccountStore;
import a6a.dr.si.auth.SessionStore;
import a6a.dr.si.message.ChatService;
import a6a.dr.si.music.FallbackSource;
import a6a.dr.si.music.MusicService;
import a6a.dr.si.music.NeteaseStore;
import a6a.dr.si.music.RequestLog;
import a6a.dr.si.web.WebServer;
import net.minecraft.server.MinecraftServer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局单例容器。HTTP 线程与服务端线程都通过它取到共享组件。
 */
public final class SiCore {
	private static SiConfig config;
	private static AccountStore accounts;
	private static SessionStore sessions;
	private static MusicService music;
	private static RequestLog requests;
	private static NeteaseStore netease;
	private static FallbackSource fallback;
	private static ChatService chat;

	public static volatile MinecraftServer server;
	public static volatile WebServer web;

	private static final Map<UUID, Float> VOLUMES = new ConcurrentHashMap<>();
	private static final long LAN_TTL_MS = 60_000L;

	private static volatile String cachedLan;
	private static volatile long cachedLanAt;

	private SiCore() {
	}

	public static void init(SiConfig cfg, AccountStore acc, SessionStore sess, MusicService mus,
							RequestLog req, NeteaseStore neteaseStore, FallbackSource fallbackSource,
							ChatService chatService) {
		config = cfg;
		accounts = acc;
		sessions = sess;
		music = mus;
		requests = req;
		netease = neteaseStore;
		fallback = fallbackSource;
		chat = chatService;
	}

	public static ChatService chat() {
		return chat;
	}

	public static NeteaseStore netease() {
		return netease;
	}

	public static FallbackSource fallback() {
		return fallback;
	}

	public static SiConfig config() {
		return config;
	}

	public static AccountStore accounts() {
		return accounts;
	}

	public static SessionStore sessions() {
		return sessions;
	}

	public static MusicService music() {
		return music;
	}

	public static RequestLog requests() {
		return requests;
	}

	/**
	 * 显示给玩家的点歌台地址（{@code si.json} 的 {@code displayUrl}）。
	 *
	 * <p>留空就自动探测内网 IP。只写域名（mc.example.com）也能用，会自动补 {@code http://}，
	 * 这样游戏里那一行才是可点的链接。</p>
	 */
	public static String baseUrl() {
		SiConfig cfg = config;
		if (cfg == null) return "http://127.0.0.1:8765";
		if (cfg.displayUrl != null && !cfg.displayUrl.isBlank()) return normalizeUrl(cfg.displayUrl);
		return "http://" + lanAddress() + ":" + cfg.port;
	}

	private static String normalizeUrl(String raw) {
		String url = raw.trim();
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			url = "http://" + url;
		}
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	/**
	 * 内网地址。
	 *
	 * <p>{@code NetworkInterface.getNetworkInterfaces()} 是系统调用，Windows 上动辄几毫秒，
	 * 而 /api/me 每次刷新页面都会问一次，所以缓存 60 秒。</p>
	 */
	private static String lanAddress() {
		long now = System.currentTimeMillis();
		String cached = cachedLan;
		if (cached != null && now - cachedLanAt < LAN_TTL_MS) return cached;
		String found = resolveLanAddress();
		cachedLan = found;
		cachedLanAt = now;
		return found;
	}

	private static String resolveLanAddress() {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface ni = interfaces.nextElement();
				if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
				Enumeration<InetAddress> addresses = ni.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress address = addresses.nextElement();
					if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
						return address.getHostAddress();
					}
				}
			}
		} catch (Exception e) {
			Si.LOGGER.debug("获取内网地址失败", e);
		}
		return "127.0.0.1";
	}

	public static float volumeOf(UUID uuid) {
		if (uuid == null) return config == null ? 0.8f : config.defaultVolume;
		Float value = VOLUMES.get(uuid);
		if (value != null) return value;
		return config == null ? 0.8f : config.defaultVolume;
	}

	public static void setVolume(UUID uuid, float value) {
		if (uuid != null) VOLUMES.put(uuid, Math.max(0f, Math.min(1f, value)));
	}

	public static UUID uuidByName(String name) {
		AccountStore.Account account = accounts == null ? null : accounts.get(name);
		if (account == null || account.uuid == null) return null;
		try {
			return UUID.fromString(account.uuid);
		} catch (Exception e) {
			return null;
		}
	}
}

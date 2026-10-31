package a6a.dr.si;

import a6a.dr.Si;
import a6a.dr.si.auth.AccountStore;
import a6a.dr.si.auth.SessionStore;
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

	public static volatile MinecraftServer server;
	public static volatile WebServer web;

	private static final Map<UUID, Float> VOLUMES = new ConcurrentHashMap<>();

	private SiCore() {
	}

	public static void init(SiConfig cfg, AccountStore acc, SessionStore sess, MusicService mus,
							RequestLog req, NeteaseStore neteaseStore, FallbackSource fallbackSource) {
		config = cfg;
		accounts = acc;
		sessions = sess;
		music = mus;
		requests = req;
		netease = neteaseStore;
		fallback = fallbackSource;
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

	/** 网页访问地址（给玩家看的） */
	public static String baseUrl() {
		SiConfig cfg = config;
		if (cfg == null) return "http://127.0.0.1:8765";
		if (cfg.publicUrl != null && !cfg.publicUrl.isBlank()) {
			String url = cfg.publicUrl.trim();
			return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
		}
		return "http://" + lanAddress() + ":" + cfg.port;
	}

	private static String lanAddress() {
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

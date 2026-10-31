package a6a.dr.si.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线玩家快照。HTTP 线程不能直接读 Minecraft 的服务端状态，
 * 所以进服 / 退服时维护一份只读快照供网页查询。
 */
public final class OnlineRegistry {
	public record Info(UUID uuid, String name, boolean hasCu) {
	}

	private static final Map<UUID, Info> ONLINE = new ConcurrentHashMap<>();

	private OnlineRegistry() {
	}

	public static void put(Info info) {
		ONLINE.put(info.uuid(), info);
	}

	public static void remove(UUID uuid) {
		if (uuid != null) ONLINE.remove(uuid);
	}

	public static Info get(UUID uuid) {
		return uuid == null ? null : ONLINE.get(uuid);
	}

	public static Info byName(String name) {
		if (name == null) return null;
		String key = name.toLowerCase(Locale.ROOT);
		for (Info info : ONLINE.values()) {
			if (info.name().toLowerCase(Locale.ROOT).equals(key)) return info;
		}
		return null;
	}

	public static List<String> names() {
		List<String> list = new ArrayList<>();
		ONLINE.values().forEach(info -> list.add(info.name()));
		return list;
	}

	public static List<Info> all() {
		return new ArrayList<>(ONLINE.values());
	}

	public static int size() {
		return ONLINE.size();
	}

	public static void clear() {
		ONLINE.clear();
	}
}

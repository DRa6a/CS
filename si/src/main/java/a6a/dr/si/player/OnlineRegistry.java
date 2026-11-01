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
 *
 * <p>按名字查用一张小写索引表，避免每次点歌都把所有在线玩家扫一遍。</p>
 */
public final class OnlineRegistry {
	public record Info(UUID uuid, String name, boolean hasCu) {
	}

	private static final Map<UUID, Info> ONLINE = new ConcurrentHashMap<>();
	private static final Map<String, Info> BY_NAME = new ConcurrentHashMap<>();

	private OnlineRegistry() {
	}

	public static void put(Info info) {
		Info old = ONLINE.put(info.uuid(), info);
		if (old != null) BY_NAME.remove(lower(old.name()), old);
		BY_NAME.put(lower(info.name()), info);
	}

	public static void remove(UUID uuid) {
		if (uuid == null) return;
		Info old = ONLINE.remove(uuid);
		if (old != null) BY_NAME.remove(lower(old.name()), old);
	}

	public static Info get(UUID uuid) {
		return uuid == null ? null : ONLINE.get(uuid);
	}

	public static Info byName(String name) {
		return name == null ? null : BY_NAME.get(lower(name));
	}

	private static String lower(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
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
		BY_NAME.clear();
	}
}

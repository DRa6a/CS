package a6a.dr.si.message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 服务器事件流（内存环形缓冲）。
 *
 * <p>聊天之外还记进出服、死亡、执行命令这些，网页和外部页面可以按类型订阅。
 * 记录哪几类由 {@code si.json} 的 {@code eventTypes} 决定，总开关是 {@code eventsEnabled}。</p>
 *
 * <p>和聊天记录一样不落盘：重启清空。</p>
 */
public final class EventLog {
	public static final String CHAT = "chat";
	public static final String WEB = "web";
	public static final String JOIN = "join";
	public static final String QUIT = "quit";
	public static final String DEATH = "death";
	public static final String COMMAND = "command";

	/** 支持的事件类型，顺序即文档里的顺序 */
	public static final List<String> ALL = List.of(CHAT, WEB, JOIN, QUIT, DEATH, COMMAND);

	public static final class Event {
		public long id;
		public long at;
		public String type = CHAT;
		public String player = "";
		public String text = "";
		/** 附加信息：命令的来源（player/server）、死亡的伤害类型 id 等，没有就是 null */
		public String detail;
	}

	private final int capacity;
	private final Set<String> enabled = new LinkedHashSet<>();
	private final Deque<Event> events = new ArrayDeque<>();
	private long seq;

	public EventLog(int capacity, Collection<String> types) {
		this.capacity = Math.max(50, capacity);
		if (types != null) {
			for (String type : types) {
				if (type == null) continue;
				String value = type.trim().toLowerCase(Locale.ROOT);
				if (ALL.contains(value)) enabled.add(value);
			}
		}
	}

	/** 这一类事件是否要记 */
	public boolean records(String type) {
		return enabled.contains(type);
	}

	public Set<String> types() {
		return Set.copyOf(enabled);
	}

	public Event add(String type, String player, String text) {
		return add(type, player, text, null);
	}

	/**
	 * 追加一条事件。类型没开启时直接返回 null，调用方不用自己判断。
	 */
	public synchronized Event add(String type, String player, String text, String detail) {
		if (type == null || !enabled.contains(type)) return null;
		Event event = new Event();
		event.id = ++seq;
		event.at = System.currentTimeMillis();
		event.type = type;
		event.player = player == null ? "" : player;
		event.text = text == null ? "" : text;
		event.detail = detail;
		events.addLast(event);
		while (events.size() > capacity) {
			events.removeFirst();
		}
		return event;
	}

	/**
	 * 按时间正序取事件。
	 *
	 * @param since 只取 id 大于它的（增量拉取），0 表示从头
	 * @param types 只取这些类型，null / 空表示不筛选
	 */
	public synchronized List<Event> recent(int limit, long since, Set<String> types) {
		int keep = Math.max(1, Math.min(limit, capacity));
		List<Event> matched = new ArrayList<>();
		for (Event event : events) {
			if (event.id <= since) continue;
			if (types != null && !types.isEmpty() && !types.contains(event.type)) continue;
			matched.add(event);
		}
		if (matched.size() <= keep) return matched;
		// 只要最新的 keep 条
		return new ArrayList<>(matched.subList(matched.size() - keep, matched.size()));
	}

	/** 当前最大 id，客户端拿它当下次的 since */
	public synchronized long latest() {
		return seq;
	}

	public synchronized int size() {
		return events.size();
	}

	public synchronized void clear() {
		events.clear();
	}
}

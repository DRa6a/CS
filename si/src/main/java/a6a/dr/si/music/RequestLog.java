package a6a.dr.si.music;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 点歌记录（内存环形缓冲），网页用来展示「播放中 / 排队中 / 已播完」。
 *
 * <p>全服播放时同一个请求会发给多个客户端，每个客户端各自回报状态。所以这里不是
 * 「最后一条回报覆盖全部」，而是按接收者分别记录，再聚合出一个整体状态：
 * 有人在播 → 播放中；没人播但还有人排队 → 排队中；全部有结果 → 播完 / 切歌 / 失败。</p>
 */
public final class RequestLog {
	public static final String QUEUED = "QUEUED";
	public static final String PLAYING = "PLAYING";
	public static final String DONE = "DONE";
	public static final String FAILED = "FAILED";
	/** 没播完就被切掉 / 被新歌打断 */
	public static final String SKIPPED = "SKIPPED";

	public static final class Entry {
		public String id;
		public String player;
		/** 点给谁听（全服播放时为空） */
		public String target;
		public String title;
		public String artist;
		public String url;
		public boolean broadcast;
		public long requestedAt;
		public String state = QUEUED;
		public String message = "";
		/** 这次请求发给了谁，以及各自最近一次回报的状态（全服播放时会有多个） */
		public final Map<UUID, String> listeners = new LinkedHashMap<>();
		/** 此刻正在播的客户端数，网页用来显示「3 人播放中」 */
		public int playing;
	}

	private final int capacity;
	private final LinkedList<Entry> entries = new LinkedList<>();

	public RequestLog(int capacity) {
		this.capacity = Math.max(5, capacity);
	}

	public synchronized void add(Entry entry) {
		entries.addFirst(entry);
		while (entries.size() > capacity) {
			entries.removeLast();
		}
	}

	/**
	 * 某个客户端回报状态。listener 为 null 时表示不知道是谁（兼容旧客户端）。
	 */
	public synchronized void update(String id, UUID listener, String state, String message) {
		Entry entry = find(id);
		if (entry == null) return;
		if (listener != null && state != null) {
			entry.listeners.put(listener, state);
		}
		if (message != null && !message.isBlank()) {
			entry.message = message;
		}
		recompute(entry);
	}

	/**
	 * 服务端自己下的结论（没发出去 / 玩家离线 / 没有可播客户端）。
	 * 这种条目不会再收到客户端回报，所以直接落终态。
	 */
	public synchronized void finish(String id, String state, String message) {
		Entry entry = find(id);
		if (entry == null) return;
		entry.listeners.clear();
		entry.playing = 0;
		if (state != null) entry.state = state;
		if (message != null) entry.message = message;
	}

	/** 由各接收者的状态聚合出整体状态 */
	private static void recompute(Entry entry) {
		if (entry.listeners.isEmpty()) return;
		int playing = 0;
		int queued = 0;
		int failed = 0;
		int skipped = 0;
		int done = 0;
		for (String state : entry.listeners.values()) {
			switch (state) {
				case PLAYING -> playing++;
				case QUEUED -> queued++;
				case FAILED -> failed++;
				case SKIPPED -> skipped++;
				default -> done++;
			}
		}
		entry.playing = playing;
		if (playing > 0) {
			entry.state = PLAYING;
		} else if (queued > 0) {
			entry.state = QUEUED;
		} else if (failed > 0) {
			entry.state = FAILED;
		} else if (skipped > 0) {
			entry.state = SKIPPED;
		} else {
			entry.state = DONE;
		}
	}

	public synchronized List<Entry> recent(int limit) {
		List<Entry> result = new ArrayList<>();
		for (Entry entry : entries) {
			if (result.size() >= limit) break;
			result.add(entry);
		}
		return result;
	}

	public synchronized List<Entry> recentFor(String player, int limit) {
		List<Entry> result = new ArrayList<>();
		for (Entry entry : entries) {
			if (result.size() >= limit) break;
			if (entry.player.equalsIgnoreCase(player)) result.add(entry);
		}
		return result;
	}

	public synchronized Entry find(String id) {
		for (Entry entry : entries) {
			if (entry.id.equals(id)) return entry;
		}
		return null;
	}
}

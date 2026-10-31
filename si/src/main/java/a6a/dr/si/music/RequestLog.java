package a6a.dr.si.music;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 点歌记录（内存环形缓冲），网页用来展示「播放中 / 排队中 / 失败」。
 */
public final class RequestLog {
	public static final String QUEUED = "QUEUED";
	public static final String PLAYING = "PLAYING";
	public static final String DONE = "DONE";
	public static final String FAILED = "FAILED";

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

	public synchronized void update(String id, String state, String message) {
		if (id == null) return;
		for (Entry entry : entries) {
			if (id.equals(entry.id)) {
				if (state != null) entry.state = state;
				if (message != null) entry.message = message;
				return;
			}
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

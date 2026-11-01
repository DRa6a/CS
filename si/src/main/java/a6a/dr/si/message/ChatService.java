package a6a.dr.si.message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 聊天记录（内存环形缓冲）。
 *
 * <p>游戏里玩家说的话、网页发进来的消息都记在同一张表里，
 * 网页用它渲染「游戏内对话」，靠 {@code source} 区分来源。
 * 不落盘——聊天记录是一次性的，重启清空。</p>
 */
public final class ChatService {
	/** 一条消息最多多少字 */
	public static final int MAX_LENGTH = 120;
	public static final String GAME = "game";
	public static final String WEB = "web";

	public static final class Line {
		public String id;
		public String name;
		public String text;
		public String source = GAME;
		public long at;
	}

	private final int capacity;
	private final Deque<Line> lines = new ArrayDeque<>();
	private long seq;

	public ChatService(int capacity) {
		this.capacity = Math.max(20, capacity);
	}

	/**
	 * 追加一行。
	 *
	 * @param source {@link #GAME} 或 {@link #WEB}
	 */
	public synchronized Line add(String name, String text, String source) {
		Line line = new Line();
		line.id = Long.toString(++seq);
		line.name = name == null ? "" : name;
		line.text = text == null ? "" : text;
		line.source = source == null ? GAME : source;
		line.at = System.currentTimeMillis();
		lines.addLast(line);
		while (lines.size() > capacity) {
			lines.removeFirst();
		}
		return line;
	}

	/** 最近的若干行，按时间正序（老 → 新） */
	public synchronized List<Line> recent(int limit) {
		int keep = Math.max(1, Math.min(limit, lines.size()));
		List<Line> list = new ArrayList<>(keep);
		int skip = lines.size() - keep;
		int index = 0;
		for (Line line : lines) {
			if (index++ < skip) continue;
			list.add(line);
		}
		return list;
	}

	public synchronized int size() {
		return lines.size();
	}

	/**
	 * 清理输入：换行 / 制表符变空格，去掉其它控制字符，压缩连续空格，超长截断。
	 *
	 * <p>游戏内是用 {@code Component.literal} 渲染的，不会解析格式符，
	 * 这里只是让显示干净一点。</p>
	 */
	public static String clean(String text) {
		if (text == null) return "";
		StringBuilder sb = new StringBuilder(Math.min(text.length(), MAX_LENGTH * 2));
		boolean lastSpace = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n' || c == '\r' || c == '\t') {
				if (!lastSpace && sb.length() > 0) {
					sb.append(' ');
					lastSpace = true;
				}
				continue;
			}
			if (c < 0x20 || c == 0x7F) continue;
			if (c == ' ') {
				if (lastSpace || sb.length() == 0) continue;
				lastSpace = true;
			} else {
				lastSpace = false;
			}
			sb.append(c);
			if (sb.length() >= MAX_LENGTH) break;
		}
		return sb.toString().trim();
	}
}

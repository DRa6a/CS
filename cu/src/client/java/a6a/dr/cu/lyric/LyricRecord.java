package a6a.dr.cu.lyric;

import java.util.List;

/**
 * 一首歌的时间轴歌词（原词 + 可选的官方翻译）。
 */
public final class LyricRecord {
	public record Line(long timeMs, String text) {
	}

	private final List<Line> lines;
	private final List<Line> translation;

	public LyricRecord(List<Line> lines, List<Line> translation) {
		this.lines = lines;
		this.translation = translation;
	}

	public List<Line> lines() {
		return lines;
	}

	public List<Line> translation() {
		return translation;
	}

	public boolean isEmpty() {
		return lines == null || lines.isEmpty();
	}

	/** 当前时间落在第几行；早于第一行返回 -1 */
	public int indexAt(long timeMs) {
		if (lines == null || lines.isEmpty()) return -1;
		int idx = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).timeMs() <= timeMs) {
				idx = i;
			} else {
				break;
			}
		}
		return idx;
	}

	public Line lineAt(int index) {
		if (lines == null || index < 0 || index >= lines.size()) return null;
		return lines.get(index);
	}

	public int lineCount() {
		return lines == null ? 0 : lines.size();
	}

	/** 与当前行时间最接近的翻译行（翻译行时间可能和原词不完全对齐） */
	public String translationAt(long timeMs) {
		if (translation == null || translation.isEmpty()) return null;
		String result = null;
		for (Line line : translation) {
			if (line.timeMs() <= timeMs) {
				result = line.text();
			} else {
				break;
			}
		}
		return result;
	}
}

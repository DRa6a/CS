package a6a.dr.cu.lyric;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析网易云歌词接口的 JSON：{ code, lrc:{lyric}, tlyric:{lyric} }。
 * 翻译字段 tlyric 是官方翻译，很多歌自带，无需额外翻译服务。
 */
public final class LyricParser {
	private static final Gson GSON = new Gson();
	private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");

	private LyricParser() {
	}

	public static LyricRecord parse(String json) {
		if (json == null || json.isBlank()) return null;
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			if (root.has("code") && !root.get("code").isJsonNull() && root.get("code").getAsInt() != 200) {
				return null;
			}
			List<LyricRecord.Line> lines = split(lyricText(root, "lrc"));
			if (lines.isEmpty()) return null;
			List<LyricRecord.Line> translation = split(lyricText(root, "tlyric"));
			return new LyricRecord(lines, translation.isEmpty() ? null : translation);
		} catch (Exception e) {
			return null;
		}
	}

	private static String lyricText(JsonObject root, String key) {
		if (!root.has(key) || root.get(key).isJsonNull()) return null;
		try {
			JsonObject obj = root.getAsJsonObject(key);
			if (obj == null || !obj.has("lyric") || obj.get("lyric").isJsonNull()) return null;
			return obj.get("lyric").getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private static List<LyricRecord.Line> split(String content) {
		List<LyricRecord.Line> list = new ArrayList<>();
		if (content == null || content.isBlank()) return list;
		for (String raw : content.split("\n")) {
			Matcher matcher = TAG.matcher(raw);
			List<Long> times = new ArrayList<>(2);
			int lastEnd = 0;
			while (matcher.find()) {
				times.add(toMillis(matcher));
				lastEnd = matcher.end();
			}
			if (times.isEmpty()) continue;
			String text = raw.substring(lastEnd).trim();
			if (text.isEmpty()) continue;
			for (Long time : times) {
				list.add(new LyricRecord.Line(time, text));
			}
		}
		list.sort(Comparator.comparingLong(LyricRecord.Line::timeMs));
		return list;
	}

	private static long toMillis(Matcher matcher) {
		long minutes = Long.parseLong(matcher.group(1));
		long seconds = Long.parseLong(matcher.group(2));
		long millis = 0L;
		String fraction = matcher.group(3);
		if (fraction != null && !fraction.isEmpty()) {
			long raw = Long.parseLong(fraction);
			switch (fraction.length()) {
				case 1 -> millis = raw * 100L;
				case 2 -> millis = raw * 10L;
				default -> millis = raw;
			}
		}
		return minutes * 60_000L + seconds * 1_000L + millis;
	}
}

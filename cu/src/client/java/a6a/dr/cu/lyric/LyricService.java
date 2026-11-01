package a6a.dr.cu.lyric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客户端歌词服务。
 *
 * <p>不给服务端增加负担：直接从 Si 下发的音频直链里抠出网易云歌曲 id
 * （{@code https://music.163.com/song/media/outer/url?id=123.mp3}），
 * 在客户端自己去网易云拉歌词 + 官方翻译；直链里没有 id（绑定账号后的 CDN 地址 / 任意直链）时，
 * 退化为按 歌名+歌手 搜一次。所有请求都在客户端后台线程，带缓存。</p>
 */
public final class LyricService {
	private static final LyricService INSTANCE = new LyricService();

	public static LyricService get() {
		return INSTANCE;
	}

	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
	private static final Pattern ID_PATTERN = Pattern.compile("[?&]id=(\\d+)");

	private final ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Cu-Lyric");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});
	/** 键为歌曲 id（有则用 id），值为解析结果（可能为 empty 表示确实没歌词） */
	private final Map<String, Optional<LyricRecord>> cache = new ConcurrentHashMap<>();

	private volatile String lastRequestId;
	private volatile LyricRecord current;
	private volatile boolean loading;

	/** 每个客户端 tick 调一次；歌曲变了才触发拉取。 */
	public void update(String requestId, String title, String artist, String url) {
		if (requestId == null) {
			if (lastRequestId != null) {
				lastRequestId = null;
				current = null;
				loading = false;
			}
			return;
		}
		if (requestId.equals(lastRequestId)) return;

		lastRequestId = requestId;
		current = null;
		loading = true;

		String id = extractId(url);
		String key = cacheKey(id, title, artist);
		Optional<LyricRecord> cached = cache.get(key);
		if (cached != null) {
			current = cached.orElse(null);
			loading = false;
			return;
		}

		pool.submit(() -> {
			LyricRecord record = null;
			try {
				record = load(id, title, artist);
			} catch (Exception ignored) {
			}
			cache.put(key, Optional.ofNullable(record));
			if (requestId.equals(lastRequestId)) {
				current = record;
				loading = false;
			}
		});
	}

	public LyricRecord current() {
		return current;
	}

	/** 断线时清掉当前曲目状态（缓存保留）。 */
	public void reset() {
		lastRequestId = null;
		current = null;
		loading = false;
	}

	public boolean isLoading() {
		return loading;
	}

	// -------------------------------------------------------------- 拉取

	private LyricRecord load(String id, String title, String artist) {
		if (id != null) {
			LyricRecord record = fetchLyric(id);
			if (record != null) return record;
		}
		if (title != null && !title.isBlank()) {
			String query = title + (artist == null || artist.isBlank() ? "" : " " + artist);
			String foundId = searchId(query);
			if (foundId != null) return fetchLyric(foundId);
		}
		return null;
	}

	private LyricRecord fetchLyric(String id) {
		String url = "https://music.163.com/api/song/lyric/?id=" + id + "&lv=-1&kv=-1&tv=-1";
		String body = httpGet(url, 512 * 1024);
		return body == null ? null : LyricParser.parse(body);
	}

	private String searchId(String query) {
		try {
			String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
			String url = "https://music.163.com/api/search/get/web?csrf_token=&s=" + encoded
					+ "&type=1&offset=0&total=true&limit=1";
			String body = httpGet(url, 1024 * 1024);
			if (body == null) return null;
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (!root.has("result") || root.get("result").isJsonNull()) return null;
			JsonObject result = root.getAsJsonObject("result");
			if (!result.has("songs") || result.get("songs").isJsonNull()) return null;
			JsonArray songs = result.getAsJsonArray("songs");
			if (songs.isEmpty()) return null;
			JsonElement first = songs.get(0);
			if (!first.isJsonObject()) return null;
			JsonObject song = first.getAsJsonObject();
			return song.has("id") && !song.get("id").isJsonNull() ? song.get("id").getAsString() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static String httpGet(String url, int maxBytes) {
		try {
			HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setConnectTimeout(8000);
			connection.setReadTimeout(12000);
			connection.setRequestProperty("User-Agent", UA);
			connection.setRequestProperty("Referer", "https://music.163.com/");
			connection.setRequestProperty("Accept", "application/json, text/plain, */*");
			if (connection.getResponseCode() != 200) {
				connection.disconnect();
				return null;
			}
			try (InputStream in = connection.getInputStream()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
				byte[] buffer = new byte[8192];
				int total = 0;
				int n;
				while (total < maxBytes && (n = in.read(buffer, 0, Math.min(buffer.length, maxBytes - total))) > 0) {
					out.write(buffer, 0, n);
					total += n;
				}
				return out.toString(StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static String extractId(String url) {
		if (url == null || url.isBlank()) return null;
		Matcher matcher = ID_PATTERN.matcher(url);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static String cacheKey(String id, String title, String artist) {
		if (id != null) return "id:" + id;
		if (title == null) return "none";
		return "q:" + title.toLowerCase() + "|" + (artist == null ? "" : artist.toLowerCase());
	}
}

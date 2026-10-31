package a6a.dr.si.music;

import a6a.dr.Si;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网易云检索 / 登录 / 播放地址解析。
 *
 * <p>没有绑定账号时走「外链」（只有免费歌能放）；
 * 绑定网易云账号后，搜索带 Cookie，播放地址改用 v1 接口，VIP 歌曲也能拿到真实音频。</p>
 */
public final class MusicService {
	public record Song(long id, String name, String artist, String album, long durationMs, String cover, int fee) {
	}

	public record Probe(boolean ok, int status, String contentType, long length, String finalUrl, String error) {
	}

	public record Profile(String musicU, String nickname, long uid) {
	}

	/** 扫码状态：800 过期 / 801 等待扫码 / 802 待确认 / 803 成功 */
	public record QrState(int code, String message, Profile profile) {
	}

	/** 单首歌的可播性：code 200 可播 / -110 需要登录 / 404 无版权 */
	public record Resolved(long id, int code, String url, int fee) {
		public boolean playable() {
			return url != null && !url.isBlank();
		}

		public boolean needLogin() {
			return code == -110;
		}
	}

	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
	private static final String BASE_COOKIE = "os=pc; appver=2.10.13; osver=Microsoft-Windows-10; deviceId=si-mod";
	private static final String OUTER = "https://music.163.com/song/media/outer/url?id=";

	private final int timeoutMs;

	public MusicService() {
		this(10);
	}

	public MusicService(int timeoutSeconds) {
		this.timeoutMs = Math.max(3, timeoutSeconds) * 1000;
	}

	// ---------------------------------------------------------------- 搜索

	public List<Song> search(String keyword, int limit) throws IOException {
		return search(keyword, limit, null);
	}

	public List<Song> search(String keyword, int limit, String musicU) throws IOException {
		String query = keyword == null ? "" : keyword.trim();
		if (query.isEmpty()) return List.of();
		int size = Math.min(Math.max(limit, 1), 50);
		String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

		Map<String, String> headers = musicHeaders(musicU);

		List<String> endpoints = List.of(
				"https://music.163.com/api/search/get/web?csrf_token=&s=" + encoded
						+ "&type=1&offset=0&total=true&limit=" + size,
				"https://music.163.com/api/search/get?s=" + encoded
						+ "&type=1&offset=0&limit=" + size
		);

		IOException last = null;
		for (String endpoint : endpoints) {
			try {
				Response response = request(endpoint, "GET", headers, 3, 4 * 1024 * 1024);
				if (response.status != 200) throw new IOException("HTTP " + response.status);
				List<Song> songs = parseSongs(new String(response.body, StandardCharsets.UTF_8));
				if (songs != null) return songs;
			} catch (IOException e) {
				last = e;
			}
		}
		throw last != null ? last : new IOException("网易云没有返回可解析的数据");
	}

	private List<Song> parseSongs(String body) {
		if (body == null || body.isBlank()) return null;
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (!root.has("result") || root.get("result").isJsonNull()) return null;
			JsonObject result = root.getAsJsonObject("result");
			JsonArray songs = result.has("songs") && !result.get("songs").isJsonNull()
					? result.getAsJsonArray("songs")
					: null;
			if (songs == null) return null;
			List<Song> list = new ArrayList<>(songs.size());
			for (JsonElement element : songs) {
				if (!element.isJsonObject()) continue;
				JsonObject song = element.getAsJsonObject();
				long id = song.has("id") ? song.get("id").getAsLong() : 0L;
				if (id == 0L) continue;
				String name = string(song, "name", "未知歌曲");

				StringBuilder artists = new StringBuilder();
				if (song.has("artists") && song.get("artists").isJsonArray()) {
					for (JsonElement artist : song.getAsJsonArray("artists")) {
						if (!artist.isJsonObject()) continue;
						if (artists.length() > 0) artists.append(" / ");
						artists.append(string(artist.getAsJsonObject(), "name", "未知歌手"));
					}
				}
				if (artists.length() == 0) artists.append("未知歌手");

				String album = "";
				String cover = null;
				if (song.has("album") && song.get("album").isJsonObject()) {
					JsonObject albumObject = song.getAsJsonObject("album");
					album = string(albumObject, "name", "");
					cover = string(albumObject, "picUrl", null);
				}
				long duration = song.has("duration") ? song.get("duration").getAsLong() : 0L;
				int fee = song.has("fee") ? song.get("fee").getAsInt() : 0;
				list.add(new Song(id, name, artists.toString(), album, duration, cover, fee));
			}
			return list;
		} catch (Exception e) {
			Si.LOGGER.debug("解析搜索结果失败: {}", e.toString());
			return null;
		}
	}

	private static String string(JsonObject object, String key, String fallback) {
		if (object.has(key) && !object.get(key).isJsonNull()) {
			try {
				return object.get(key).getAsString();
			} catch (Exception ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	// -------------------------------------------------------------- 账号

	/** 手机号 + 密码登录，返回 MUSIC_U Cookie 与昵称 */
	public Profile login(String phone, String password) throws IOException {
		String json = "{\"phone\":\"" + escape(phone) + "\",\"password\":\"" + escape(password)
				+ "\",\"countrycode\":\"86\",\"csrf_token\":\"\",\"rememberLogin\":\"true\"}";
		Map<String, String> form = WeApi.encrypt(json);
		String body = "params=" + URLEncoder.encode(form.get("params"), StandardCharsets.UTF_8)
				+ "&encSecKey=" + URLEncoder.encode(form.get("encSecKey"), StandardCharsets.UTF_8);

		Response response = postForm("https://music.163.com/weapi/login/cellphone?csrf_token=", body, null);
		String text = new String(response.body, StandardCharsets.UTF_8);
		JsonObject root = parse(text);
		if (root == null) {
			throw new IOException("网易云返回异常，请稍后重试");
		}
		int code = root.has("code") ? root.get("code").getAsInt() : -1;
		if (code != 200) {
			throw new IOException(loginError(code, root));
		}
		String musicU = extractMusicU(response.setCookie);
		if (musicU == null) {
			throw new IOException("登录成功但没有拿到 Cookie，请改用粘贴 Cookie 的方式");
		}
		String nickname = root.has("profile") && root.get("profile").isJsonObject()
				? string(root.getAsJsonObject("profile"), "nickname", "网易云用户")
				: "网易云用户";
		long uid = root.has("account") && root.get("account").isJsonObject()
				? longOf(root.getAsJsonObject("account"), "id")
				: 0L;
		return new Profile(musicU, nickname, uid);
	}

	// -------------------------------------------------------------- 扫码

	/** 申请一个扫码登录的 key */
	public String qrKey() throws IOException {
		Response response = request("https://music.163.com/api/login/qrcode/unikey?type=1", "POST",
				musicHeaders(null), 3, 64 * 1024, "");
		JsonObject root = parse(new String(response.body, StandardCharsets.UTF_8));
		if (root == null || !root.has("unikey") || root.get("unikey").isJsonNull()) {
			throw new IOException("获取二维码失败，请稍后重试");
		}
		return root.get("unikey").getAsString();
	}

	/** 轮询扫码状态，803 时返回登录态 */
	public QrState qrPoll(String key) throws IOException {
		if (key == null || key.isBlank()) throw new IOException("缺少 key");
		String url = "https://music.163.com/api/login/qrcode/client/login?key="
				+ URLEncoder.encode(key, StandardCharsets.UTF_8) + "&type=1";
		Response response = request(url, "GET", musicHeaders(null), 3, 256 * 1024);
		JsonObject root = parse(new String(response.body, StandardCharsets.UTF_8));
		int code = root != null && root.has("code") ? root.get("code").getAsInt() : -1;
		String message = root != null && root.has("message") && !root.get("message").isJsonNull()
				? root.get("message").getAsString()
				: "";
		if (code == 803) {
			String musicU = extractMusicU(response.setCookie);
			if (musicU == null) {
				return new QrState(803, "授权成功但没有拿到 Cookie", null);
			}
			Profile profile = profile(musicU);
			if (profile == null) profile = new Profile(musicU, "网易云用户", 0L);
			return new QrState(803, "登录成功", profile);
		}
		return new QrState(code, message, null);
	}

	/** 校验 MUSIC_U 是否有效，无效返回 null */
	public Profile profile(String musicU) {		if (musicU == null || musicU.isBlank()) return null;
		try {
			Response response = request("https://music.163.com/api/nuser/account/get", "GET",
					musicHeaders(musicU), 3, 512 * 1024);
			JsonObject root = parse(new String(response.body, StandardCharsets.UTF_8));
			if (root == null || !root.has("account") || root.get("account").isJsonNull()) return null;
			JsonObject account = root.getAsJsonObject("account");
			JsonObject profile = root.has("profile") && root.get("profile").isJsonObject()
					? root.getAsJsonObject("profile")
					: new JsonObject();
			String nickname = string(profile, "nickname", "网易云用户");
			return new Profile(musicU, nickname, longOf(account, "id"));
		} catch (Exception e) {
			return null;
		}
	}

	private static String loginError(int code, JsonObject root) {
		String message = root.has("message") && !root.get("message").isJsonNull()
				? root.get("message").getAsString()
				: (root.has("msg") && !root.get("msg").isJsonNull() ? root.get("msg").getAsString() : "");
		return switch (code) {
			case 501 -> "账号不存在";
			case 502 -> "密码错误";
			case 400 -> message.isEmpty() ? "请求格式有误" : message;
			case 10004 -> "网易云风控拦截（" + message + "），请改用「粘贴 Cookie」方式";
			case 8810 -> "需要短信验证，请改用「粘贴 Cookie」方式";
			default -> (message.isEmpty() ? "登录失败" : message) + "（code " + code + "）";
		};
	}

	private static String extractMusicU(String setCookie) {
		if (setCookie == null) return null;
		for (String part : setCookie.split("\n")) {
			String trimmed = part.trim();
			int index = trimmed.indexOf("MUSIC_U=");
			if (index < 0) continue;
			String value = trimmed.substring(index + "MUSIC_U=".length());
			int end = value.indexOf(';');
			if (end >= 0) value = value.substring(0, end);
			value = value.trim();
			if (!value.isEmpty()) return value;
		}
		return null;
	}

	// ------------------------------------------------------------ 播放地址

	public String outerUrl(long songId) {
		return OUTER + songId + ".mp3";
	}

	/** 绑定账号后走 v1 接口，能拿到 VIP 歌曲的真实地址；拿不到返回 null */
	public String resolveUrl(long songId, String musicU) {
		Resolved resolved = resolveUrls(List.of(songId), musicU).get(songId);
		return resolved == null || !resolved.playable() ? null : resolved.url();
	}

	/**
	 * 批量解析播放地址（一次请求查多首），用于在搜索结果里直接标出能不能播。
	 *
	 * @param musicU 绑定后的 MUSIC_U；匿名传 null
	 */
	public Map<Long, Resolved> resolveUrls(Collection<Long> ids, String musicU) {
		Map<Long, Resolved> result = new LinkedHashMap<>();
		if (ids == null || ids.isEmpty()) return result;
		StringBuilder list = new StringBuilder();
		for (Long id : ids) {
			if (id == null || id <= 0) continue;
			if (list.length() > 0) list.append(',');
			list.append(id);
			if (list.length() > 900) break;
		}
		if (list.length() == 0) return result;
		try {
			String url = "https://music.163.com/api/song/enhance/player/url/v1?ids=%5B" + list
					+ "%5D&level=exhigh&encodeType=mp3";
			Response response = request(url, "GET", musicHeaders(musicU), 3, 512 * 1024);
			JsonObject root = parse(new String(response.body, StandardCharsets.UTF_8));
			if (root == null || !root.has("data") || !root.get("data").isJsonArray()) return result;
			for (JsonElement element : root.getAsJsonArray("data")) {
				if (!element.isJsonObject()) continue;
				JsonObject item = element.getAsJsonObject();
				long id = longOf(item, "id");
				if (id <= 0) continue;
				int code = item.has("code") ? item.get("code").getAsInt() : -1;
				String resolvedUrl = string(item, "url", null);
				int fee = item.has("fee") ? item.get("fee").getAsInt() : 0;
				result.put(id, new Resolved(id, code, resolvedUrl, fee));
			}
		} catch (Exception e) {
			Si.LOGGER.debug("批量解析播放地址失败: {}", e.toString());
		}
		return result;
	}

	/** 探测地址是否真的能拿到音频，避免推给客户端一个 404。 */
	public Probe probe(String url) {
		if (url == null || url.isBlank()) {
			return new Probe(false, 0, null, 0, null, "地址为空");
		}
		try {
			Response response = request(url, "GET", Map.of(
					"User-Agent", UA,
					"Referer", "https://music.163.com/",
					"Range", "bytes=0-2047"
			), 5, 2048);
			String type = response.contentType == null ? "" : response.contentType.toLowerCase();
			boolean audio = type.startsWith("audio") || type.startsWith("video/mp2t")
					|| type.contains("octet-stream") || type.contains("mpegurl");
			boolean statusOk = response.status == 200 || response.status == 206;
			boolean hasBody = response.length > 0 || response.bytesRead > 0;
			boolean ok = statusOk && audio && hasBody;
			String error = null;
			if (!ok) {
				boolean notFoundPage = response.finalUrl != null && response.finalUrl.contains("/404");
				if (notFoundPage) {
					error = "这首歌在网易云上听不了（VIP 或无版权）";
				} else if (!statusOk) {
					error = "这首歌暂时取不到音频（HTTP " + response.status + "），可能已下架";
				} else if (!audio) {
					error = "这个地址不是音频文件，请检查直链";
				} else {
					error = "音频内容为空，可能已下架";
				}
			}
			return new Probe(ok, response.status, type, response.length, response.finalUrl, error);
		} catch (Exception e) {
			return new Probe(false, 0, null, 0, null, "无法访问音频地址: " + e.getMessage());
		}
	}

	// ---------------------------------------------------------------- HTTP

	private Map<String, String> musicHeaders(String musicU) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("User-Agent", UA);
		headers.put("Referer", "https://music.163.com/");
		headers.put("Accept", "application/json, text/plain, */*");
		headers.put("Cookie", musicU == null || musicU.isBlank()
				? BASE_COOKIE
				: "MUSIC_U=" + musicU + "; " + BASE_COOKIE);
		return headers;
	}

	private Response postForm(String url, String body, String musicU) throws IOException {
		Map<String, String> headers = musicHeaders(musicU);
		headers.put("Content-Type", "application/x-www-form-urlencoded");
		return request(url, "POST", headers, 3, 4 * 1024 * 1024, body);
	}

	private static JsonObject parse(String text) {
		try {
			JsonElement element = JsonParser.parseString(text);
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static long longOf(JsonObject object, String key) {
		if (object.has(key) && !object.get(key).isJsonNull()) {
			try {
				return object.get(key).getAsLong();
			} catch (Exception ignored) {
				return 0L;
			}
		}
		return 0L;
	}

	private static String escape(String text) {
		if (text == null) return "";
		return text.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record Response(int status, String contentType, long length, String finalUrl,
							byte[] body, int bytesRead, String setCookie) {
	}

	private Response request(String url, String method, Map<String, String> headers, int maxHops, int maxBytes)
			throws IOException {
		return request(url, method, headers, maxHops, maxBytes, null);
	}

	private Response request(String url, String method, Map<String, String> headers, int maxHops,
							 int maxBytes, String formBody) throws IOException {
		String current = url;
		for (int hop = 0; hop <= maxHops; hop++) {
			HttpURLConnection connection = (HttpURLConnection) URI.create(current).toURL().openConnection();
			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout(timeoutMs);
			connection.setReadTimeout(timeoutMs);
			connection.setRequestMethod(method);
			headers.forEach(connection::setRequestProperty);
			if (formBody != null) {
				connection.setDoOutput(true);
				try (OutputStream out = connection.getOutputStream()) {
					out.write(formBody.getBytes(StandardCharsets.UTF_8));
				}
			}
			int status = connection.getResponseCode();
			if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
				String location = connection.getHeaderField("Location");
				connection.disconnect();
				if (location == null || location.isBlank()) {
					throw new IOException("重定向缺少 Location");
				}
				current = URI.create(current).resolve(location).toString();
				continue;
			}
			String contentType = connection.getContentType();
			long length = connection.getContentLengthLong();
			StringBuilder cookies = new StringBuilder();
			Map<String, List<String>> fields = connection.getHeaderFields();
			for (Map.Entry<String, List<String>> entry : fields.entrySet()) {
				if (entry.getKey() == null || !"set-cookie".equalsIgnoreCase(entry.getKey())) continue;
				for (String value : entry.getValue()) {
					if (cookies.length() > 0) cookies.append('\n');
					cookies.append(value);
				}
			}
			byte[] body = new byte[0];
			int read = 0;
			if (status >= 200 && status < 300) {
				try (InputStream in = connection.getInputStream()) {
					body = readLimited(in, maxBytes);
					read = body.length;
				} catch (IOException e) {
					// 有些 CDN 在 Range 请求下不给 body，忽略
				}
			}
			connection.disconnect();
			return new Response(status, contentType, length, current, body, read, cookies.toString());
		}
		throw new IOException("重定向次数过多");
	}

	private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
		byte[] buffer = new byte[8192];
		int total = 0;
		int n;
		while (total < maxBytes && (n = in.read(buffer, 0, Math.min(buffer.length, maxBytes - total))) > 0) {
			out.write(buffer, 0, n);
			total += n;
		}
		return out.toByteArray();
	}
}

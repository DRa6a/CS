package a6a.dr.si.music;

import a6a.dr.Si;
import a6a.dr.si.util.TtlCache;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 适配服务（对应方案文档里的「解析服务」+ 可选「代理转发层」）。
 *
 * <p>只负责把歌曲 id 换成可播放直链；元数据（搜索 / 歌单 / 我喜欢）仍走网易云。
 * 三种解析返回形态都兼容：</p>
 * <ul>
 *   <li>{@code 302}：跟随重定向，取重定向终态 URL 作为直链（不读 body，避免下载整首）；</li>
 *   <li>{@code text}：纯文本响应体即地址；</li>
 *   <li>{@code json}：JSON 响应里取某个字段（支持 {@code a.b.c} 嵌套，如 {@code data.url}）。</li>
 * </ul>
 *
 * <p>统一处理「@ 前缀」与 https 升级，可选把结果包进代理转发层（{@code forceProxy}）。</p>
 *
 * <p><b>这是一套独立、无鉴权的服务</b>：不接收也不发送网易云 cookie，绑定账号与否都不影响它。
 * 默认关闭，需要服主在 si.json 里填好解析服务基址并打开开关。</p>
 */
public final class ExternalSource {
	public static final String MODE_302 = "302";
	public static final String MODE_TEXT = "text";
	public static final String MODE_JSON = "json";

	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

	private final boolean enabled;
	/** 解析服务基址，支持 {id} {br} 占位；不含占位则把 id 拼在末尾 */
	private final String parseBase;
	/** 代理转发层基址（跨域 / 防盗链兜底），可空 */
	private final String proxyBase;
	/** 解析返回形态：302 / text / json */
	private final String mode;
	/** mode=json 时取音频地址的字段，支持 a.b.c 嵌套，如 data.url */
	private final String jsonField;
	/** 音质参数，如 320 */
	private final int bitrate;
	/** 是否强制所有解析结果都走代理转发层 */
	private final boolean forceProxy;
	private final int timeoutMs;

	private final TtlCache<Long, String> cache = new TtlCache<>(512, 3 * 60_000L);

	public ExternalSource(boolean enabled, String parseBase, String proxyBase, String mode,
						  String jsonField, int bitrate, boolean forceProxy, int timeoutSeconds) {
		this.enabled = enabled && parseBase != null && !parseBase.trim().isBlank();
		this.parseBase = parseBase == null ? "" : parseBase.trim();
		this.proxyBase = proxyBase == null ? "" : proxyBase.trim();
		this.mode = normalizeMode(mode);
		this.jsonField = jsonField == null ? "" : jsonField.trim();
		this.bitrate = Math.max(0, bitrate);
		this.forceProxy = forceProxy;
		this.timeoutMs = Math.max(3, timeoutSeconds) * 1000;
	}

	public boolean enabled() {
		return enabled;
	}

	/** 解析出可播放直链；拿不到返回 null。cookie 不参与（无鉴权服务）。 */
	public String resolve(long songId) {
		if (!enabled || songId <= 0) return null;
		String cached = cache.get(songId);
		if (cached != null) return cached;
		String url = fetch(buildParseUrl(songId));
		if (url == null || url.isBlank()) return null;
		if (forceProxy && !proxyBase.isBlank()) url = wrapProxy(url);
		cache.put(songId, url);
		return url;
	}

	/** 解析服务基址 + 占位符 / id，构造成最终请求地址 */
	private String buildParseUrl(long songId) {
		String base = parseBase;
		if (base.contains("{id}")) {
			base = base.replace("{id}", Long.toString(songId))
					.replace("{br}", Integer.toString(bitrate));
		} else if (base.endsWith("/")) {
			base = base + songId;
		} else {
			base = base + "/" + songId;
		}
		return base;
	}

	private static String normalizeMode(String mode) {
		if (MODE_302.equalsIgnoreCase(mode)) return MODE_302;
		if (MODE_JSON.equalsIgnoreCase(mode)) return MODE_JSON;
		return MODE_TEXT; // text 是兜底默认
	}

	/** GET 解析地址，按模式取出直链，统一 https、去 @ 前缀。 */
	private String fetch(String url) {
		String current = url;
		boolean redirected = false;
		HttpURLConnection connection = null;
		for (int hop = 0; hop < 6; hop++) {
			try {
				connection = (HttpURLConnection) URI.create(current).toURL().openConnection();
				connection.setInstanceFollowRedirects(false);
				connection.setConnectTimeout(timeoutMs);
				connection.setReadTimeout(timeoutMs);
				connection.setRequestProperty("User-Agent", UA);
				connection.setRequestProperty("Accept", "*/*");
				int status = connection.getResponseCode();
				if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
					String location = connection.getHeaderField("Location");
					connection.disconnect();
					if (location == null || location.isBlank()) return null;
					current = URI.create(current).resolve(location).toString();
					redirected = true;
					continue;
				}
				if (MODE_302.equals(mode)) {
					// 期望靠 302 给直链；若首跳就 200（没发生重定向），多半 body 里就是地址
					if (!redirected) {
						String body = readBody(connection);
						connection.disconnect();
						String raw = normalize(body);
						if (raw != null) return raw;
					}
					connection.disconnect();
					return normalize(current);
				}
				String body = readBody(connection);
				connection.disconnect();
				String raw = MODE_JSON.equals(mode) ? readJsonField(body) : body;
				return normalize(raw);
			} catch (IOException e) {
				if (connection != null) {
					try { connection.disconnect(); } catch (Exception ignored) { }
				}
				Si.LOGGER.debug("适配服务解析失败: {}", e.toString());
				return null;
			}
		}
		return null;
	}

	private String readJsonField(String body) {
		if (body == null || body.isBlank() || jsonField.isBlank()) return null;
		try {
			JsonElement element = JsonParser.parseString(body);
			JsonObject root = asObject(element);
			if (root == null) return null;
			return readPath(root, jsonField);
		} catch (Exception e) {
			Si.LOGGER.debug("适配服务 JSON 解析失败: {}", e.toString());
			return null;
		}
	}

	/**
	 * 解析结果可能是对象，也可能是对象数组（常见于 Meting 类服务，取首个元素）。
	 * 也允许 jsonField 以 {@code [n]} 开头显式指定下标，如 {@code [0].url}。
	 */
	private static JsonObject asObject(JsonElement element) {
		if (element == null || element.isJsonNull()) return null;
		if (element.isJsonObject()) return element.getAsJsonObject();
		if (element.isJsonArray()) {
			var arr = element.getAsJsonArray();
			if (arr.size() == 0) return null;
			JsonElement first = arr.get(0);
			return first.isJsonObject() ? first.getAsJsonObject() : null;
		}
		return null;
	}

	/** 支持 a.b.c 嵌套字段 */
	private static String readPath(JsonObject root, String path) {
		JsonObject current = root;
		String[] parts = path.split("\\.");
		for (int i = 0; i < parts.length; i++) {
			if (current == null || !current.has(parts[i]) || current.get(parts[i]).isJsonNull()) return null;
			JsonElement element = current.get(parts[i]);
			if (i == parts.length - 1) {
				try { return element.getAsString(); } catch (Exception e) { return null; }
			}
			current = element.isJsonObject() ? element.getAsJsonObject() : null;
		}
		return null;
	}

	private static String readBody(HttpURLConnection connection) throws IOException {
		try (InputStream in = connection.getInputStream()) {
			ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
			byte[] buffer = new byte[8192];
			int total = 0, n;
			while (total < 256 * 1024 && (n = in.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				total += n;
			}
			return out.toString(StandardCharsets.UTF_8).trim();
		}
	}

	/** 把最终直链包进代理转发层：proxy?url=<encoded> */
	private String wrapProxy(String direct) {
		String base = proxyBase;
		String sep = base.contains("?") ? "&" : "?";
		return base + sep + "url=" + URLEncoder.encode(direct, StandardCharsets.UTF_8);
	}

	/** 去 @ 前缀、协议相对补 https、强制 https（规避混合内容）。 */
	private static String normalize(String raw) {
		if (raw == null) return null;
		String s = raw.trim();
		while (s.startsWith("@")) s = s.substring(1).trim();
		if (s.isEmpty()) return null;
		if (s.startsWith("//")) s = "https:" + s;
		if (!s.startsWith("http://") && !s.startsWith("https://")) return null;
		if (s.startsWith("http://")) s = "https://" + s.substring(7);
		return s;
	}
}

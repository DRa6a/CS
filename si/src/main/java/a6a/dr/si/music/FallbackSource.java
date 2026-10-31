package a6a.dr.si.music;

import a6a.dr.Si;
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
 * 备用音源：网易云拿不到地址时，按配置的 URL 模板再试一次。
 *
 * <p>模板支持 {id} {name} {artist}，会自动 URL 编码。<br>
 * 如果接口直接返回音频（或 302 跳音频），{@code jsonField} 留空；<br>
 * 如果接口返回 JSON，就填上装着地址的字段名，例如 {@code url} / {@code data.url}。</p>
 *
 * <p>默认关闭。填什么源由服务器管理员决定，模组本身不内置任何第三方站点。</p>
 */
public final class FallbackSource {
	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

	private final boolean enabled;
	private final String template;
	private final String jsonField;
	private final int timeoutMs;

	public FallbackSource(boolean enabled, String template, String jsonField, int timeoutSeconds) {
		this.enabled = enabled && template != null && !template.isBlank();
		this.template = template == null ? "" : template.trim();
		this.jsonField = jsonField == null ? "" : jsonField.trim();
		this.timeoutMs = Math.max(3, timeoutSeconds) * 1000;
	}

	public boolean enabled() {
		return enabled;
	}

	/** 解析出可播放地址；拿不到返回 null */
	public String resolve(long songId, String name, String artist) {
		if (!enabled) return null;
		String url = template
				.replace("{id}", Long.toString(songId))
				.replace("{name}", encode(name))
				.replace("{artist}", encode(artist));
		try {
			if (jsonField.isBlank()) {
				return url;
			}
			String body = get(url);
			if (body == null || body.isBlank()) return null;
			JsonElement element = JsonParser.parseString(body);
			JsonObject root = element.isJsonObject() ? element.getAsJsonObject() : null;
			if (root == null) return null;
			String value = readField(root, jsonField);
			if (value == null || value.isBlank()) return null;
			if (value.startsWith("//")) return "https:" + value;
			if (!value.startsWith("http://") && !value.startsWith("https://")) return null;
			return value;
		} catch (Exception e) {
			Si.LOGGER.debug("备用源解析失败: {}", e.toString());
			return null;
		}
	}

	/** 支持 a.b.c 这种嵌套字段 */
	private static String readField(JsonObject root, String path) {
		JsonObject current = root;
		String[] parts = path.split("\\.");
		for (int i = 0; i < parts.length; i++) {
			if (!current.has(parts[i]) || current.get(parts[i]).isJsonNull()) return null;
			JsonElement element = current.get(parts[i]);
			if (i == parts.length - 1) {
				try {
					return element.getAsString();
				} catch (Exception e) {
					return null;
				}
			}
			if (!element.isJsonObject()) return null;
			current = element.getAsJsonObject();
		}
		return null;
	}

	private String get(String url) throws IOException {
		String current = url;
		for (int hop = 0; hop < 5; hop++) {
			HttpURLConnection connection = (HttpURLConnection) URI.create(current).toURL().openConnection();
			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout(timeoutMs);
			connection.setReadTimeout(timeoutMs);
			connection.setRequestProperty("User-Agent", UA);
			int status = connection.getResponseCode();
			if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
				String location = connection.getHeaderField("Location");
				connection.disconnect();
				if (location == null || location.isBlank()) return null;
				current = URI.create(current).resolve(location).toString();
				continue;
			}
			if (status != 200) {
				connection.disconnect();
				return null;
			}
			try (InputStream in = connection.getInputStream()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				int total = 0;
				int n;
				while (total < 256 * 1024 && (n = in.read(buffer)) > 0) {
					out.write(buffer, 0, n);
					total += n;
				}
				return out.toString(StandardCharsets.UTF_8);
			} finally {
				connection.disconnect();
			}
		}
		return null;
	}

	private static String encode(String text) {
		return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
	}
}

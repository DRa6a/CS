package a6a.dr.si;

import a6a.dr.Si;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置文件：config/si.json
 */
public final class SiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** 是否启用内嵌网页服务 */
	public boolean enabled = true;
	/** 监听地址，0.0.0.0 表示所有网卡 */
	public String bindAddress = "0.0.0.0";
	/** 监听端口（需要在防火墙/安全组放行） */
	public int port = 8765;
	/**
	 * 显示给玩家的点歌台地址（聊天栏提示、/si url、网页副标题都读这个）。
	 *
	 * <p>留空就自动取内网 IP。可以只写域名（mc.example.com），会自动补 http://。
	 * 老版本里的 {@code publicUrl} 已合并到这里，读取时会自动迁移。</p>
	 */
	public String displayUrl = "";
	/** 网页标题里显示的服务器名 */
	public String serverName = "我的世界服务器";
	/** 登录会话有效期（小时） */
	public int sessionHours = 12;
	/** 单次点歌冷却（秒） */
	public int requestCooldownSeconds = 8;
	/** 是否允许「全服播放」 */
	public boolean allowBroadcast = true;
	/** 默认音量 0~1 */
	public float defaultVolume = 0.8f;
	/** 网页里保留的点歌记录条数 */
	public int historySize = 40;
	/** 网易云搜索请求超时（秒） */
	public int searchTimeoutSeconds = 10;
	/** 管理员名单（可用 /si reset 重置玩家密码），控制台无需在此列中 */
	public List<String> admins = new ArrayList<>();
	/** 玩家头像地址模板，支持 {name} 和 {uuid} 占位；留空则不显示头像 */
	public String avatarUrl = "https://littleskin.cn/avatar/player/{name}?size=96";
	/** 是否允许网页绑定网易云账号（绑定后可播放 VIP 歌曲） */
	public boolean neteaseLogin = true;
	/** 备用音源开关：网易云拿不到地址时再试一次 */
	public boolean fallbackEnabled = false;
	/** 备用音源 URL 模板，支持 {id} {name} {artist}，例如 https://your-host/song?id={id} */
	public String fallbackUrlTemplate = "";
	/** 备用接口若返回 JSON，填装着音频地址的字段（如 url 或 data.url）；留空表示接口直接返回音频 */
	public String fallbackJsonField = "";

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("si.json");
	}

	public static SiConfig load() {
		Path path = file();
		SiConfig config = new SiConfig();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonObject raw = JsonParser.parseReader(reader).getAsJsonObject();
				SiConfig loaded = GSON.fromJson(raw, SiConfig.class);
				if (loaded != null) {
					config = loaded;
				}
				// 老版本这个字段叫 publicUrl，现在统一成 displayUrl：新的没填就沿用旧的
				if (config.displayUrl == null || config.displayUrl.isBlank()) {
					JsonElement legacy = raw.get("publicUrl");
					if (legacy != null && !legacy.isJsonNull()) {
						config.displayUrl = legacy.getAsString();
					}
				}
			} catch (Exception e) {
				Si.LOGGER.warn("读取 si.json 失败，将使用默认配置", e);
			}
		}
		config.normalize();
		// 写回一次，老的 publicUrl 键就自然消失了
		config.save();
		return config;
	}

	private void normalize() {
		if (bindAddress == null || bindAddress.isBlank()) bindAddress = "0.0.0.0";
		if (port < 1 || port > 65535) port = 8765;
		if (sessionHours < 1) sessionHours = 12;
		if (requestCooldownSeconds < 0) requestCooldownSeconds = 8;
		if (defaultVolume < 0f) defaultVolume = 0f;
		if (defaultVolume > 1f) defaultVolume = 1f;
		if (historySize < 5) historySize = 40;
		if (searchTimeoutSeconds < 3) searchTimeoutSeconds = 10;
		if (admins == null) admins = new ArrayList<>();
		if (serverName == null || serverName.isBlank()) serverName = "我的世界服务器";
		if (displayUrl == null) displayUrl = "";
		if (avatarUrl == null) avatarUrl = "";
		if (fallbackUrlTemplate == null) fallbackUrlTemplate = "";
		if (fallbackJsonField == null) fallbackJsonField = "";
	}

	public void save() {
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			Si.LOGGER.warn("保存 si.json 失败", e);
		}
	}
}

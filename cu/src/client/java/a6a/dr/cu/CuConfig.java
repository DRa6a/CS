package a6a.dr.cu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 客户端配置：config/cu.json
 */
public final class CuConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 歌词位置取值 */
	public static final String POS_BOTTOM = "bottom";
	public static final String POS_CENTER = "center";
	public static final String POS_TOP = "top";
	public static final List<String> POSITIONS = List.of(POS_BOTTOM, POS_CENTER, POS_TOP);

	/** 本地默认音量（服务端下发的音量优先） */
	public float volume = 0.8f;
	/** 音频缓存上限（MB），超出后按最久未使用清理 */
	public int maxCacheMb = 512;
	/** 单首下载超时（秒） */
	public int downloadTimeoutSeconds = 20;
	/** 是否在聊天栏提示播放状态 */
	public boolean chatMessages = true;
	/** 播放结束后是否自动播下一首 */
	public boolean autoPlayNext = true;

	/** true=有人点歌直接播放/切歌；false=弹窗手动同意 */
	public boolean autoAccept = true;

	/** 是否显示歌词 HUD */
	public boolean lyricsEnabled = true;
	/** 是否显示歌词翻译（网易云官方翻译） */
	public boolean lyricsShowTranslation = true;
	/** 歌词位置：bottom / center / top */
	public String lyricsPosition = POS_BOTTOM;
	/** 歌词字号倍率 0.75 ~ 2.0 */
	public float lyricsScale = 1.0f;
	/** 歌词透明度 0.2 ~ 1.0 */
	public float lyricsOpacity = 0.85f;
	/** 是否给歌词画半透明底 */
	public boolean lyricsShowBackground = true;

	/** 聊天框输入 @ 是否自动补全玩家名 */
	public boolean mentionEnabled = true;

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("cu.json");
	}

	public static CuConfig load() {
		Path path = file();
		CuConfig config = new CuConfig();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				CuConfig loaded = GSON.fromJson(reader, CuConfig.class);
				if (loaded != null) config = loaded;
			} catch (Exception ignored) {
			}
		}
		config.normalize();
		config.save();
		return config;
	}

	private void normalize() {
		volume = Math.max(0f, Math.min(1f, volume));
		if (maxCacheMb < 32) maxCacheMb = 512;
		if (downloadTimeoutSeconds < 5) downloadTimeoutSeconds = 20;
		if (lyricsPosition == null || !POSITIONS.contains(lyricsPosition)) lyricsPosition = POS_BOTTOM;
		lyricsScale = Math.max(0.75f, Math.min(2.0f, lyricsScale));
		lyricsOpacity = Math.max(0.2f, Math.min(1.0f, lyricsOpacity));
	}

	public void save() {
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception ignored) {
		}
	}
}

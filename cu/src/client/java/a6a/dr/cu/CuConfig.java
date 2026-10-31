package a6a.dr.cu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端配置：config/cu.json
 */
public final class CuConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
		config.volume = Math.max(0f, Math.min(1f, config.volume));
		if (config.maxCacheMb < 32) config.maxCacheMb = 512;
		if (config.downloadTimeoutSeconds < 5) config.downloadTimeoutSeconds = 20;
		config.save();
		return config;
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

package a6a.dr.cu.config;

import a6a.dr.client.CuClient;
import a6a.dr.cu.CuConfig;
import a6a.dr.cu.audio.AudioEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cu 配置页：点歌模式、歌词、翻译、@ 补全、音量。
 * 由 ModMenu 提供入口，也绑了快捷键（见 CuClient）。
 */
public final class CuConfigScreen extends Screen {
	private static final int ROW = 24;
	private static final int COL_W = 156;

	private final Screen parent;

	public CuConfigScreen(Screen parent) {
		super(Component.literal("点歌台 · 设置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		CuConfig cfg = CuClient.config;
		int centerX = this.width / 2;
		int leftX = centerX - COL_W - 6;
		int rightX = centerX + 6;
		int y = 44;

		// 左列
		addToggle(leftX, y, COL_W, "点歌模式", cfg.autoAccept,
				"自动播放/切歌", "手动同意", value -> cfg.autoAccept = value);
		y += ROW;
		addCycle(leftX, y, COL_W, "新歌处理", CuConfig.NEW_SONG_MODES.toArray(new String[0]),
				cfg.onNewSong, this::newSongLabel, value -> cfg.onNewSong = value);
		y += ROW;
		addToggle(leftX, y, COL_W, "歌词显示", cfg.lyricsEnabled, "开", "关",
				value -> cfg.lyricsEnabled = value);
		y += ROW;
		addToggle(leftX, y, COL_W, "歌词翻译", cfg.lyricsShowTranslation, "开", "关",
				value -> cfg.lyricsShowTranslation = value);
		y += ROW;
		addCycle(leftX, y, COL_W, "歌词位置", CuConfig.POSITIONS.toArray(new String[0]),
				cfg.lyricsPosition, this::positionLabel, value -> cfg.lyricsPosition = value);
		y += ROW;
		addToggle(leftX, y, COL_W, "@ 补全", cfg.mentionEnabled, "开", "关",
				value -> cfg.mentionEnabled = value);

		// 右列
		y = 44;
		addSlider(rightX, y, COL_W, "歌词字号", 0.75f, 2.0f, cfg.lyricsScale,
				v -> String.format("%.2fx", v), value -> cfg.lyricsScale = value);
		y += ROW;
		addSlider(rightX, y, COL_W, "歌词透明度", 0.2f, 1.0f, cfg.lyricsOpacity,
				v -> Math.round(v * 100) + "%", value -> cfg.lyricsOpacity = value);
		y += ROW;
		addToggle(rightX, y, COL_W, "歌词背景", cfg.lyricsShowBackground, "开", "关",
				value -> cfg.lyricsShowBackground = value);
		y += ROW;
		addSlider(rightX, y, COL_W, "音量", 0f, 1f, cfg.volume,
				v -> Math.round(v * 100) + "%", value -> {
					cfg.volume = value;
					AudioEngine.get().setVolume(value);
				});

		// 完成
		addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
				.bounds(centerX - 60, this.height - 30, 120, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
		graphics.centeredText(this.font,
				Component.literal("有人点歌时如何响应 · 歌词与聊天增强").withStyle(ChatFormatting.GRAY),
				this.width / 2, 30, 0xFF9A9A9A);
	}

	@Override
	public void onClose() {
		CuClient.config.save();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
	}

	private String positionLabel(String pos) {
		return switch (pos) {
			case CuConfig.POS_TOP -> "顶部";
			case CuConfig.POS_CENTER -> "居中";
			default -> "底部";
		};
	}

	private String newSongLabel(String mode) {
		return CuConfig.NEW_SONG_CUT.equals(mode) ? "立即切歌" : "播完再放";
	}

	private void addToggle(int x, int y, int w, String name, boolean value,
						   String onText, String offText, Consumer<Boolean> setter) {
		final boolean[] state = {value};
		Button button = Button.builder(toggleLabel(name, state[0], onText, offText), b -> {
			state[0] = !state[0];
			setter.accept(state[0]);
			b.setMessage(toggleLabel(name, state[0], onText, offText));
			CuClient.config.save();
		}).bounds(x, y, w, 20).build();
		addRenderableWidget(button);
	}

	private void addCycle(int x, int y, int w, String name, String[] values, String current,
						  Function<String, String> formatter, Consumer<String> setter) {
		final int[] index = {Math.max(0, Arrays.asList(values).indexOf(current))};
		Button button = Button.builder(Component.literal(name + "：" + formatter.apply(values[index[0]])), b -> {
			index[0] = (index[0] + 1) % values.length;
			setter.accept(values[index[0]]);
			b.setMessage(Component.literal(name + "：" + formatter.apply(values[index[0]])));
			CuClient.config.save();
		}).bounds(x, y, w, 20).build();
		addRenderableWidget(button);
	}

	private void addSlider(int x, int y, int w, String name, float min, float max, float value,
						   Function<Float, String> formatter, Consumer<Float> setter) {
		addRenderableWidget(new FloatSlider(x, y, w, 20, name, min, max, value, formatter, v -> {
			setter.accept(v);
			CuClient.config.save();
		}));
	}

	private static Component toggleLabel(String name, boolean on, String onText, String offText) {
		return Component.literal(name + "：" + (on ? onText : offText));
	}

	/** 0~1 滑块，映射到 [min,max] */
	private static final class FloatSlider extends AbstractSliderButton {
		private final String label;
		private final float min;
		private final float max;
		private final Function<Float, String> formatter;
		private final Consumer<Float> setter;

		FloatSlider(int x, int y, int w, int h, String label, float min, float max, float value,
					Function<Float, String> formatter, Consumer<Float> setter) {
			super(x, y, w, h, Component.literal(label), (value - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.formatter = formatter;
			this.setter = setter;
			updateMessage();
		}

		private float actual() {
			return min + (float) this.value * (max - min);
		}

		@Override
		protected void updateMessage() {
			if (this.formatter == null) return;
			setMessage(Component.literal(this.label + "：" + this.formatter.apply(actual())));
		}

		@Override
		protected void applyValue() {
			if (this.setter != null) this.setter.accept(actual());
		}
	}
}

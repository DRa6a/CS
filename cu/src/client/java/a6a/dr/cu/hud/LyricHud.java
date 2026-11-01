package a6a.dr.cu.hud;

import a6a.dr.client.CuClient;
import a6a.dr.cu.CuConfig;
import a6a.dr.cu.audio.AudioEngine;
import a6a.dr.cu.lyric.LyricRecord;
import a6a.dr.cu.lyric.LyricService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * 歌词 HUD：单行字幕，有官方翻译时再显示一行。
 *
 * <p>位置 / 字号 / 透明度 / 是否带底都可配。这个方法每帧都会被调用，
 * 所以「把歌词文本变成 Component、量文字宽度」这类活只在换行时做一次，
 * 缓存命中时每帧只剩两次文本绘制。</p>
 */
public final class LyricHud implements HudElement {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("cu", "lyric_hud");

	/** 上一次构建缓存时的标识：曲目 + 行号 + 翻译，任一变化才重建 */
	private String cachedKey = "";
	private Component cachedMain;
	private Component cachedSub;
	private int cachedWidth;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		CuConfig cfg = CuClient.config;
		if (cfg == null || !cfg.lyricsEnabled) return;

		AudioEngine engine = AudioEngine.get();
		String requestId = engine.currentRequestId();
		if (requestId == null) return;

		LyricService.get().update(requestId, engine.currentTitle(), engine.currentArtist(), engine.currentUrl());
		LyricRecord record = LyricService.get().current();
		if (record == null || record.isEmpty()) return;

		long time = engine.currentTimeMs();
		int index = record.indexAt(time);
		if (index < 0) return;

		LyricRecord.Line current = record.lineAt(index);
		if (current == null) return;

		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;

		String translation = null;
		if (cfg.lyricsShowTranslation) {
			String candidate = record.translationAt(time);
			if (candidate != null && !candidate.isBlank() && !candidate.equals(current.text())) {
				translation = candidate;
			}
		}

		refresh(font, requestId, index, current.text(), translation);

		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		int alpha = Math.round(cfg.lyricsOpacity * 255f);

		int lineHeight = font.lineHeight + 2;
		int lines = cachedSub == null ? 1 : 2;
		int blockHeight = lines * lineHeight;
		float scale = cfg.lyricsScale;
		float scaledHeight = blockHeight * scale;

		float baseY = switch (cfg.lyricsPosition == null ? CuConfig.POS_BOTTOM : cfg.lyricsPosition) {
			case CuConfig.POS_TOP -> 40f;
			case CuConfig.POS_CENTER -> (screenHeight - scaledHeight) / 2f;
			default -> screenHeight - 44f - scaledHeight;
		};

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(screenWidth / 2f, baseY);
		pose.scale(scale, scale);

		if (cfg.lyricsShowBackground) {
			int padX = 8;
			int padY = 4;
			graphics.fill(-cachedWidth / 2 - padX, -padY, cachedWidth / 2 + padX, blockHeight + padY,
					argb(Math.round(alpha * 0.55f), 0x000000));
		}

		graphics.centeredText(font, cachedMain, 0, 0, argb(alpha, 0xFFFFFF));
		if (cachedSub != null) {
			graphics.centeredText(font, cachedSub, 0, lineHeight, argb(Math.round(alpha * 0.9f), 0x9FC5E8));
		}

		pose.popMatrix();
	}

	/** 换行时才重建 Component 并量宽度 */
	private void refresh(Font font, String requestId, int index, String text, String translation) {
		String key = requestId + '\u0001' + index + '\u0001' + (translation == null ? "" : translation);
		if (key.equals(this.cachedKey)) return;
		this.cachedKey = key;
		this.cachedMain = Component.literal(text);
		this.cachedSub = translation == null ? null : Component.literal(translation);
		int width = font.width(this.cachedMain);
		if (this.cachedSub != null) {
			width = Math.max(width, font.width(this.cachedSub));
		}
		this.cachedWidth = width;
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}
}

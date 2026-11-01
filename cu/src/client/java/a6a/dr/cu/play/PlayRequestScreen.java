package a6a.dr.cu.play;

import a6a.dr.cu.audio.AudioEngine;
import a6a.dr.cu.net.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 手动同意模式下的点歌确认框：播放（加入队列）/ 切歌（停当前再播）/ 拒绝。
 */
public final class PlayRequestScreen extends Screen {
	private final Screen parent;
	private final Packets.Play payload;

	public PlayRequestScreen(Screen parent, Packets.Play payload) {
		super(Component.literal("点歌请求"));
		this.parent = parent;
		this.payload = payload;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 + 28;
		int w = 96;
		int gap = 8;
		int totalWidth = w * 3 + gap * 2;
		int startX = centerX - totalWidth / 2;

		addRenderableWidget(Button.builder(Component.literal("播放"), b -> play())
				.bounds(startX, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("切歌"), b -> skip())
				.bounds(startX + w + gap, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("拒绝"), b -> reject())
				.bounds(startX + (w + gap) * 2, y, w, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int centerX = this.width / 2;
		int y = this.height / 2 - 44;

		graphics.centeredText(this.font, Component.literal("有人给你点了首歌").withStyle(ChatFormatting.AQUA),
				centerX, y, 0xFFFFFFFF);
		y += this.font.lineHeight + 8;
		graphics.centeredText(this.font, Component.literal(payload.title()), centerX, y, 0xFFFFFFFF);
		y += this.font.lineHeight + 2;
		if (payload.artist() != null && !payload.artist().isBlank()) {
			graphics.centeredText(this.font, Component.literal(payload.artist()).withStyle(ChatFormatting.GRAY),
					centerX, y, 0xFFAAAAAA);
			y += this.font.lineHeight + 2;
		}
		String who = payload.requester() == null || payload.requester().isBlank() ? "某人" : payload.requester();
		if (payload.broadcast()) who += "（全服）";
		graphics.centeredText(this.font, Component.literal("点歌人：" + who).withStyle(ChatFormatting.GRAY),
				centerX, y, 0xFFAAAAAA);
	}

	private void play() {
		AudioEngine.get().enqueue(payload);
		finish();
	}

	private void skip() {
		AudioEngine.get().stopAll("切歌");
		AudioEngine.get().enqueue(payload);
		finish();
	}

	private void reject() {
		try {
			if (ClientPlayNetworking.canSend(Packets.Status.TYPE)) {
				ClientPlayNetworking.send(new Packets.Status(payload.requestId(), AudioEngine.STATE_FAILED, "对方未接受播放"));
			}
		} catch (Exception ignored) {
		}
		finish();
	}

	private void finish() {
		PlayApproval.resolved(parent);
	}

	@Override
	public void onClose() {
		// 直接按 Esc 视为拒绝
		reject();
	}
}

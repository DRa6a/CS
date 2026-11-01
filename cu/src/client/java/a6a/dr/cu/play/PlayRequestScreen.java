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
 * 手动同意模式下的点歌确认框。
 *
 * <p>按钮跟着「当前有没有在播」变：</p>
 * <ul>
 *   <li>没在播：播放 / 拒绝</li>
 *   <li>正在播：立即播放（打断当前）/ 排队 / 拒绝</li>
 * </ul>
 */
public final class PlayRequestScreen extends Screen {
	private static final int BTN_W = 96;
	private static final int GAP = 8;

	private final Screen parent;
	private final Packets.Play payload;
	/** 打开这个框的时候有没有在播——决定显示两个按钮还是三个 */
	private final boolean busy;

	public PlayRequestScreen(Screen parent, Packets.Play payload) {
		super(Component.literal("点歌请求"));
		this.parent = parent;
		this.payload = payload;
		this.busy = AudioEngine.get().isPlaying();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 + 28;

		if (this.busy) {
			int total = BTN_W * 3 + GAP * 2;
			int x = centerX - total / 2;
			addRenderableWidget(Button.builder(Component.literal("立即播放"), b -> playNow())
					.bounds(x, y, BTN_W, 20).build());
			addRenderableWidget(Button.builder(Component.literal("排队"), b -> queue())
					.bounds(x + BTN_W + GAP, y, BTN_W, 20).build());
			addRenderableWidget(Button.builder(Component.literal("拒绝"), b -> reject())
					.bounds(x + (BTN_W + GAP) * 2, y, BTN_W, 20).build());
			return;
		}

		int total = BTN_W * 2 + GAP;
		int x = centerX - total / 2;
		addRenderableWidget(Button.builder(Component.literal("播放"), b -> queue())
				.bounds(x, y, BTN_W, 20).build());
		addRenderableWidget(Button.builder(Component.literal("拒绝"), b -> reject())
				.bounds(x + BTN_W + GAP, y, BTN_W, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int centerX = this.width / 2;
		int y = this.height / 2 - 50;

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

		if (this.busy) {
			y += this.font.lineHeight + 6;
			String now = AudioEngine.get().currentTitle();
			String line = now == null || now.isBlank()
					? "当前正在播放另一首"
					: "正在播放：" + now;
			graphics.centeredText(this.font,
					Component.literal(line).withStyle(ChatFormatting.DARK_GRAY), centerX, y, 0xFF777777);
			y += this.font.lineHeight + 2;
			graphics.centeredText(this.font,
					Component.literal("「立即播放」会打断当前这首").withStyle(ChatFormatting.DARK_GRAY),
					centerX, y, 0xFF777777);
		}
	}

	/** 没在播时直接播；在播时打断当前 */
	private void playNow() {
		// replaceWith 会给被打断的那首报「已切歌」，网页上不会一直挂在「播放中」
		AudioEngine.get().replaceWith(payload);
		finish();
	}

	/** 排到队尾，等当前这首播完 */
	private void queue() {
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

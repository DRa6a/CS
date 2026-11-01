package a6a.dr.cu.mixin;

import a6a.dr.cu.mention.MentionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 聊天框 @ 补全：输入 @ 后弹出带头像的玩家候选面板，
 * 方向键切换、Tab/回车确认、鼠标点击插入。匹配逻辑复用 MojiDrop。
 *
 * <p>priority 500：保证 {@code onEdited} 上的注入比 MojiDrop（默认 1000）更早执行。</p>
 */
@Mixin(value = ChatScreen.class, priority = 500)
public abstract class ChatScreenMixin {
	@Shadow
	protected EditBox input;

	/**
	 * 自己改输入框内容时，别让其它模组把这次改动当成「玩家刚敲了空格」。
	 *
	 * <p>MojiDrop 的颜文字补全在 onEdited 里判断「文本以空格结尾」就发一次请求，
	 * 而 @ 提人插入后恰好以空格结尾（{@code @名字 }）。不挡住的话，
	 * 每次选完玩家名都会立刻触发一次颜文字请求，一两秒后弹出候选列表。</p>
	 */
	@Inject(method = "onEdited", at = @At("HEAD"), cancellable = true)
	private void cu$skipEditedWhileRewriting(String value, CallbackInfo ci) {
		if (MentionState.isRewriting()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cu$renderMention(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		MentionState.update(this.input.getValue(), this.input.getCursorPosition());
		if (MentionState.isActive()) {
			cu$drawPanel(graphics);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void cu$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		MentionState.update(this.input.getValue(), this.input.getCursorPosition());
		if (!MentionState.isActive()) return;
		switch (event.key()) {
			case GLFW.GLFW_KEY_UP -> {
				MentionState.move(-1);
				cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_DOWN -> {
				MentionState.move(1);
				cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				MentionState.insert(this.input);
				cir.setReturnValue(true);
			}
			default -> {
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cu$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		MentionState.update(this.input.getValue(), this.input.getCursorPosition());
		if (!MentionState.isActive()) return;
		int index = MentionState.clickedIndex(event.x(), event.y());
		if (index >= 0) {
			MentionState.select(index);
			MentionState.insert(this.input);
			cir.setReturnValue(true);
		}
	}

	private void cu$drawPanel(GuiGraphicsExtractor graphics) {
		List<String> matches = MentionState.matches();
		if (matches.isEmpty()) return;

		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int rowHeight = font.lineHeight + 6;
		int head = 10;

		int maxNameWidth = 0;
		for (String name : matches) {
			maxNameWidth = Math.max(maxNameWidth, font.width(name));
		}
		int panelWidth = head + 5 + maxNameWidth + 10;
		int rows = matches.size();
		int panelHeight = rows * rowHeight + 4;

		int x = this.input.getX();
		int y = this.input.getY() - panelHeight - 2;
		if (y < 2) {
			y = this.input.getY() + this.input.getHeight() + 2;
		}

		graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xD0101018);

		for (int i = 0; i < rows; i++) {
			int rowY = y + 2 + i * rowHeight;
			boolean selected = i == MentionState.selected();
			if (selected) {
				graphics.fill(x + 1, rowY - 1, x + panelWidth - 1, rowY + rowHeight - 1, 0x66FFFFFF);
			}
			PlayerInfo info = minecraft.getConnection() == null ? null
					: minecraft.getConnection().getPlayerInfo(matches.get(i));
			if (info != null) {
				int headY = rowY + (rowHeight - head) / 2;
				PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), x + 3, headY, head);
			}
			int textY = rowY + (rowHeight - font.lineHeight) / 2;
			graphics.text(font, Component.literal(matches.get(i)), x + 3 + head + 5, textY,
					selected ? 0xFFFFFF66 : 0xFFE0E0E0);
		}

		MentionState.setPanel(x, y, panelWidth, rowHeight, rows);
	}
}

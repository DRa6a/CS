package a6a.dr.cu.mixin;

import a6a.dr.cu.mention.MentionState;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 @ 提人只出现一个面板。
 *
 * <p>原版聊天框会往建议槽塞「在线玩家名」（TAB 弹出），MojiDrop 也会在检测到 @ 时
 * 往同一个槽里写一份纯文本候选。Cu 自己的面板是独立绘制的，如果不压掉原版列表，
 * 输入 @ 就会看到两套列表叠在一起。这里在 Cu 面板激活时把原版列表挡住即可；
 * 以 "/" 开头的命令补全不受影响（Cu 面板本来就不会在命令里激活）。</p>
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
	@Shadow
	private EditBox input;

	@Shadow
	public abstract void hide();

	@Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
	private void cu$blockVanillaList(boolean immediateNarration, CallbackInfo ci) {
		if (cu$panelActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "updateCommandInfo", at = @At("TAIL"))
	private void cu$hideVanillaList(CallbackInfo ci) {
		if (cu$panelActive()) {
			this.hide();
		}
	}

	private boolean cu$panelActive() {
		if (this.input == null) return false;
		MentionState.update(this.input.getValue(), this.input.getCursorPosition());
		return MentionState.isActive();
	}
}

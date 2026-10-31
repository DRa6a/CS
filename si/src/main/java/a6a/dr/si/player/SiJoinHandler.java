package a6a.dr.si.player;

import a6a.dr.si.SiCore;
import a6a.dr.si.auth.PasswordSetup;
import a6a.dr.si.net.SiNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 进服逻辑：登记在线状态；没设过密码的玩家进入「聊天框输入密码」状态。
 */
public final class SiJoinHandler {
	private SiJoinHandler() {
	}

	public static void onJoin(ServerPlayer player) {
		String name = player.getName().getString();
		boolean hasCu = SiNetworking.canPlay(player);
		OnlineRegistry.put(new OnlineRegistry.Info(player.getUUID(), name, hasCu));

		if (SiCore.accounts().exists(name)) {
			return;
		}
		prompt(player);
	}

	/** 首次进服 / 手动 /si setup 时的提示 */
	public static void prompt(ServerPlayer player) {
		PasswordSetup.begin(player.getUUID());
		player.sendSystemMessage(SiNetworking.prefix().append(
				Component.literal("第一次来，先设置一个网页密码").withStyle(ChatFormatting.WHITE)));
		player.sendSystemMessage(Component.literal("  ")
				.append(Component.literal("直接在聊天框输入密码（至少 6 位），再输一次确认")
						.withStyle(ChatFormatting.GRAY)));
		player.sendSystemMessage(Component.literal("  ")
				.append(Component.literal("输入的内容不会发给其他玩家；想先聊天请输入 ")
						.withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("/si cancel").withStyle(ChatFormatting.YELLOW)));
		player.sendSystemMessage(Component.literal("  ")
				.append(Component.literal("网页点歌地址 ").withStyle(ChatFormatting.GRAY))
				.append(SiNetworking.link(SiCore.baseUrl())));
		if (!SiNetworking.canPlay(player)) {
			player.sendSystemMessage(Component.literal("  ")
					.append(Component.literal("提示：安装 Cu 客户端模组后才能在游戏里听到点歌")
							.withStyle(ChatFormatting.DARK_GRAY)));
		}
	}
}

package a6a.dr.si.command;

import a6a.dr.si.SiCore;
import a6a.dr.si.auth.PasswordSetup;
import a6a.dr.si.player.SiJoinHandler;
import a6a.dr.si.net.SiNetworking;
import a6a.dr.si.player.OnlineRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class SiCommand {
	private SiCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("si")
				.executes(context -> {
					help(context.getSource());
					return 1;
				})
				.then(Commands.literal("url").executes(context -> {
					CommandSourceStack source = context.getSource();
					source.sendSuccess(() -> SiNetworking.prefix().append(
							Component.literal(SiCore.baseUrl()).withStyle(ChatFormatting.AQUA)), false);
					return 1;
				}))
				.then(Commands.literal("setup").executes(context -> {
					CommandSourceStack source = context.getSource();
					ServerPlayer player = source.getPlayer();
					if (player == null) {
						source.sendFailure(Component.literal("该命令只能由玩家执行"));
						return 0;
					}
					String name = player.getName().getString();
					if (SiCore.accounts().exists(name)) {
						player.sendSystemMessage(SiNetworking.prefix().append(
								Component.literal("你已经设置过密码，登录网页后在「设置」里可以修改：").withStyle(ChatFormatting.GRAY))
								.append(SiNetworking.link(SiCore.baseUrl())));
						return 1;
					}
					SiJoinHandler.prompt(player);
					return 1;
				}))
				.then(Commands.literal("cancel").executes(context -> {
					ServerPlayer player = context.getSource().getPlayer();
					if (player == null) {
						context.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
						return 0;
					}
					PasswordSetup.cancel(player.getUUID());
					player.sendSystemMessage(SiNetworking.prefix().append(
							Component.literal("已取消密码设置，现在可以正常聊天了（随时用 /si setup 重新开始）")
									.withStyle(ChatFormatting.GRAY)));
					return 1;
				}))
				.then(Commands.literal("stop").executes(context -> {
					ServerPlayer player = context.getSource().getPlayer();
					if (player == null) {
						context.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
						return 0;
					}
					SiNetworking.sendStop(player, "已停止播放");
					player.sendSystemMessage(SiNetworking.prefix().append(
							Component.literal("已停止播放").withStyle(ChatFormatting.GRAY)));
					return 1;
				}))
				.then(Commands.literal("volume")
						.then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(context -> {
							ServerPlayer player = context.getSource().getPlayer();
							if (player == null) {
								context.getSource().sendFailure(Component.literal("该命令只能由玩家执行"));
								return 0;
							}
							int value = IntegerArgumentType.getInteger(context, "value");
							float volume = value / 100f;
							SiCore.setVolume(player.getUUID(), volume);
							SiNetworking.sendVolume(player, volume);
							player.sendSystemMessage(SiNetworking.prefix().append(
									Component.literal("音量已设为 " + value + "%").withStyle(ChatFormatting.GRAY)));
							return 1;
						})))
				.then(Commands.literal("status").executes(context -> {
					CommandSourceStack source = context.getSource();
					source.sendSuccess(() -> SiNetworking.prefix().append(
							Component.literal("网页 ").withStyle(ChatFormatting.GRAY))
							.append(Component.literal(SiCore.baseUrl()).withStyle(ChatFormatting.AQUA))
							.append(Component.literal(" | 在线 " + OnlineRegistry.size() + " 人 | 已注册 "
									+ SiCore.accounts().size() + " 人").withStyle(ChatFormatting.GRAY)), false);
					return 1;
				}))
				.then(Commands.literal("reset")
						.then(Commands.argument("player", StringArgumentType.word()).executes(context -> {
							CommandSourceStack source = context.getSource();
							ServerPlayer self = source.getPlayer();
							if (self != null && !isAdmin(self.getName().getString())) {
								source.sendFailure(Component.literal("你没有权限重置密码（请在 si.json 的 admins 中添加自己）"));
								return 0;
							}
							String target = StringArgumentType.getString(context, "player");
							if (!SiCore.accounts().exists(target)) {
								source.sendFailure(Component.literal("玩家 " + target + " 还没有设置密码"));
								return 0;
							}
							SiCore.accounts().remove(target);
							SiCore.sessions().removeByName(target);
							source.sendSuccess(() -> Component.literal("已重置 " + target + " 的密码，他下次进服可重新设置")
									.withStyle(ChatFormatting.GREEN), false);
							return 1;
						})))
		);
	}

	private static boolean isAdmin(String name) {
		List<String> admins = SiCore.config().admins;
		if (admins == null) return false;
		for (String admin : admins) {
			if (admin != null && admin.equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	private static void help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("点歌台命令").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("/si url").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" — 查看网页点歌地址").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/si setup").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" — 设置网页密码（在聊天框输入）").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/si cancel").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" — 取消密码设置，恢复聊天").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/si stop").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" — 停止播放").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/si volume <0-100>").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" — 调整音量").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/si status").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" — 查看运行状态").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/si reset <玩家>").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" — 重置某玩家密码（管理员）").withStyle(ChatFormatting.GRAY)), false);
	}
}

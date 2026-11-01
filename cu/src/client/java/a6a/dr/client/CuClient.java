package a6a.dr.client;

import a6a.dr.cu.CuConfig;
import a6a.dr.cu.audio.AudioEngine;
import a6a.dr.cu.hud.LyricHud;
import a6a.dr.cu.lyric.LyricService;
import a6a.dr.cu.mention.MentionState;
import a6a.dr.cu.net.Packets;
import a6a.dr.cu.play.PlayApproval;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CuClient implements ClientModInitializer {
	public static CuConfig config;

	@Override
	public void onInitializeClient() {
		config = CuConfig.load();
		AudioEngine.get().init(config);

		// 歌词 HUD（当前行 + 翻译，可配）
		HudElementRegistry.addLast(LyricHud.ID, new LyricHud());

		// 收到播放请求
		ClientPlayNetworking.registerGlobalReceiver(Packets.Play.TYPE, (payload, context) -> {
			if (config.autoAccept) {
				if (CuConfig.NEW_SONG_CUT.equals(config.onNewSong)) {
					// 插播优先：停掉当前这首（会报「已切歌」），直接播新的
					AudioEngine.get().replaceWith(payload);
				} else {
					// 播完本首再放：进队列
					AudioEngine.get().enqueue(payload);
				}
				if (config.chatMessages) {
					context.client().execute(() -> chatPlayNotice(payload));
				}
			} else {
				// 手动同意：弹确认框（播放 / 切歌 / 拒绝）
				context.client().execute(() -> PlayApproval.offer(payload, context.client().gui.screen()));
			}
		});

		// 停止播放
		ClientPlayNetworking.registerGlobalReceiver(Packets.Stop.TYPE, (payload, context) -> {
			AudioEngine.get().stopAll(payload.reason());
			context.client().execute(() -> chat(Component.literal("[点歌台] ").withStyle(ChatFormatting.DARK_AQUA)
					.append(Component.literal(payload.reason()).withStyle(ChatFormatting.GRAY)), true));
		});

		// 音量
		ClientPlayNetworking.registerGlobalReceiver(Packets.Volume.TYPE, (payload, context) -> {
			AudioEngine.get().setVolume(payload.value());
			config.volume = payload.value();
			config.save();
			context.client().execute(() -> chat(Component.literal("[点歌台] ").withStyle(ChatFormatting.DARK_AQUA)
					.append(Component.literal("音量 " + Math.round(payload.value() * 100) + "%")
							.withStyle(ChatFormatting.GRAY)), true));
		});

		// 主线程只处理队列里的轻量任务（发状态、压背景音乐）
		ClientTickEvents.END_CLIENT_TICK.register(client -> AudioEngine.get().tick());

		// 断线时停止播放并清理临时状态
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			AudioEngine.get().stopAll("已断开连接");
			PlayApproval.clear();
			MentionState.clear();
			LyricService.get().reset();
		});
	}

	private static void chatPlayNotice(Packets.Play payload) {
		var message = Component.literal("[点歌台] ").withStyle(ChatFormatting.DARK_AQUA)
				.append(Component.literal(payload.title()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" — " + payload.artist()).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("  点歌人 " + payload.requester())
						.withStyle(ChatFormatting.DARK_GRAY));
		if (payload.broadcast()) {
			message.append(Component.literal(" [全服]").withStyle(ChatFormatting.GOLD));
		}
		chat(message, false);
	}

	private static void chat(Component component, boolean overlay) {
		try {
			Minecraft.getInstance().gui.chatListener().handleSystemMessage(component, overlay);
		} catch (Exception ignored) {
		}
	}
}

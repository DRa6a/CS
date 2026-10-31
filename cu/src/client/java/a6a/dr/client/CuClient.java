package a6a.dr.client;

import a6a.dr.cu.CuConfig;
import a6a.dr.cu.audio.AudioEngine;
import a6a.dr.cu.net.Packets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CuClient implements ClientModInitializer {
	public static CuConfig config;

	@Override
	public void onInitializeClient() {
		config = CuConfig.load();
		AudioEngine.get().init(config);

		// 收到播放请求
		ClientPlayNetworking.registerGlobalReceiver(Packets.Play.TYPE, (payload, context) -> {
			AudioEngine.get().enqueue(payload);
			if (config.chatMessages) {
				context.client().execute(() -> {
					var message = Component.literal("[点歌台] ").withStyle(ChatFormatting.DARK_AQUA)
							.append(Component.literal(payload.title()).withStyle(ChatFormatting.WHITE))
							.append(Component.literal(" — " + payload.artist()).withStyle(ChatFormatting.GRAY))
							.append(Component.literal("  点歌人 " + payload.requester())
									.withStyle(ChatFormatting.DARK_GRAY));
					if (payload.broadcast()) {
						message.append(Component.literal(" [全服]").withStyle(ChatFormatting.GOLD));
					}
					chat(message, false);
				});
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

		// 断线时停止播放
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AudioEngine.get().stopAll("已断开连接"));
	}

	private static void chat(Component component, boolean overlay) {
		try {
			Minecraft.getInstance().gui.chatListener().handleSystemMessage(component, overlay);
		} catch (Exception ignored) {
		}
	}
}

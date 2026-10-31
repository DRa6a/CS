package a6a.dr.si.net;

import a6a.dr.Si;
import a6a.dr.si.SiCore;
import a6a.dr.si.music.RequestLog;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;

public final class SiNetworking {
	private SiNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Packets.Play.TYPE, Packets.Play.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Packets.Stop.TYPE, Packets.Stop.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Packets.Volume.TYPE, Packets.Volume.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(Packets.Status.TYPE, Packets.Status.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Packets.Status.TYPE,
				(payload, context) -> handleStatus(payload));
	}

	private static void handleStatus(Packets.Status payload) {
		String state = switch (payload.state()) {
			case 1 -> RequestLog.PLAYING;
			case 2 -> RequestLog.DONE;
			case 3 -> RequestLog.FAILED;
			default -> RequestLog.QUEUED;
		};
		SiCore.requests().update(payload.requestId(), state, payload.message());
		if (RequestLog.FAILED.equals(state)) {
			Si.LOGGER.info("点歌 {} 播放失败：{}", payload.requestId(), payload.message());
		}
	}

	// ------------------------------------------------------------ 发包工具

	public static boolean canPlay(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, Packets.Play.TYPE);
	}

	public static void sendPlay(ServerPlayer player, String requestId, String url, String title,
								String artist, String requester, float volume, boolean broadcast) {
		ServerPlayNetworking.send(player, new Packets.Play(requestId, url, title, artist, requester, volume, broadcast));
	}

	public static void sendStop(ServerPlayer player, String reason) {
		if (!ServerPlayNetworking.canSend(player, Packets.Stop.TYPE)) return;
		ServerPlayNetworking.send(player, new Packets.Stop(reason));
	}

	public static void sendVolume(ServerPlayer player, float volume) {
		if (!ServerPlayNetworking.canSend(player, Packets.Volume.TYPE)) return;
		ServerPlayNetworking.send(player, new Packets.Volume(volume));
	}

	// ------------------------------------------------------------ 文本工具

	public static MutableComponent prefix() {
		return Component.literal("[点歌台] ").withStyle(ChatFormatting.DARK_AQUA);
	}

	public static MutableComponent link(String url) {
		return Component.literal(url).withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
	}
}

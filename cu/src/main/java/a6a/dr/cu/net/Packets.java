package a6a.dr.cu.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 与 Si 端一致的自定义数据包定义（同一套 channel id）。
 */
public final class Packets {
	private Packets() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("si", path);
	}

	private static String safe(String value, int max) {
		if (value == null) return "";
		return value.length() > max ? value.substring(0, max) : value;
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Stop.TYPE, Stop.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Volume.TYPE, Volume.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Status.TYPE, Status.CODEC);
	}

	// ------------------------------------------------------------------ S2C

	public record Play(String requestId, String url, String title, String artist,
					   String requester, float volume, boolean broadcast) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Play> TYPE = new CustomPacketPayload.Type<>(id("play"));
		public static final StreamCodec<FriendlyByteBuf, Play> CODEC = StreamCodec.of(
				(buf, value) -> {
					buf.writeUtf(safe(value.requestId, 48));
					buf.writeUtf(safe(value.url, 1024));
					buf.writeUtf(safe(value.title, 256));
					buf.writeUtf(safe(value.artist, 256));
					buf.writeUtf(safe(value.requester, 64));
					buf.writeFloat(value.volume);
					buf.writeBoolean(value.broadcast);
				},
				buf -> new Play(buf.readUtf(48), buf.readUtf(1024), buf.readUtf(256), buf.readUtf(256),
						buf.readUtf(64), buf.readFloat(), buf.readBoolean())
		);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Stop(String reason) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Stop> TYPE = new CustomPacketPayload.Type<>(id("stop"));
		public static final StreamCodec<FriendlyByteBuf, Stop> CODEC = StreamCodec.of(
				(buf, value) -> buf.writeUtf(safe(value.reason, 256)),
				buf -> new Stop(buf.readUtf(256))
		);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Volume(float value) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Volume> TYPE = new CustomPacketPayload.Type<>(id("volume"));
		public static final StreamCodec<FriendlyByteBuf, Volume> CODEC = StreamCodec.of(
				(buf, value) -> buf.writeFloat(value.value),
				buf -> new Volume(buf.readFloat())
		);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ C2S

	public record Status(String requestId, int state, String message) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Status> TYPE = new CustomPacketPayload.Type<>(id("status"));
		public static final StreamCodec<FriendlyByteBuf, Status> CODEC = StreamCodec.of(
				(buf, value) -> {
					buf.writeUtf(safe(value.requestId, 48));
					buf.writeByte(value.state);
					buf.writeUtf(safe(value.message, 256));
				},
				buf -> new Status(buf.readUtf(48), buf.readUnsignedByte(), buf.readUtf(256))
		);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}

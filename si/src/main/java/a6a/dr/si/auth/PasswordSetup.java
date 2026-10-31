package a6a.dr.si.auth;

import a6a.dr.si.SiCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 首次进服的密码设置：直接在聊天框输入密码，两次确认。
 *
 * <p>聊天内容会被 {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} 拦下，
 * 不会广播给其他玩家，也不会进聊天记录界面。</p>
 */
public final class PasswordSetup {
	private static final long TTL_MS = 5 * 60_000L;
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private static final class Pending {
		String first;
		long expiresAt = System.currentTimeMillis() + TTL_MS;
	}

	private PasswordSetup() {
	}

	public static void begin(UUID uuid) {
		if (uuid != null) PENDING.put(uuid, new Pending());
	}

	public static void cancel(UUID uuid) {
		if (uuid != null) PENDING.remove(uuid);
	}

	public static boolean isWaiting(UUID uuid) {
		if (uuid == null) return false;
		Pending pending = PENDING.get(uuid);
		if (pending == null) return false;
		if (System.currentTimeMillis() > pending.expiresAt) {
			PENDING.remove(uuid);
			return false;
		}
		return true;
	}

	/**
	 * 处理玩家在聊天框输入的一行文本。
	 *
	 * @return 回复内容；null 表示当前没有在等密码
	 */
	public static Component submit(ServerPlayer player, String text) {
		UUID uuid = player.getUUID();
		Pending pending = PENDING.get(uuid);
		if (pending == null) return null;
		if (System.currentTimeMillis() > pending.expiresAt) {
			PENDING.remove(uuid);
			return Component.literal("设置已超时，请重新输入 /si setup").withStyle(ChatFormatting.RED);
		}

		String password = text == null ? "" : text.trim();
		String error = AccountStore.validatePassword(password);
		if (error != null) {
			return Component.literal(error + "，请重新输入").withStyle(ChatFormatting.RED);
		}

		if (pending.first == null) {
			pending.first = password;
			pending.expiresAt = System.currentTimeMillis() + TTL_MS;
			return Component.literal("再输入一次确认").withStyle(ChatFormatting.GRAY);
		}

		if (!pending.first.equals(password)) {
			pending.first = null;
			return Component.literal("两次输入不一致，请重新输入密码").withStyle(ChatFormatting.RED);
		}

		String name = player.getName().getString();
		String createError = SiCore.accounts().create(name, uuid, password);
		PENDING.remove(uuid);
		if (createError != null) {
			return Component.literal(createError).withStyle(ChatFormatting.RED);
		}
		return Component.literal("密码设置成功！").withStyle(ChatFormatting.GREEN)
				.append(Component.literal(" 打开网页用游戏名 + 这个密码登录：").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(SiCore.baseUrl()).withStyle(ChatFormatting.AQUA));
	}
}

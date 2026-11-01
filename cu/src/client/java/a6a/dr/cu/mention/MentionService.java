package a6a.dr.cu.mention;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @ 提人匹配逻辑，复用自 MojiDrop 的 PlayerMentionService：
 * 从在线玩家列表里按 @ 后面的前缀匹配玩家名。
 */
public final class MentionService {
	private MentionService() {
	}

	/** 当前光标前是否是「@ 前缀」状态，且不在命令里 */
	public static boolean shouldTrigger(String input, int cursor) {
		if (input == null || cursor <= 0 || cursor > input.length()) return false;
		String before = input.substring(0, cursor);
		int at = before.lastIndexOf('@');
		if (at < 0) return false;
		String afterAt = before.substring(at + 1);
		return !afterAt.contains(" ") && !before.trim().startsWith("/");
	}

	/** @ 符号的下标；没有返回 -1 */
	public static int atIndex(String input, int cursor) {
		if (input == null || cursor <= 0) return -1;
		return input.substring(0, Math.min(cursor, input.length())).lastIndexOf('@');
	}

	/** 匹配到的玩家名（最多 8 个，按名字排序） */
	public static List<String> matches(String input, int cursor) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() == null) return List.of();
		int at = atIndex(input, cursor);
		if (at < 0) return List.of();
		String query = input.substring(at + 1, cursor).toLowerCase(Locale.ROOT);

		List<String> names = new ArrayList<>();
		for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
			GameProfile profile = info.getProfile();
			if (profile == null || profile.name() == null) continue;
			String name = profile.name();
			if (query.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(query)) {
				names.add(name);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		if (names.size() > 8) {
			return new ArrayList<>(names.subList(0, 8));
		}
		return names;
	}
}

package a6a.dr.cu.mention;

import a6a.dr.client.CuClient;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

/**
 * @ 补全面板的运行时状态：当前候选、选中项、面板矩形（供点击命中）。
 */
public final class MentionState {
	private static boolean active;
	private static List<String> matches = List.of();
	private static int selected;
	/** 程序化改写输入框期间为 true */
	private static boolean rewriting;

	private static int panelX;
	private static int panelY;
	private static int panelW;
	private static int rowH;
	private static int rowCount;

	private MentionState() {
	}

	public static void update(String input, int cursor) {
		if (CuClient.config == null || !CuClient.config.mentionEnabled
				|| !MentionService.shouldTrigger(input, cursor)) {
			clear();
			return;
		}
		List<String> found = MentionService.matches(input, cursor);
		if (found.isEmpty()) {
			clear();
			return;
		}
		matches = found;
		if (selected >= matches.size()) selected = matches.size() - 1;
		if (selected < 0) selected = 0;
		active = true;
	}

	public static void clear() {
		active = false;
		matches = List.of();
		selected = 0;
		rowCount = 0;
	}

	public static boolean isActive() {
		return active;
	}

	public static List<String> matches() {
		return matches;
	}

	public static int selected() {
		return selected;
	}

	public static void select(int index) {
		if (index >= 0 && index < matches.size()) selected = index;
	}

	public static void move(int delta) {
		if (matches.isEmpty()) return;
		selected = (selected + delta + matches.size()) % matches.size();
	}

	public static void setPanel(int x, int y, int width, int rowHeight, int rows) {
		panelX = x;
		panelY = y;
		panelW = width;
		rowH = rowHeight;
		rowCount = rows;
	}

	/** 点击落在面板第几行；不在面板内返回 -1 */
	public static int clickedIndex(double mouseX, double mouseY) {
		if (rowCount <= 0) return -1;
		if (mouseX < panelX || mouseX > panelX + panelW) return -1;
		if (mouseY < panelY || mouseY > panelY + rowCount * rowH + 4) return -1;
		int index = (int) ((mouseY - panelY - 2) / rowH);
		return index >= 0 && index < rowCount ? index : -1;
	}

	/** 把选中玩家名插入到 @ 之后 */
	public static void insert(EditBox input) {
		if (matches.isEmpty()) {
			clear();
			return;
		}
		String value = input.getValue();
		int cursor = input.getCursorPosition();
		int at = MentionService.atIndex(value, cursor);
		if (at < 0) {
			clear();
			return;
		}
		String name = matches.get(Math.max(0, Math.min(selected, matches.size() - 1)));
		String newValue = value.substring(0, at + 1) + name + " " + value.substring(cursor);
		// setValue 会同步回调 ChatScreen.onEdited，这里打个标记，
		// 让别家模组（MojiDrop 的颜文字补全）别把这次程序化修改当成玩家输入
		rewriting = true;
		try {
			input.setValue(newValue);
			input.setCursorPosition(at + 1 + name.length() + 1);
		} finally {
			rewriting = false;
		}
		clear();
	}

	/** 是否正在程序化改写输入框内容 */
	public static boolean isRewriting() {
		return rewriting;
	}
}

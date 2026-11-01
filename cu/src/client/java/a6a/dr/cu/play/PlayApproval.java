package a6a.dr.cu.play;

import a6a.dr.cu.net.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 手动同意模式下的点歌请求队列：一次只弹一个确认框，处理完再弹下一个。
 */
public final class PlayApproval {
	private static final Deque<Packets.Play> pending = new ArrayDeque<>();

	private PlayApproval() {
	}

	/** 收到一条点歌请求（自动模式不走这里）。parent 是弹框关闭后要回到的界面，可为 null。 */
	public static void offer(Packets.Play payload, Screen parent) {
		pending.add(payload);
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.gui.screen() instanceof PlayRequestScreen)) {
			showNext(parent);
		}
	}

	/** 当前确认框处理完，弹出下一个或回到 parent。 */
	public static void resolved(Screen parent) {
		pending.poll();
		showNext(parent);
	}

	/** 断线时清空待处理队列。 */
	public static void clear() {
		pending.clear();
	}

	private static void showNext(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		Packets.Play next = pending.peek();
		if (next == null) {
			minecraft.setScreenAndShow(parent);
			return;
		}
		minecraft.setScreenAndShow(new PlayRequestScreen(parent, next));
	}
}

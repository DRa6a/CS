package a6a.dr.cu.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * 播放点歌时把 Minecraft 自带背景音乐压掉，播完再恢复。
 *
 * <p>所有方法都必须在客户端主线程调用。</p>
 */
public final class MusicDucker {
	private static Double previous;

	private MusicDucker() {
	}

	public static void duck() {
		if (previous != null) return;
		try {
			Minecraft minecraft = Minecraft.getInstance();
			previous = (double) minecraft.options.getSoundSourceVolume(SoundSource.MUSIC);
			minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
			minecraft.getMusicManager().stopPlaying();
		} catch (Exception ignored) {
			previous = null;
		}
	}

	/** 每次 tick 兜底：背景音乐如果又起来了就按下去 */
	public static void keepQuiet() {
		if (previous == null) return;
		try {
			Minecraft.getInstance().getMusicManager().stopPlaying();
		} catch (Exception ignored) {
		}
	}

	public static void restore() {
		if (previous == null) return;
		try {
			Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(previous);
		} catch (Exception ignored) {
		}
		previous = null;
	}
}

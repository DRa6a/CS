package a6a.dr.cu.audio;

import a6a.dr.Cu;
import a6a.dr.cu.CuConfig;
import a6a.dr.cu.net.Packets;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 播放引擎。
 *
 * <p>线程划分（渲染线程不做任何重活）：</p>
 * <ul>
 *   <li><b>Cu-Audio</b>：解码 + 写入声卡，逐首播放</li>
 *   <li><b>Cu-Download</b>：低优先级，提前把下一首下载到缓存，避免切歌卡顿</li>
 *   <li><b>客户端主线程</b>：只从队列里取出「发状态包 / 压背景音乐」这类轻量任务执行</li>
 * </ul>
 */
public final class AudioEngine {
	public static final int STATE_QUEUED = 0;
	public static final int STATE_PLAYING = 1;
	public static final int STATE_DONE = 2;
	public static final int STATE_FAILED = 3;

	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
	private static final long MAX_BYTES = 96L * 1024 * 1024;
	private static final char[] HEX = "0123456789abcdef".toCharArray();
	/** 缓存清理最短间隔：每下一首就扫一遍目录太浪费 */
	private static final long TRIM_INTERVAL_MS = 30_000L;

	private static final AudioEngine INSTANCE = new AudioEngine();

	private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<>();
	private final Map<String, CompletableFuture<Path>> prefetched = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
	private final AtomicReference<Job> current = new AtomicReference<>();

	private ExecutorService downloader;
	private Thread worker;
	private volatile float volume = 0.8f;
	private volatile CuConfig config;
	private volatile long lastTrimAt;
	/** 只在渲染线程读写 */
	private int quietTicks;
	/** 复用的输出线路：每首歌都开关一次音频设备要几十毫秒，也容易让游戏音频抖一下 */
	private SourceDataLine sharedLine;
	private AudioFormat sharedFormat;

	private AudioEngine() {
	}

	public static AudioEngine get() {
		return INSTANCE;
	}

	public void init(CuConfig config) {
		this.config = config;
		this.volume = config.volume;
		if (downloader == null) {
			downloader = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "Cu-Download");
				thread.setDaemon(true);
				thread.setPriority(Thread.MIN_PRIORITY);
				return thread;
			});
		}
		if (worker == null) {
			worker = new Thread(this::loop, "Cu-Audio");
			worker.setDaemon(true);
			worker.start();
		}
	}

	/** 客户端 tick 调用：只做轻量主线程任务 */
	public void tick() {
		Runnable task;
		while ((task = mainThreadTasks.poll()) != null) {
			try {
				task.run();
			} catch (Exception ignored) {
			}
		}
		// 背景音乐每 4 tick（5 次/秒）按一次就够了，没必要占满 20 次
		if (current.get() != null && (++quietTicks & 3) == 0) {
			MusicDucker.keepQuiet();
		}
	}

	public float volume() {
		return volume;
	}

	public void setVolume(float value) {
		this.volume = Math.max(0f, Math.min(1f, value));
	}

	public boolean isPlaying() {
		return current.get() != null;
	}

	public int queueSize() {
		return queue.size();
	}

	// ---------------------------------------------------- 当前曲目（给歌词/HUD 用）

	public String currentRequestId() {
		Job job = current.get();
		return job == null ? null : job.requestId;
	}

	public String currentTitle() {
		Job job = current.get();
		return job == null ? null : job.title;
	}

	public String currentArtist() {
		Job job = current.get();
		return job == null ? null : job.artist;
	}

	public String currentUrl() {
		Job job = current.get();
		return job == null ? null : job.url;
	}

	/** 当前曲目已播放的毫秒数；未开始播放返回 0 */
	public long currentTimeMs() {
		Job job = current.get();
		if (job == null || job.startedAt <= 0L) return 0L;
		return Math.max(0L, System.currentTimeMillis() - job.startedAt);
	}

	public void enqueue(Packets.Play payload) {
		Job job = new Job(payload);
		queue.add(job);
		report(job, STATE_QUEUED, "");
	}

	/** 停止当前播放并清空队列 */
	public void stopAll(String reason) {
		Job job = current.get();
		if (job != null) {
			job.cancelled = true;
			// 把缓冲区里还没播完的残留直接丢掉，比关掉再重开设备快得多
			flushLine();
		}
		for (Job queued : queue) {
			queued.cancelled = true;
		}
		queue.clear();
		prefetched.values().forEach(future -> future.cancel(true));
		prefetched.clear();
		mainThreadTasks.add(MusicDucker::restore);
		Cu.LOGGER.info("停止播放：{}", reason);
	}

	// ------------------------------------------------------------ 工作线程

	private void loop() {
		while (true) {
			Job job;
			try {
				job = queue.take();
			} catch (InterruptedException e) {
				return;
			}
			if (job.cancelled) continue;
			current.set(job);
			try {
				Path file = resolve(job);
				if (job.cancelled) continue;
				job.startedAt = System.currentTimeMillis();
				mainThreadTasks.add(MusicDucker::duck);
				report(job, STATE_PLAYING, "");
				prefetchNext();
				play(file, job);
				if (!job.cancelled) report(job, STATE_DONE, "");
			} catch (Throwable e) {
				// 出错了就把线路关掉，下一首重新开，避免半死状态一直被沿用
				releaseLine();
				if (!job.cancelled) {
					Cu.LOGGER.warn("播放《{}》失败: {}", job.title, e.toString());
					report(job, STATE_FAILED, friendly(e));
				}
			} finally {
				current.compareAndSet(job, null);
				cleanupPrefetch();
				if (queue.isEmpty()) {
					mainThreadTasks.add(MusicDucker::restore);
				}
			}
		}
	}

	private static String friendly(Throwable e) {
		String message = e.getMessage();
		if (message == null || message.isBlank()) message = e.getClass().getSimpleName();
		if (message.contains("HTTP 404") || message.contains("HTTP 403")) {
			return "歌曲无法播放（可能是 VIP / 无版权）";
		}
		return message.length() > 120 ? message.substring(0, 120) : message;
	}

	private void report(Job job, int state, String message) {
		mainThreadTasks.add(() -> {
			try {
				if (ClientPlayNetworking.canSend(Packets.Status.TYPE)) {
					ClientPlayNetworking.send(new Packets.Status(job.requestId, state, message));
				}
			} catch (Exception ignored) {
			}
		});
	}

	// -------------------------------------------------------------- 下载

	private Path resolve(Job job) throws IOException {
		CompletableFuture<Path> future = prefetched.remove(job.requestId);
		if (future != null) {
			try {
				return future.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("已取消");
			} catch (Exception e) {
				Throwable cause = e.getCause() == null ? e : e.getCause();
				throw new IOException(cause.getMessage() == null ? "下载失败" : cause.getMessage());
			}
		}
		return download(job);
	}

	/** 当前歌开播后，顺手把下一首也下载了 */
	private void prefetchNext() {
		Job next = queue.peek();
		if (next == null || next.cancelled || downloader == null) return;
		prefetched.computeIfAbsent(next.requestId, key -> CompletableFuture.supplyAsync(() -> {
			try {
				return download(next);
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}, downloader));
	}

	/** 丢掉已经不在队列里的预取结果，避免这张表随着播放一直变长 */
	private void cleanupPrefetch() {
		if (prefetched.isEmpty()) return;
		Job next = queue.peek();
		String keep = next == null ? null : next.requestId;
		prefetched.keySet().removeIf(id -> !id.equals(keep));
	}

	private Path cacheDir() throws IOException {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("cu-cache");
		Files.createDirectories(dir);
		return dir;
	}

	private Path download(Job job) throws IOException {
		Path dir = cacheDir();
		String key = sha1(job.url);
		Path target = dir.resolve(key + ".bin");
		if (Files.isRegularFile(target) && Files.size(target) > 2048) {
			target.toFile().setLastModified(System.currentTimeMillis());
			return target;
		}
		Path tmp = dir.resolve(key + ".part");
		String currentUrl = job.url;
		int timeout = config == null ? 20 : config.downloadTimeoutSeconds;
		for (int hop = 0; hop < 6; hop++) {
			HttpURLConnection connection = (HttpURLConnection) URI.create(currentUrl).toURL().openConnection();
			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout(10_000);
			connection.setReadTimeout(timeout * 1000);
			connection.setRequestProperty("User-Agent", UA);
			connection.setRequestProperty("Referer", "https://music.163.com/");
			int status = connection.getResponseCode();
			if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
				String location = connection.getHeaderField("Location");
				connection.disconnect();
				if (location == null || location.isBlank()) throw new IOException("重定向缺少地址");
				currentUrl = URI.create(currentUrl).resolve(location).toString();
				continue;
			}
			if (status != 200) {
				connection.disconnect();
				throw new IOException("HTTP " + status);
			}
			long total = 0;
			boolean complete = false;
			try (InputStream in = connection.getInputStream();
				 OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), 256 * 1024)) {
				byte[] buffer = new byte[32 * 1024];
				int n;
				while (!job.cancelled && (n = in.read(buffer)) > 0) {
					total += n;
					if (total > MAX_BYTES) throw new IOException("音频文件过大");
					out.write(buffer, 0, n);
				}
				complete = !job.cancelled;
			} finally {
				// 正常下完就不断开，连接留在池里，下一首省掉一次 TCP + TLS 握手
				if (!complete) connection.disconnect();
			}
			if (job.cancelled) {
				Files.deleteIfExists(tmp);
				throw new IOException("已取消");
			}
			if (total < 2048) {
				Files.deleteIfExists(tmp);
				throw new IOException("音频内容为空（可能是 VIP / 无版权）");
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			trimCache();
			return target;
		}
		throw new IOException("重定向次数过多");
	}

	private void trimCache() {
		long now = System.currentTimeMillis();
		if (now - lastTrimAt < TRIM_INTERVAL_MS) return;
		lastTrimAt = now;
		try {
			Path dir = cacheDir();
			long limit = (long) (config == null ? 512 : config.maxCacheMb) * 1024 * 1024;
			long total = 0;
			List<Path> files = new ArrayList<>();
			try (Stream<Path> stream = Files.list(dir)) {
				for (Path path : stream.toList()) {
					if (!Files.isRegularFile(path)) continue;
					total += Files.size(path);
					files.add(path);
				}
			}
			if (total <= limit) return;
			files.sort(Comparator.comparingLong(path -> path.toFile().lastModified()));
			for (Path path : files) {
				if (total <= limit) break;
				long size = Files.size(path);
				if (Files.deleteIfExists(path)) total -= size;
			}
		} catch (Exception ignored) {
		}
	}

	private static String sha1(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
			char[] out = new char[digest.length * 2];
			for (int i = 0; i < digest.length; i++) {
				int value = digest[i] & 0xFF;
				out[i * 2] = HEX[value >>> 4];
				out[i * 2 + 1] = HEX[value & 0x0F];
			}
			return new String(out);
		} catch (Exception e) {
			return Integer.toHexString(text.hashCode());
		}
	}

	// -------------------------------------------------------------- 播放

	private void play(Path file, Job job) throws Exception {
		byte[] head = new byte[4];
		try (InputStream in = Files.newInputStream(file)) {
			int read = in.read(head);
			if (read < 4) throw new IOException("音频文件损坏");
		}
		if (isMp3(head)) {
			playMp3(file, job);
		} else {
			playGeneric(file, job);
		}
	}

	private static boolean isMp3(byte[] head) {
		if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return true;
		int b0 = head[0] & 0xFF;
		int b1 = head[1] & 0xFF;
		return b0 == 0xFF && (b1 & 0xE0) == 0xE0;
	}

	private void playMp3(Path file, Job job) throws Exception {
		try (InputStream raw = new BufferedInputStream(Files.newInputStream(file), 128 * 1024)) {
			Bitstream bitstream = new Bitstream(raw);
			Decoder decoder = new Decoder();
			SourceDataLine line = null;
			// 复用同一块 PCM 缓冲：原来每帧 new 一次（约 38 帧/秒），持续播放时一直在制造垃圾
			byte[] pcm = new byte[8192];
			try {
				Header header;
				while (!job.cancelled && (header = bitstream.readFrame()) != null) {
					SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
					if (line == null) {
						AudioFormat format = new AudioFormat(decoder.getOutputFrequency(), 16,
								decoder.getOutputChannels(), true, false);
						line = acquireLine(format);
					}
					short[] buffer = samples.getBuffer();
					int length = samples.getBufferLength();
					int bytes = length * 2;
					if (pcm.length < bytes) pcm = new byte[bytes];
					float gain = gain(job);
					for (int i = 0, j = 0; i < length; i++, j += 2) {
						int value = (int) (buffer[i] * gain);
						if (value > 32767) value = 32767;
						else if (value < -32768) value = -32768;
						pcm[j] = (byte) value;
						pcm[j + 1] = (byte) (value >> 8);
					}
					line.write(pcm, 0, bytes);
					bitstream.closeFrame();
				}
				if (line != null && !job.cancelled) line.drain();
			} finally {
				try {
					bitstream.close();
				} catch (Exception ignored) {
				}
			}
		}
	}

	private void playGeneric(Path file, Job job) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat format = in.getFormat();
			SourceDataLine line = acquireLine(format);
			boolean pcm16 = format.getSampleSizeInBits() == 16 && !format.isBigEndian();
			byte[] buffer = new byte[16 * 1024];
			int n;
			while (!job.cancelled && (n = in.read(buffer)) > 0) {
				if (pcm16) applyGain(buffer, n, gain(job));
				line.write(buffer, 0, n);
			}
			if (!job.cancelled) line.drain();
		}
	}

	private static void applyGain(byte[] buffer, int length, float gain) {
		for (int i = 0; i + 1 < length; i += 2) {
			int value = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
			value = (int) (value * gain);
			if (value > 32767) value = 32767;
			else if (value < -32768) value = -32768;
			buffer[i] = (byte) value;
			buffer[i + 1] = (byte) (value >> 8);
		}
	}

	/** 开场 500ms 淡入，避免爆音 */
	private float gain(Job job) {
		long elapsed = System.currentTimeMillis() - job.startedAt;
		float fade = Math.min(1f, Math.max(0f, elapsed / 500f));
		return volume * fade;
	}

	/**
	 * 拿一条输出线路。格式没变就复用上一条——每次 getSourceDataLine + open + close
	 * 在 Windows 上要几十毫秒，反复申请/释放音频设备也容易让游戏那边抖一下。
	 */
	private synchronized SourceDataLine acquireLine(AudioFormat format) throws Exception {
		if (sharedLine != null && sharedFormat != null && sharedFormat.matches(format)) {
			return sharedLine;
		}
		closeShared();
		SourceDataLine line = AudioSystem.getSourceDataLine(format);
		line.open(format, 1 << 16);
		line.start();
		sharedLine = line;
		sharedFormat = format;
		return line;
	}

	/** 丢掉缓冲区里还没播完的声音（停止播放时用），线路本身留着复用 */
	private void flushLine() {
		SourceDataLine line = sharedLine;
		if (line == null) return;
		try {
			line.flush();
		} catch (Exception ignored) {
		}
	}

	/** 关闭并丢弃复用的线路（播放出错时用，下一首会重新开） */
	private synchronized void releaseLine() {
		closeShared();
	}

	private void closeShared() {
		SourceDataLine line = sharedLine;
		sharedLine = null;
		sharedFormat = null;
		if (line == null) return;
		try {
			line.stop();
			line.flush();
			line.close();
		} catch (Exception ignored) {
		}
	}

	// ---------------------------------------------------------------- Job

	private static final class Job {
		final String requestId;
		final String url;
		final String title;
		final String artist;
		final String requester;
		final boolean broadcast;
		volatile boolean cancelled;
		volatile long startedAt = 0L;

		Job(Packets.Play payload) {
			this.requestId = payload.requestId();
			this.url = payload.url();
			this.title = payload.title();
			this.artist = payload.artist();
			this.requester = payload.requester();
			this.broadcast = payload.broadcast();
		}
	}
}

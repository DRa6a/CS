package a6a.dr.si.web;

import a6a.dr.Si;
import a6a.dr.si.SiConfig;
import a6a.dr.si.SiCore;
import a6a.dr.si.auth.AccountStore;
import a6a.dr.si.auth.SessionStore;
import a6a.dr.si.message.ChatService;
import a6a.dr.si.music.FallbackSource;
import a6a.dr.si.music.MusicService;
import a6a.dr.si.music.NeteaseStore;
import a6a.dr.si.music.QrCodes;
import a6a.dr.si.music.RequestLog;
import a6a.dr.si.net.SiNetworking;
import a6a.dr.si.player.OnlineRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;

/**
 * 内嵌 HTTP 服务：静态页面 + JSON API。
 *
 * <p>网页只负责登录和点歌，密码在游戏内聊天框设置。</p>
 */
public final class WebServer {
	private static final String COOKIE = "si_session";
	private static final String ALL = "__all__";
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Map<String, String> MIME = Map.of(
			"html", "text/html; charset=utf-8",
			"css", "text/css; charset=utf-8",
			"js", "application/javascript; charset=utf-8",
			"json", "application/json; charset=utf-8",
			"png", "image/png",
			"svg", "image/svg+xml",
			"ico", "image/x-icon"
	);

	private final SiConfig config;
	private final HttpServer http;
	private final ExecutorService pool;
	private final Map<String, byte[]> assets = new ConcurrentHashMap<>();
	/** 预压好的 gzip 版本，省得每次请求现压 */
	private final Map<String, byte[]> gzipped = new ConcurrentHashMap<>();
	private final Map<String, String> etags = new ConcurrentHashMap<>();
	private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
	private final Map<String, Long> messageCooldowns = new ConcurrentHashMap<>();
	private final Map<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<>();
	private final Map<String, QrEntry> qrSessions = new ConcurrentHashMap<>();

	private record QrEntry(String owner, long expiresAt) {
	}

	public WebServer(SiConfig config) throws IOException {
		this.config = config;
		loadAssets();
		InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(config.bindAddress), config.port);
		this.http = HttpServer.create(address, 128);
		this.pool = newExecutor();
		this.http.setExecutor(pool);
		this.http.createContext("/", this::handle);
	}

	/**
	 * 网页线程池。
	 *
	 * <p>搜歌 / 探测播放地址都是阻塞的外网请求，固定 4 个线程时几个人同时搜歌就会把
	 * 轮询接口（每 3 秒一次）一起堵住。这里用「按需扩容 + 用完回收」的池子，
	 * 队列满了直接交给调度线程跑，保证不会无限堆积。</p>
	 *
	 * <p>核心线程也允许超时回收：没人用网页的时候线程数会归零，不白占栈内存。</p>
	 */
	private static ThreadPoolExecutor newExecutor() {
		AtomicInteger counter = new AtomicInteger();
		int core = Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));
		int max = Math.max(16, core * 8);
		ThreadPoolExecutor pool = new ThreadPoolExecutor(core, max, 60L, TimeUnit.SECONDS,
				new SynchronousQueue<>(), runnable -> {
					Thread thread = new Thread(runnable, "Si-Web-" + counter.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				}, new ThreadPoolExecutor.CallerRunsPolicy());
		pool.allowCoreThreadTimeOut(true);
		return pool;
	}

	public void start() {
		http.start();
	}

	public void stop() {
		try {
			http.stop(0);
		} catch (Exception ignored) {
		}
		pool.shutdownNow();
	}

	private void loadAssets() {
		for (String name : List.of("index.html", "app.css", "app.js")) {
			try (InputStream in = WebServer.class.getResourceAsStream("/assets/si/web/" + name)) {
				if (in == null) {
					Si.LOGGER.error("缺少网页资源: {}", name);
					continue;
				}
				byte[] data = in.readAllBytes();
				assets.put(name, data);
				etags.put(name, etagOf(data));
				// 文本资源启动时就压好，之后每次请求直接吐 gzip
				if (compressible(name)) {
					byte[] small = gzip(data);
					if (small != null && small.length < data.length) {
						gzipped.put(name, small);
					}
				}
			} catch (IOException e) {
				Si.LOGGER.error("读取网页资源失败: {}", name, e);
			}
		}
	}

	private static boolean compressible(String name) {
		return name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js")
				|| name.endsWith(".json") || name.endsWith(".svg");
	}

	private static byte[] gzip(byte[] data) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, data.length / 3));
			try (GZIPOutputStream gzip = new GZIPOutputStream(out, 8192)) {
				gzip.write(data);
			}
			return out.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	private static String etagOf(byte[] data) {
		CRC32 crc = new CRC32();
		crc.update(data);
		return "\"" + Integer.toHexString(data.length) + "-" + Long.toHexString(crc.getValue()) + "\"";
	}

	private static boolean acceptsGzip(HttpExchange exchange) {
		String header = exchange.getRequestHeaders().getFirst("Accept-Encoding");
		return header != null && header.toLowerCase(Locale.ROOT).contains("gzip");
	}

	// ------------------------------------------------------------- 分发

	private void handle(HttpExchange exchange) {
		String path = exchange.getRequestURI().getPath();
		try {
			if (path.startsWith("/api/")) {
				handleApi(exchange, path);
			} else {
				handleStatic(exchange, path);
			}
		} catch (Exception e) {
			Si.LOGGER.warn("处理网页请求出错: {} {}", exchange.getRequestMethod(), path, e);
			try {
				JsonObject body = new JsonObject();
				body.addProperty("ok", false);
				body.addProperty("error", "服务器内部错误");
				sendJson(exchange, 500, body);
			} catch (Exception ignored) {
			}
		} finally {
			exchange.close();
		}
	}

	private void handleStatic(HttpExchange exchange, String path) throws IOException {
		String name = (path.isEmpty() || path.equals("/")) ? "index.html" : path.substring(1);
		if (name.contains("..")) {
			exchange.sendResponseHeaders(404, -1);
			return;
		}
		byte[] data = assets.get(name);
		if (data == null) {
			byte[] text = "404 Not Found".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
			exchange.sendResponseHeaders(404, text.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(text);
			}
			return;
		}

		String etag = etags.get(name);
		if (etag != null) {
			exchange.getResponseHeaders().set("ETag", etag);
			exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
			// 内容没变就直接 304，别再传一遍几十 KB
			if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
				exchange.sendResponseHeaders(304, -1);
				return;
			}
		}

		byte[] gz = gzipped.get(name);
		boolean useGzip = gz != null && acceptsGzip(exchange);
		byte[] body = useGzip ? gz : data;

		int dot = name.lastIndexOf('.');
		String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
		exchange.getResponseHeaders().set("Content-Type", MIME.getOrDefault(ext, "application/octet-stream"));
		exchange.getResponseHeaders().set("Cache-Control", name.endsWith(".html") ? "no-store" : "public, max-age=600");
		if (useGzip) {
			exchange.getResponseHeaders().set("Content-Encoding", "gzip");
		}
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private void handleApi(HttpExchange exchange, String path) throws IOException {
		switch (path) {
			case "/api/login" -> apiLogin(exchange);
			case "/api/logout" -> apiLogout(exchange);
			case "/api/me" -> apiMe(exchange);
			case "/api/password" -> apiPassword(exchange);
			case "/api/netease/status" -> apiNeteaseStatus(exchange);
			case "/api/netease/login" -> apiNeteaseLogin(exchange);
			case "/api/netease/qr" -> apiNeteaseQr(exchange);
			case "/api/netease/qr/poll" -> apiNeteaseQrPoll(exchange);
			case "/api/netease/cookie" -> apiNeteaseCookie(exchange);
			case "/api/netease/logout" -> apiNeteaseLogout(exchange);
			case "/api/players" -> apiPlayers(exchange);
			case "/api/search" -> apiSearch(exchange);
			case "/api/request" -> apiRequest(exchange);
			case "/api/entries" -> apiEntries(exchange);
			case "/api/message" -> apiMessage(exchange);
			case "/api/chat" -> apiChat(exchange);
			case "/api/health" -> sendJson(exchange, 200, ok());
			default -> sendError(exchange, 404, "接口不存在");
		}
	}

	// --------------------------------------------------------------- 账号

	private void apiLogin(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			sendError(exchange, 405, "请使用 POST");
			return;
		}
		String ip = ipOf(exchange);
		if (!allowLogin(ip)) {
			sendError(exchange, 429, "尝试次数过多，请稍后再试");
			return;
		}
		JsonObject body = readJson(exchange);
		String username = str(body, "username");
		String password = str(body, "password");
		if (username.isEmpty() || password.isEmpty()) {
			sendError(exchange, 400, "请输入玩家名和密码");
			return;
		}
		if (!SiCore.accounts().exists(username)) {
			sendError(exchange, 400, "该玩家还没有设置密码，请先进服在聊天框设置");
			return;
		}
		if (!SiCore.accounts().verify(username, password)) {
			sendError(exchange, 401, "玩家名或密码错误");
			return;
		}
		String token = SiCore.sessions().create(username, SiCore.uuidByName(username), ip);
		setCookie(exchange, token);
		sendJson(exchange, 200, userPayload(username));
	}

	private void apiLogout(HttpExchange exchange) throws IOException {
		SiCore.sessions().remove(cookie(exchange, COOKIE));
		exchange.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; HttpOnly; Max-Age=0");
		sendJson(exchange, 200, ok());
	}

	private void apiMe(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			JsonObject body = new JsonObject();
			body.addProperty("ok", true);
			body.addProperty("loggedIn", false);
			body.add("server", serverPayload());
			sendJson(exchange, 200, body);
			return;
		}
		sendJson(exchange, 200, userPayload(session.name()));
	}

	private void apiPassword(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			sendError(exchange, 405, "请使用 POST");
			return;
		}
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		JsonObject body = readJson(exchange);
		String oldPassword = str(body, "oldPassword");
		String newPassword = str(body, "newPassword");
		if (!SiCore.accounts().verify(session.name(), oldPassword)) {
			sendError(exchange, 400, "原密码不正确");
			return;
		}
		String error = AccountStore.validatePassword(newPassword);
		if (error != null) {
			sendError(exchange, 400, error);
			return;
		}
		if (oldPassword.equals(newPassword)) {
			sendError(exchange, 400, "新密码和原密码一样");
			return;
		}
		String result = SiCore.accounts().resetPassword(session.name(), newPassword);
		if (result != null) {
			sendError(exchange, 400, result);
			return;
		}
		Si.LOGGER.info("玩家 {} 修改了网页密码", session.name());
		JsonObject response = ok();
		response.addProperty("message", "密码已更新，下次登录请使用新密码");
		sendJson(exchange, 200, response);
	}

	// --------------------------------------------------------- 网易云账号

	private void apiNeteaseStatus(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		JsonObject body = ok();
		body.add("netease", neteasePayload(session.name()));
		sendJson(exchange, 200, body);
	}

	private void apiNeteaseLogin(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			sendError(exchange, 405, "请使用 POST");
			return;
		}
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		if (!config.neteaseLogin) {
			sendError(exchange, 403, "服务器已关闭网易云登录");
			return;
		}
		JsonObject request = readJson(exchange);
		String phone = str(request, "phone");
		String password = str(request, "password");
		if (phone.isEmpty() || password.isEmpty()) {
			sendError(exchange, 400, "请输入手机号和密码");
			return;
		}
		try {
			MusicService.Profile profile = SiCore.music().login(phone, password);
			saveNetease(session.name(), profile);
			JsonObject body = ok();
			body.addProperty("nickname", profile.nickname());
			body.add("netease", neteasePayload(session.name()));
			sendJson(exchange, 200, body);
		} catch (IOException e) {
			sendError(exchange, 400, e.getMessage());
		} catch (Exception e) {
			Si.LOGGER.warn("网易云登录异常", e);
			sendError(exchange, 500, "登录失败：" + e.getMessage());
		}
	}

	/** 扫码登录：申请 key + 二维码图片 */
	private void apiNeteaseQr(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			sendError(exchange, 405, "请使用 POST");
			return;
		}
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		if (!config.neteaseLogin) {
			sendError(exchange, 403, "服务器已关闭网易云登录");
			return;
		}
		long now = System.currentTimeMillis();
		qrSessions.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
		try {
			String key = SiCore.music().qrKey();
			String image = QrCodes.pngDataUrl("https://music.163.com/login?codekey=" + key, 260);
			if (image == null) {
				sendError(exchange, 500, "二维码生成失败");
				return;
			}
			qrSessions.put(key, new QrEntry(session.name(), now + 180_000L));
			JsonObject body = ok();
			body.addProperty("key", key);
			body.addProperty("image", image);
			sendJson(exchange, 200, body);
		} catch (IOException e) {
			sendError(exchange, 502, e.getMessage());
		}
	}

	/** 扫码登录：轮询状态 */
	private void apiNeteaseQrPoll(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		String key = queryParam(exchange, "key");
		QrEntry entry = key == null ? null : qrSessions.get(key);
		if (entry == null || entry.expiresAt < System.currentTimeMillis()
				|| !entry.owner.equalsIgnoreCase(session.name())) {
			sendError(exchange, 400, "二维码已过期，请刷新");
			return;
		}
		try {
			MusicService.QrState state = SiCore.music().qrPoll(key);
			String status = switch (state.code()) {
				case 800 -> "EXPIRED";
				case 802 -> "SCANNED";
				case 803 -> "CONFIRMED";
				default -> "WAITING";
			};
			JsonObject body = ok();
			body.addProperty("status", status);
			body.addProperty("message", state.message());
			if ("CONFIRMED".equals(status) && state.profile() != null) {
				saveNetease(session.name(), state.profile());
				qrSessions.remove(key);
				body.add("netease", neteasePayload(session.name()));
				body.addProperty("nickname", state.profile().nickname());
			}
			if ("EXPIRED".equals(status)) {
				qrSessions.remove(key);
			}
			sendJson(exchange, 200, body);
		} catch (IOException e) {
			sendError(exchange, 502, e.getMessage());
		}
	}

	/** 兜底方式：直接粘贴浏览器里的 MUSIC_U */	private void apiNeteaseCookie(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			sendError(exchange, 405, "请使用 POST");
			return;
		}
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		if (!config.neteaseLogin) {
			sendError(exchange, 403, "服务器已关闭网易云登录");
			return;
		}
		JsonObject request = readJson(exchange);
		String musicU = str(request, "musicU");
		if (musicU.contains("MUSIC_U=")) {
			musicU = musicU.substring(musicU.indexOf("MUSIC_U=") + "MUSIC_U=".length());
		}
		int semicolon = musicU.indexOf(';');
		if (semicolon >= 0) musicU = musicU.substring(0, semicolon);
		musicU = musicU.trim();
		if (musicU.isEmpty()) {
			sendError(exchange, 400, "请填写 MUSIC_U");
			return;
		}
		MusicService.Profile profile = SiCore.music().profile(musicU);
		if (profile == null) {
			sendError(exchange, 400, "Cookie 无效或已过期，请重新从浏览器复制");
			return;
		}
		saveNetease(session.name(), profile);
		JsonObject body = ok();
		body.addProperty("nickname", profile.nickname());
		body.add("netease", neteasePayload(session.name()));
		sendJson(exchange, 200, body);
	}

	private void apiNeteaseLogout(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		SiCore.netease().remove(session.name());
		JsonObject body = ok();
		body.add("netease", neteasePayload(session.name()));
		sendJson(exchange, 200, body);
	}

	private void saveNetease(String mcName, MusicService.Profile profile) {
		NeteaseStore.Entry entry = new NeteaseStore.Entry();
		entry.musicU = profile.musicU();
		entry.nickname = profile.nickname();
		entry.uid = profile.uid();
		entry.savedAt = System.currentTimeMillis();
		SiCore.netease().put(mcName, entry);
		Si.LOGGER.info("玩家 {} 绑定了网易云账号 {}", mcName, profile.nickname());
	}

	// --------------------------------------------------------------- 点歌

	private void apiPlayers(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		JsonArray array = new JsonArray();
		for (OnlineRegistry.Info info : OnlineRegistry.all()) {
			JsonObject item = new JsonObject();
			item.addProperty("name", info.name());
			item.addProperty("hasCu", info.hasCu());
			item.addProperty("self", info.name().equalsIgnoreCase(session.name()));
			array.add(item);
		}
		JsonObject body = ok();
		body.add("players", array);
		body.addProperty("allowBroadcast", config.allowBroadcast);
		sendJsonCacheable(exchange, body);
	}

	private void apiSearch(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		String keyword = queryParam(exchange, "q");
		if (keyword.isBlank()) {
			JsonObject body = ok();
			body.add("songs", new JsonArray());
			sendJson(exchange, 200, body);
			return;
		}
		try {
			String cookie = SiCore.netease().cookie(session.name());
			List<MusicService.Song> songs = SiCore.music().search(keyword, 20, cookie);

			// 批量查一次播放地址，顺便标出「可播 / 需要登录 / 无版权」
			List<Long> ids = new java.util.ArrayList<>(songs.size());
			for (MusicService.Song song : songs) ids.add(song.id());
			Map<Long, MusicService.Resolved> resolved = SiCore.music().resolveUrls(ids, cookie);

			JsonArray array = new JsonArray();
			for (MusicService.Song song : songs) {
				MusicService.Resolved state = resolved.get(song.id());
				String playState = state == null ? "UNKNOWN"
						: state.playable() ? "OK"
						: state.needLogin() ? "LOGIN" : "NONE";
				JsonObject item = new JsonObject();
				item.addProperty("id", song.id());
				item.addProperty("name", song.name());
				item.addProperty("artist", song.artist());
				item.addProperty("album", song.album());
				item.addProperty("duration", song.durationMs());
				item.addProperty("cover", song.cover());
				item.addProperty("fee", song.fee());
				item.addProperty("state", playState);
				item.addProperty("url", state != null && state.playable()
						? state.url()
						: SiCore.music().outerUrl(song.id()));
				array.add(item);
			}
			JsonObject body = ok();
			body.add("songs", array);
			body.addProperty("neteaseBound", cookie != null);
			sendJson(exchange, 200, body);
		} catch (Exception e) {
			sendError(exchange, 502, "网易云搜索失败：" + e.getMessage() + "（可以试试直接粘贴歌曲直链）");
		}
	}

	private void apiRequest(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			sendError(exchange, 405, "请使用 POST");
			return;
		}
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}

		JsonObject body = readJson(exchange);
		String title = str(body, "name");
		String artist = str(body, "artist");
		String rawUrl = str(body, "url");
		long songId = longValue(body, "id");
		String targetName = str(body, "target");
		boolean broadcast = ALL.equals(targetName) && config.allowBroadcast;

		// 决定放给谁听：点歌人自己 / 指定玩家 / 全服
		OnlineRegistry.Info requester = OnlineRegistry.byName(session.name());
		UUID targetUuid = null;
		if (!broadcast) {
			if (targetName.isBlank() || targetName.equalsIgnoreCase("self")) {
				if (requester == null) {
					sendError(exchange, 400, "你当前不在服务器里，请选择一位在线玩家");
					return;
				}
				targetName = requester.name();
			}
			OnlineRegistry.Info target = OnlineRegistry.byName(targetName);
			if (target == null) {
				sendError(exchange, 400, "玩家 " + targetName + " 不在线");
				return;
			}
			if (!target.hasCu()) {
				sendError(exchange, 400, target.name() + " 的客户端没有安装 Cu 模组");
				return;
			}
			targetName = target.name();
			targetUuid = target.uuid();
		}

		long now = System.currentTimeMillis();
		long cooldownMs = config.requestCooldownSeconds * 1000L;
		String cooldownKey = session.name().toLowerCase(Locale.ROOT);
		Long last = cooldowns.get(cooldownKey);
		if (last != null && now - last < cooldownMs) {
			long wait = (cooldownMs - (now - last) + 999) / 1000;
			sendError(exchange, 429, "点歌太快了，请 " + wait + " 秒后再试");
			return;
		}

		String cookie = SiCore.netease().cookie(session.name());
		boolean neteaseBound = cookie != null;
		if (title.isBlank()) title = "未知歌曲";
		if (artist.isBlank()) artist = "未知歌手";

		String url = null;
		String source = "netease";
		if (songId > 0) {
			// 按 id 点歌时总是重新解析，拿到最新的（带签名的）地址
			url = SiCore.music().resolveUrl(songId, cookie);
		}
		if (url == null && !rawUrl.isBlank()) {
			url = rawUrl;
		}
		if (url == null && songId > 0) {
			url = SiCore.music().outerUrl(songId);
		}
		if (url == null || url.isBlank()) {
			sendError(exchange, 400, "缺少歌曲地址");
			return;
		}
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			sendError(exchange, 400, "只支持 http/https 音频地址");
			return;
		}

		MusicService.Probe probe = SiCore.music().probe(url);

		// 主源放不了 → 试一次备用源
		if (!probe.ok() && songId > 0) {
			FallbackSource fallback = SiCore.fallback();
			if (fallback != null && fallback.enabled()) {
				String alternative = fallback.resolve(songId, title, artist);
				if (alternative != null && !alternative.isBlank()) {
					MusicService.Probe altProbe = SiCore.music().probe(alternative);
					if (altProbe.ok()) {
						url = alternative;
						probe = altProbe;
						source = "fallback";
						Si.LOGGER.info("《{}》主源不可用，已改用备用源", title);
					}
				}
			}
		}

		if (!probe.ok()) {
			String message = probe.error() == null ? "这首歌暂时无法播放" : probe.error();
			if (!neteaseBound && songId > 0 && message.contains("VIP")) {
				message += "，在「账号设置 → 网易云账号」绑定后可以播放 VIP 歌曲";
			}
			sendError(exchange, 400, message);
			return;
		}

		String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		RequestLog.Entry entry = new RequestLog.Entry();
		entry.id = requestId;
		entry.player = session.name();
		entry.target = broadcast ? "" : targetName;
		entry.title = title;
		entry.artist = artist;
		entry.url = url;
		entry.broadcast = broadcast;
		entry.requestedAt = now;
		entry.state = RequestLog.QUEUED;
		SiCore.requests().add(entry);
		cooldowns.put(cooldownKey, now);

		dispatch(targetUuid, requestId, url, title, artist, session.name(), broadcast);

		JsonObject response = ok();
		response.addProperty("requestId", requestId);
		response.addProperty("title", title);
		response.addProperty("broadcast", broadcast);
		response.addProperty("target", broadcast ? "" : targetName);
		response.addProperty("source", source);
		sendJson(exchange, 200, response);
	}

	private void apiEntries(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		JsonArray array = new JsonArray();
		for (RequestLog.Entry entry : SiCore.requests().recent(config.historySize)) {
			JsonObject item = new JsonObject();
			item.addProperty("id", entry.id);
			item.addProperty("player", entry.player);
			item.addProperty("target", entry.target == null ? "" : entry.target);
			item.addProperty("title", entry.title);
			item.addProperty("artist", entry.artist);
			item.addProperty("state", entry.state);
			item.addProperty("message", entry.message);
			item.addProperty("broadcast", entry.broadcast);
			item.addProperty("requestedAt", entry.requestedAt);
			item.addProperty("mine", entry.player.equalsIgnoreCase(session.name()));
			array.add(item);
		}
		JsonObject body = ok();
		body.add("entries", array);
		body.addProperty("online", OnlineRegistry.size());
		sendJsonCacheable(exchange, body);
	}

	// --------------------------------------------------------------- 消息

	/** 网页发一条消息，直接出现在目标玩家（或全服）的聊天栏里 */
	private void apiMessage(HttpExchange exchange) throws IOException {
		if (!"POST".equals(exchange.getRequestMethod())) {
			sendError(exchange, 405, "请使用 POST");
			return;
		}
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}

		JsonObject request = readJson(exchange);
		String text = ChatService.clean(str(request, "text"));
		if (text.isEmpty()) {
			sendError(exchange, 400, "先写点什么吧");
			return;
		}

		String targetName = str(request, "target");
		boolean broadcast = ALL.equals(targetName) && config.allowBroadcast;

		long now = System.currentTimeMillis();
		long cooldownMs = 3000L;
		String cooldownKey = session.name().toLowerCase(Locale.ROOT);
		Long last = messageCooldowns.get(cooldownKey);
		if (last != null && now - last < cooldownMs) {
			long wait = (cooldownMs - (now - last) + 999) / 1000;
			sendError(exchange, 429, "发得太快了，请 " + wait + " 秒后再试");
			return;
		}

		UUID targetUuid = null;
		if (!broadcast) {
			if (targetName.isBlank() || targetName.equalsIgnoreCase("self")) {
				OnlineRegistry.Info self = OnlineRegistry.byName(session.name());
				if (self == null) {
					sendError(exchange, 400, "你当前不在服务器里，请选择一位在线玩家");
					return;
				}
				targetName = self.name();
			}
			OnlineRegistry.Info target = OnlineRegistry.byName(targetName);
			if (target == null) {
				sendError(exchange, 400, "玩家 " + targetName + " 不在线");
				return;
			}
			targetName = target.name();
			targetUuid = target.uuid();
		}

		MinecraftServer server = SiCore.server;
		if (server == null) {
			sendError(exchange, 503, "服务器还没准备好");
			return;
		}

		ChatService.Line line = SiCore.chat().add(session.name(), text, ChatService.WEB);
		messageCooldowns.put(cooldownKey, now);

		final UUID uuid = targetUuid;
		final String to = targetName;
		final boolean toAll = broadcast;
		server.executeIfPossible(() -> deliver(uuid, session.name(), text, to, toAll));

		JsonObject body = ok();
		body.addProperty("id", line.id);
		body.addProperty("text", text);
		body.addProperty("target", broadcast ? "" : to);
		body.addProperty("broadcast", broadcast);
		body.addProperty("at", line.at);
		sendJson(exchange, 200, body);
	}

	/** 游戏内对话：玩家在游戏里说的话 + 网页发进来的消息 */
	private void apiChat(HttpExchange exchange) throws IOException {
		SessionStore.Session session = session(exchange);
		if (session == null) {
			sendError(exchange, 401, "请先登录");
			return;
		}
		JsonArray array = new JsonArray();
		for (ChatService.Line line : SiCore.chat().recent(80)) {
			JsonObject item = new JsonObject();
			item.addProperty("id", line.id);
			item.addProperty("name", line.name);
			item.addProperty("text", line.text);
			item.addProperty("source", line.source);
			item.addProperty("at", line.at);
			item.addProperty("mine", line.name.equalsIgnoreCase(session.name()));
			item.addProperty("avatar", avatarFor(line.name, SiCore.accounts().get(line.name)));
			array.add(item);
		}
		JsonObject body = ok();
		body.add("messages", array);
		body.addProperty("maxLength", ChatService.MAX_LENGTH);
		sendJsonCacheable(exchange, body);
	}

	/** 必须在服务端线程执行 */
	private void deliver(UUID targetUuid, String from, String text, String to, boolean broadcast) {
		MinecraftServer server = SiCore.server;
		if (server == null) return;
		// 尽量贴近原版聊天：<名字> 内容，网页来的在行首加个 [Web]
		MutableComponent line = Component.literal("[Web] ").withStyle(ChatFormatting.DARK_AQUA)
				.append(Component.literal("<" + from + "> ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
		if (broadcast) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.sendSystemMessage(line);
			}
			return;
		}
		ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
		if (target != null) {
			target.sendSystemMessage(line);
			return;
		}
		Si.LOGGER.info("消息没能送达 {}（已离线）：{}", to, text);
	}

	// --------------------------------------------------------- 内部工具

	private void dispatch(UUID targetUuid, String requestId, String url, String title,
						  String artist, String requester, boolean broadcast) {
		MinecraftServer server = SiCore.server;
		if (server == null) {
			SiCore.requests().update(requestId, RequestLog.FAILED, "服务器未就绪");
			return;
		}
		server.executeIfPossible(() -> {
			if (broadcast) {
				int sent = 0;
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (SiNetworking.canPlay(player)) {
						SiNetworking.sendPlay(player, requestId, url, title, artist, requester,
								SiCore.volumeOf(player.getUUID()), true);
						sent++;
					}
				}
				if (sent == 0) {
					SiCore.requests().update(requestId, RequestLog.FAILED, "没有可播放的客户端");
				}
				return;
			}
			ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
			if (target == null) {
				SiCore.requests().update(requestId, RequestLog.FAILED, "玩家已离线");
				return;
			}
			if (!SiNetworking.canPlay(target)) {
				SiCore.requests().update(requestId, RequestLog.FAILED, "客户端未安装 Cu 模组");
				return;
			}
			SiNetworking.sendPlay(target, requestId, url, title, artist, requester,
					SiCore.volumeOf(targetUuid), false);
		});
	}

	private JsonObject userPayload(String username) {
		JsonObject body = ok();
		body.addProperty("loggedIn", true);
		OnlineRegistry.Info info = OnlineRegistry.byName(username);
		AccountStore.Account account = SiCore.accounts().get(username);
		JsonObject user = new JsonObject();
		user.addProperty("name", username);
		user.addProperty("online", info != null);
		user.addProperty("hasCu", info != null && info.hasCu());
		user.addProperty("avatar", avatarFor(username, account));
		user.addProperty("createdAt", account == null ? 0L : account.createdAt);
		body.add("user", user);
		body.add("netease", neteasePayload(username));
		body.add("server", serverPayload());
		return body;
	}

	private String avatarFor(String name, AccountStore.Account account) {
		String template = config.avatarUrl;
		if (template == null || template.isBlank()) return "";
		String uuid = account == null || account.uuid == null ? "" : account.uuid;
		return template
				.replace("{name}", java.net.URLEncoder.encode(name, StandardCharsets.UTF_8))
				.replace("{uuid}", uuid);
	}

	private JsonObject neteasePayload(String username) {
		NeteaseStore.Entry entry = SiCore.netease() == null ? null : SiCore.netease().get(username);
		JsonObject body = new JsonObject();
		body.addProperty("enabled", config.neteaseLogin);
		body.addProperty("bound", entry != null);
		body.addProperty("nickname", entry == null ? "" : entry.nickname);
		return body;
	}

	private JsonObject serverPayload() {
		JsonObject server = new JsonObject();
		server.addProperty("name", config.serverName);
		server.addProperty("url", SiCore.baseUrl());
		server.addProperty("online", OnlineRegistry.size());
		server.addProperty("allowBroadcast", config.allowBroadcast);
		return server;
	}

	private static JsonObject ok() {
		JsonObject object = new JsonObject();
		object.addProperty("ok", true);
		return object;
	}

	private void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
		byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(status, data.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(data);
		}
	}

	private void sendError(HttpExchange exchange, int status, String message) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("ok", false);
		body.addProperty("error", message);
		sendJson(exchange, status, body);
	}

	/**
	 * 带 ETag 的 JSON 响应：内容没变就回 304。
	 *
	 * <p>网页每 3 秒轮询一次 /api/entries，绝大多数时候内容是一样的，
	 * 用 304 省掉整包传输和前端的一次 JSON 解析。Cache-Control 用 no-cache
	 * （允许缓存，但每次都要回来确认一次）。</p>
	 */
	private void sendJsonCacheable(HttpExchange exchange, JsonObject body) throws IOException {
		byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		String etag = etagOf(data);
		exchange.getResponseHeaders().set("ETag", etag);
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
			exchange.sendResponseHeaders(304, -1);
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(200, data.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(data);
		}
	}

	private JsonObject readJson(HttpExchange exchange) {
		try (InputStream in = exchange.getRequestBody()) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int total = 0;
			int n;
			while (total < 64 * 1024 && (n = in.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				total += n;
			}
			String text = out.toString(StandardCharsets.UTF_8);
			if (text.isBlank()) return new JsonObject();
			JsonElement element = JsonParser.parseString(text);
			return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			return new JsonObject();
		}
	}

	private static String str(JsonObject object, String key) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
		try {
			return object.get(key).getAsString().trim();
		} catch (Exception e) {
			return "";
		}
	}

	private static long longValue(JsonObject object, String key) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return 0L;
		try {
			return object.get(key).getAsLong();
		} catch (Exception e) {
			return 0L;
		}
	}

	private static String queryParam(HttpExchange exchange, String key) {
		String query = exchange.getRequestURI().getRawQuery();
		if (query == null || query.isEmpty()) return "";
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq < 0) continue;
			String name = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
			if (name.equals(key)) {
				return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			}
		}
		return "";
	}

	private SessionStore.Session session(HttpExchange exchange) {
		return SiCore.sessions().get(cookie(exchange, COOKIE));
	}

	private static String cookie(HttpExchange exchange, String name) {
		List<String> headers = exchange.getRequestHeaders().get("Cookie");
		if (headers == null) return null;
		for (String header : headers) {
			for (String part : header.split(";")) {
				String trimmed = part.trim();
				if (trimmed.startsWith(name + "=")) {
					return trimmed.substring(name.length() + 1);
				}
			}
		}
		return null;
	}

	private void setCookie(HttpExchange exchange, String token) {
		long maxAge = Math.max(1, config.sessionHours) * 3600L;
		exchange.getResponseHeaders().add("Set-Cookie",
				COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge);
	}

	private static String ipOf(HttpExchange exchange) {
		String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		InetSocketAddress remote = exchange.getRemoteAddress();
		return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
	}

	private boolean allowLogin(String ip) {
		long now = System.currentTimeMillis();
		if (loginAttempts.size() > 256) {
			// 扫一遍，把 5 分钟内没再尝试过的 IP 清掉，避免被刷爆内存
			loginAttempts.entrySet().removeIf(entry -> {
				Deque<Long> deque = entry.getValue();
				synchronized (deque) {
					Long last = deque.peekLast();
					return last == null || now - last > 5 * 60_000L;
				}
			});
		}
		Deque<Long> attempts = loginAttempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
		synchronized (attempts) {
			while (!attempts.isEmpty() && now - attempts.peekFirst() > 5 * 60_000L) {
				attempts.pollFirst();
			}
			if (attempts.size() >= 12) return false;
			attempts.addLast(now);
			return true;
		}
	}
}

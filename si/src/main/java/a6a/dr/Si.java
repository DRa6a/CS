package a6a.dr;

import a6a.dr.si.SiConfig;
import a6a.dr.si.SiCore;
import a6a.dr.si.auth.AccountStore;
import a6a.dr.si.auth.PasswordSetup;
import a6a.dr.si.auth.SessionStore;
import a6a.dr.si.command.SiCommand;
import a6a.dr.si.message.ChatService;
import a6a.dr.si.music.FallbackSource;
import a6a.dr.si.music.MusicService;
import a6a.dr.si.music.NeteaseStore;
import a6a.dr.si.music.RequestLog;
import a6a.dr.si.net.SiNetworking;
import a6a.dr.si.player.OnlineRegistry;
import a6a.dr.si.player.SiJoinHandler;
import a6a.dr.si.web.WebServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Si —— 服务端点歌台。
 *
 * <p>职责：起一个内嵌 HTTP 服务给玩家登录 / 点歌，把点歌请求通过自定义数据包推给玩家的客户端（Cu）播放。</p>
 */
public class Si implements ModInitializer {
	public static final String MOD_ID = "si";
	public static final Logger LOGGER = LoggerFactory.getLogger("Si");

	@Override
	public void onInitialize() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("si");
		SiConfig config = SiConfig.load();

		SiCore.init(
				config,
				new AccountStore(dir.resolve("accounts.json")),
				new SessionStore(config.sessionHours),
				new MusicService(config.searchTimeoutSeconds),
				new RequestLog(config.historySize),
				new NeteaseStore(dir.resolve("netease.json")),
				new FallbackSource(config.fallbackEnabled, config.fallbackUrlTemplate,
						config.fallbackJsonField, config.searchTimeoutSeconds),
				new ChatService(200)
		);

		SiNetworking.register();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			SiCore.server = server;
			if (!SiCore.config().enabled) {
				LOGGER.warn("网页服务已在配置中关闭 (si.json -> enabled=false)");
				return;
			}
			try {
				WebServer web = new WebServer(SiCore.config());
				web.start();
				SiCore.web = web;
				LOGGER.info("点歌台已启动：{}", SiCore.baseUrl());
			} catch (Exception e) {
				LOGGER.error("网页服务启动失败（端口可能被占用）", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WebServer web = SiCore.web;
			if (web != null) {
				web.stop();
			}
			SiCore.web = null;
			SiCore.server = null;
			OnlineRegistry.clear();
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			try {
				SiJoinHandler.onJoin(handler.getPlayer());
			} catch (Exception e) {
				LOGGER.warn("处理玩家进入事件失败", e);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			OnlineRegistry.remove(handler.getPlayer().getUUID());
			PasswordSetup.cancel(handler.getPlayer().getUUID());
		});

		// 首次设置密码：聊天框输入，拦下这条消息不外发
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, bound) -> {
			if (!PasswordSetup.isWaiting(player.getUUID())) {
				return true;
			}
			Component reply = PasswordSetup.submit(player, message.signedContent());
			if (reply != null) {
				player.sendSystemMessage(SiNetworking.prefix().append(reply));
			}
			return false;
		});

		// 记一份聊天记录给网页显示「游戏内对话」。
		// 上面返回 false 时这条不会走到这里，所以密码不会被记进去。
		ServerMessageEvents.CHAT_MESSAGE.register((message, player, bound) -> {
			String text = message.signedContent();
			if (text == null || text.isBlank()) return;
			SiCore.chat().add(player.getName().getString(), text, ChatService.GAME);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> SiCommand.register(dispatcher));

		LOGGER.info("Si 点歌台服务端已加载");
	}
}

package a6a.dr;

import a6a.dr.si.SiConfig;
import a6a.dr.si.SiCore;
import a6a.dr.si.auth.AccountStore;
import a6a.dr.si.auth.PasswordSetup;
import a6a.dr.si.auth.SessionStore;
import a6a.dr.si.command.SiCommand;
import a6a.dr.si.message.ChatService;
import a6a.dr.si.message.EventLog;
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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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
				new ChatService(200),
				new EventLog(config.eventHistorySize, config.eventsEnabled ? config.eventTypes : List.of())
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
			SiCore.events().add(EventLog.JOIN, handler.getPlayer().getName().getString(), "加入了服务器");
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID uuid = handler.getPlayer().getUUID();
			String name = handler.getPlayer().getName().getString();
			OnlineRegistry.remove(uuid);
			PasswordSetup.cancel(uuid);
			SiCore.events().add(EventLog.QUIT, name, "离开了服务器");
			// 客户端走了就再也发不出状态回报，服务端自己把还挂着的条目收尾，
			// 不然网页会一直显示「有玩家在播放」
			int closed = SiCore.requests().listenerOffline(uuid);
			if (closed > 0) {
				LOGGER.info("{} 离开服务器，收尾 {} 条未播完的点歌记录", name, closed);
			}
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
			String name = player.getName().getString();
			SiCore.chat().add(name, text, ChatService.GAME);
			SiCore.events().add(EventLog.CHAT, name, text);
		});

		// 玩家执行命令（含 /me、/msg 这类）：记下来，方便网页做审计或高亮
		ServerMessageEvents.COMMAND_MESSAGE.register((message, source, bound) -> {
			String text = message.signedContent();
			if (text == null || text.isBlank()) return;
			ServerPlayer player = source.getPlayer();
			String name = player != null ? player.getName().getString() : source.getTextName();
			if (!text.startsWith("/")) text = "/" + text;
			SiCore.events().add(EventLog.COMMAND, name, text, player != null ? "player" : "server");
		});

		// 死亡：只关心玩家，怪物死一片没意义
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
			if (!(entity instanceof ServerPlayer player)) return;
			Entity killer = damage.getEntity();
			String text;
			if (killer != null && !killer.getUUID().equals(player.getUUID())) {
				text = "被 " + killer.getName().getString() + " 击杀";
			} else {
				text = "死亡";
			}
			SiCore.events().add(EventLog.DEATH, player.getName().getString(), text, damage.getMsgId());
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> SiCommand.register(dispatcher));

		LOGGER.info("Si 点歌台服务端已加载");
	}
}

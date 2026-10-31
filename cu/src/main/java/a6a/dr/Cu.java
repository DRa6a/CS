package a6a.dr;

import a6a.dr.cu.net.Packets;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cu implements ModInitializer {
	public static final String MOD_ID = "cu";
	public static final Logger LOGGER = LoggerFactory.getLogger("Cu");

	@Override
	public void onInitialize() {
		Packets.register();
		LOGGER.info("Cu 点歌台客户端已加载");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

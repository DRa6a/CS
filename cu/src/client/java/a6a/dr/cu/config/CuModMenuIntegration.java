package a6a.dr.cu.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu 集成：在模组列表里点「配置」打开 Cu 配置页。
 * 用 modCompileOnly 依赖，没装 ModMenu 时本类不会被加载，不会崩。
 */
public final class CuModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CuConfigScreen::new;
	}
}

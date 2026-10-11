package mrmd.psb;

import net.minecraft.client.Minecraft;
import mrmd.psb.sb.SpeedometerSettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MainClass.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MainClass.MODID, value = Dist.CLIENT)
public class ModClient {
    public ModClient(ModContainer container) {
        // 模组配置界面（Mods 界面 → 点本模组 → 配置）打开自定义的速度仪设置菜单，
        // 不再使用 NeoForge 原生配置界面（原生界面英文键名且无预览）。
        // 传入父界面，点“完成”返回 Mods 列表界面。
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parentScreen) -> new SpeedometerSettingsScreen(parentScreen));
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        MainClass.LOGGER.info("HELLO FROM CLIENT SETUP");
        MainClass.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}

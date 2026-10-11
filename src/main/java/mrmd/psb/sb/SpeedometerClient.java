package mrmd.psb.sb;

import mrmd.psb.MainClass;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 客户端初始化：把速度仪的开关按键注册进游戏的按键系统（MOD 事件总线）。
 *
 * <p>按键注册必须走 {@link RegisterKeyMappingsEvent}，否则按键不会出现在
 * “游戏设置 → 按键”列表里，也无法被 {@code KeyMapping} 正确捕获。
 */
@EventBusSubscriber(modid = MainClass.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpeedometerClient {

    private SpeedometerClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SpeedometerHud.TOGGLE_KEY);
    }
}

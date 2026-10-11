package mrmd.psb.sb;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 速度仪客户端配置（NeoForge CLIENT 配置）。
 *
 * <p>配置文件：{@code config/speedgun-client.toml}。与游戏内设置菜单双向联动：
 * 菜单改动通过 {@link #save()} 实时写回配置文件；
 * HUD 渲染与菜单显示都直接读 {@code xxx.get()}，因此外部直接编辑配置文件
 * 会在 NeoForge 配置热重载后即时生效，无需重启游戏。
 *
 * <p>纯配置定义类，可被主类（common 侧）安全引用注册。
 */
public final class SpeedometerConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> CORNER;
    public static final ModConfigSpec.ConfigValue<Integer> ACCENT_COLOR;
    public static final ModConfigSpec.ConfigValue<Integer> BACKGROUND_ALPHA;

    public static final ModConfigSpec.BooleanValue SHOW_3D;
    public static final ModConfigSpec.BooleanValue SHOW_HORIZONTAL;
    public static final ModConfigSpec.BooleanValue SHOW_VERTICAL;
    public static final ModConfigSpec.BooleanValue SHOW_STATE;
    public static final ModConfigSpec.BooleanValue SHOW_PEAK;

    public static final ModConfigSpec.BooleanValue KMPH_3D;
    public static final ModConfigSpec.BooleanValue KMPH_HORIZONTAL;
    public static final ModConfigSpec.BooleanValue KMPH_VERTICAL;
    public static final ModConfigSpec.BooleanValue KMPH_PEAK;

    /** 合法的面板位置枚举名。 */
    private static final Set<String> VALID_CORNERS = new HashSet<>(
            Arrays.asList("TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
                    "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"));

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        CORNER = b.comment("HUD 面板位置：左上/中上/右上/左下/中下/右下")
                .define("corner", "TOP_LEFT", s -> s instanceof String && VALID_CORNERS.contains(s));
        ACCENT_COLOR = b.comment("边框/标题颜色（ARGB 十进制整数；如 0xFF00E676 = 4278236790）")
                .define("accentColor", 0xFF00E676);
        BACKGROUND_ALPHA = b.comment("背景透明度（0~255，255 完全不透明，0 全透明）")
                .defineInRange("backgroundAlpha", 0xC0, 0, 255);

        SHOW_3D = b.comment("是否显示总速度行").define("show3D", true);
        SHOW_HORIZONTAL = b.comment("是否显示水平速度行").define("showHorizontal", true);
        SHOW_VERTICAL = b.comment("是否显示垂直速度行").define("showVertical", true);
        SHOW_STATE = b.comment("是否显示状态行").define("showState", true);
        SHOW_PEAK = b.comment("是否显示峰值行").define("showPeak", true);

        KMPH_3D = b.comment("总速度行是否显示 km/h 换算").define("kmph3D", true);
        KMPH_HORIZONTAL = b.comment("水平速度行是否显示 km/h 换算").define("kmphHorizontal", true);
        KMPH_VERTICAL = b.comment("垂直速度行是否显示 km/h 换算").define("kmphVertical", true);
        KMPH_PEAK = b.comment("峰值行是否显示 km/h 换算").define("kmphPeak", true);

        SPEC = b.build();
    }

    private SpeedometerConfig() {
    }

    /** 把已 set 的配置值保存到文件（设置菜单改动后调用；读取方直接 get()，无需落盘）。 */
    public static void save() {
        SPEC.save();
    }
}

package mrmd.psb.sb;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 速度仪设置界面（手持 speedgun:sbg 右键空气打开）。
 *
 * <p>可设置：面板位置（左上/中上/右上/左下/中下/右下）、各行显示开关、
 * km/h 换算开关、边框颜色、背景透明度。
 * 所有显示值直接读 {@link SpeedometerConfig}（配置热重载即时生效），
 * 改动通过 {@link SpeedometerConfig#save()} 写回配置文件，重启不丢失。
 */
public class SpeedometerSettingsScreen extends Screen {

    /** 预设边框颜色（翻译键）。 */
    public static final NamedColor[] COLORS = {
            new NamedColor("speedgun.color.green", 0xFF00E676),
            new NamedColor("speedgun.color.red", 0xFFFF5252),
            new NamedColor("speedgun.color.blue", 0xFF40C4FF),
            new NamedColor("speedgun.color.yellow", 0xFFFFD740),
            new NamedColor("speedgun.color.orange", 0xFFFF9100),
            new NamedColor("speedgun.color.purple", 0xFFB388FF),
            new NamedColor("speedgun.color.white", 0xFFFFFFFF),
            new NamedColor("speedgun.color.black", 0xFF000000),
    };

    /** 从模组配置界面打开时传入的父界面；点“完成”返回它；null 表示右键打开（关闭回游戏）。 */
    private final Screen parent;

    /** 手持 sbg 右键空气打开（无父界面）。 */
    public SpeedometerSettingsScreen() {
        this(null);
    }

    /** 从模组配置入口（Mods → 配置）打开。 */
    public SpeedometerSettingsScreen(Screen parent) {
        super(Component.translatable("speedgun.settings.title"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent); // 返回 Mods 配置列表界面
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        int left = Math.max(16, this.width / 2 - 280); // 防止小分辨率下被屏幕左缘裁掉
        int w = 200;
        int line = 24;
        int y = 36;

        // 面板位置（点击循环切换，直接读写配置）
        addRenderableWidget(Button.builder(Component.translatable("speedgun.settings.position", SpeedometerHud.corner().toString()),
                        b -> {
                            SpeedometerHud.HudCorner next = nextCorner(SpeedometerHud.corner());
                            SpeedometerConfig.CORNER.set(next.name());
                            SpeedometerConfig.save();
                            b.setMessage(Component.translatable("speedgun.settings.position", next.toString()));
                        })
                .bounds(left, y, w, 20)
                .build());
        y += line;

        // 边框颜色（点击循环切换，直接读写配置）
        addRenderableWidget(Button.builder(Component.translatable("speedgun.settings.accent_color", COLORS[currentColorIndex()].toString()),
                        b -> {
                            int i = (currentColorIndex() + 1) % COLORS.length;
                            SpeedometerConfig.ACCENT_COLOR.set(COLORS[i].color);
                            SpeedometerConfig.save();
                            b.setMessage(Component.translatable("speedgun.settings.accent_color", COLORS[i].toString()));
                        })
                .bounds(left, y, w, 20)
                .build());
        y += line;

        // 背景透明度
        addRenderableWidget(new AlphaSlider(left, y, w, 20,
                Component.translatable("speedgun.settings.background_alpha", 0),
                SpeedometerConfig.BACKGROUND_ALPHA.get() / 255.0));
        y += line + 4;

        // 各行显示开关（每行后面跟独立的 km/h 开关）
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.show_3d"), SpeedometerConfig.SHOW_3D.get(),
                v -> SpeedometerConfig.SHOW_3D.set(v), left, y));
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.kmph"), SpeedometerConfig.KMPH_3D.get(),
                v -> SpeedometerConfig.KMPH_3D.set(v), left + 125, y));
        y += line;
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.show_horizontal"), SpeedometerConfig.SHOW_HORIZONTAL.get(),
                v -> SpeedometerConfig.SHOW_HORIZONTAL.set(v), left, y));
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.kmph"), SpeedometerConfig.KMPH_HORIZONTAL.get(),
                v -> SpeedometerConfig.KMPH_HORIZONTAL.set(v), left + 125, y));
        y += line;
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.show_vertical"), SpeedometerConfig.SHOW_VERTICAL.get(),
                v -> SpeedometerConfig.SHOW_VERTICAL.set(v), left, y));
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.kmph"), SpeedometerConfig.KMPH_VERTICAL.get(),
                v -> SpeedometerConfig.KMPH_VERTICAL.set(v), left + 125, y));
        y += line;
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.show_state"), SpeedometerConfig.SHOW_STATE.get(),
                v -> SpeedometerConfig.SHOW_STATE.set(v), left, y));
        y += line;
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.show_peak"), SpeedometerConfig.SHOW_PEAK.get(),
                v -> SpeedometerConfig.SHOW_PEAK.set(v), left, y));
        addRenderableWidget(checkbox(Component.translatable("speedgun.settings.kmph"), SpeedometerConfig.KMPH_PEAK.get(),
                v -> SpeedometerConfig.KMPH_PEAK.set(v), left + 125, y));
        y += line + 4;

        // 完成按钮
        addRenderableWidget(Button.builder(Component.translatable("speedgun.settings.done"), b -> this.onClose())
                .bounds(left, y, w, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false; // 禁止鼠标滚轮滚动菜单
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawString(this.font, this.title.getString(),
                (this.width - this.font.width(this.title)) / 2.0f, 14.0f, 0xFFFFFFFF, false);
        // 右侧 HUD 实时预览（反映当前配置；右缘距屏幕右侧 20px，从右侧算起，任何分辨率都不会与左列叠加）
        SpeedometerHud.drawPreview(gui, this.width - 20, 60);
    }

    private Checkbox checkbox(Component label, boolean selected, java.util.function.Consumer<Boolean> onChange, int x, int y) {
        return Checkbox.builder(label, this.font)
                .pos(x, y)
                .selected(selected)
                .onValueChange((cb, val) -> {
                    onChange.accept(val);
                    SpeedometerConfig.save(); // 每次勾选/取消都写回配置文件
                })
                .build();
    }

    private int currentColorIndex() {
        int current = SpeedometerConfig.ACCENT_COLOR.get();
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].color == current) {
                return i;
            }
        }
        return 0;
    }

    private static SpeedometerHud.HudCorner nextCorner(SpeedometerHud.HudCorner c) {
        SpeedometerHud.HudCorner[] all = SpeedometerHud.HudCorner.values();
        return all[(c.ordinal() + 1) % all.length];
    }

    /** 带翻译键的颜色预设。 */
    public static final class NamedColor {
        public final String translationKey;
        public final int color;

        public NamedColor(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        @Override
        public String toString() {
            return Component.translatable(translationKey).getString();
        }
    }

    /** 背景透明度滑块（0%~100%）。 */
    private static final class AlphaSlider extends AbstractSliderButton {
        private AlphaSlider(int x, int y, int w, int h, Component label, double pct) {
            super(x, y, w, h, label, pct);
            // 父类构造阶段的消息可能未被按 value 刷新（导致模板里的 %s 未替换），
            // 构造完成后主动刷新一次，保证初始文本立即正确。
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("speedgun.settings.background_alpha", Math.round(this.value * 100)));
        }

        @Override
        protected void applyValue() {
            SpeedometerConfig.BACKGROUND_ALPHA.set((int) Math.round(this.value * 255));
            SpeedometerConfig.save(); // 透明度改动写回配置文件
        }
    }
}

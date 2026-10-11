package mrmd.psb.sb;

import java.util.ArrayList;
import java.util.List;

import mrmd.psb.MainClass;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import org.lwjgl.glfw.GLFW;

/**
 * 玩家速度仪 HUD（客户端）。
 *
 * <p>在游戏 HUD 渲染完成后（{@link RenderGuiEvent.Post}）绘制速度面板：
 * 总速度（m/s 与 km/h）、水平速度（m/s 与 km/h）、垂直速度、移动状态与会话峰值。
 * 标题显示当前追踪对象：手持 sbg 潜行右键实体后显示“速度仪 目标:<实体>”，未绑定时为“速度仪”。
 * 仅当主手或副手持有 speedgun:sbg 物品时显示，否则不渲染；
 * 按 H 键（可在按键设置中修改）可手动关闭/开启，重新打开时峰值清零。
 *
 * <p>纯客户端功能，单机与联机服务器都可用，不发送任何网络数据。
 */
@EventBusSubscriber(modid = MainClass.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SpeedometerHud {

    // ================= 可调参数 =================

    /** 默认是否显示 HUD。 */
    public static boolean VISIBLE_BY_DEFAULT = true;

    /** 速度平滑系数（EMA）：1.0 完全即时但数字跳动明显，0.1 平滑但反应迟钝，建议 0.3~0.5。 */
    public static double SMOOTHING_ALPHA = 0.4;

    /** 整体缩放倍率（1.0 = 原大小，2.0 = 两倍）。 */
    public static float SCALE = 1.0f;

    /** 面板距屏幕边缘的间距（逻辑像素，随 SCALE 缩放）。 */
    public static int MARGIN = 8;

    // ================= 颜色（ARGB）=================

    /** 正文文字颜色。 */
    public static int TEXT_COLOR = 0xFFFFFFFF;

    /** 次要文字颜色（峰值、垂直速度接近 0 时）。 */
    public static int DIM_COLOR = 0xFFB0BEC5;

    /** 上升（垂直速度为正）颜色。 */
    public static int UP_COLOR = 0xFF00E676;

    /** 下降（垂直速度为负）颜色。 */
    public static int DOWN_COLOR = 0xFFFF5252;

    /** 背景 RGB 颜色（不含透明度，透明度来自配置 BACKGROUND_ALPHA）。 */
    public static int BACKGROUND_COLOR = 0x00101010;

    // ================= 按键 =================

    /** 开关速度仪的按键，默认 H（游戏设置 → 按键 → 杂项 中可改）。 */
    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.speedometer.toggle",
            GLFW.GLFW_KEY_H,
            "key.categories.misc");

    private static boolean visible = VISIBLE_BY_DEFAULT;

    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final int LINE_GAP = 2;

    private SpeedometerHud() {
    }

    // 处理开关按键（游戏事件总线，客户端）
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        while (TOGGLE_KEY.consumeClick()) {
            visible = !visible;
            PlayerSpeedTracker.resetPeak();
        }
    }

    // 绘制 HUD（游戏事件总线，客户端）：仅当主手或副手持 speedgun:sbg 时显示
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!PlayerSpeedTracker.isHoldingSbg(mc.player)) {
            return;
        }
        if (!visible) {
            return;
        }
        drawHud(event.getGuiGraphics(), mc);
    }

    private static void drawHud(GuiGraphics gui, Minecraft mc) {
        Font font = mc.font;
        float scale = SCALE;

        List<HudLine> lines = buildLines();
        int accentColor = SpeedometerConfig.ACCENT_COLOR.get();
        int backgroundAlpha = SpeedometerConfig.BACKGROUND_ALPHA.get();

        // ---- 布局（逻辑像素，最后乘 SCALE）----
        int lineHeight = font.lineHeight;
        int textWidth = 0;
        for (HudLine line : lines) {
            textWidth = Math.max(textWidth, font.width(line.text));
        }
        int panelW = textWidth + PAD_X * 2;
        int panelH = lines.size() * lineHeight + (lines.size() - 1) * LINE_GAP + PAD_Y * 2;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = (int) Math.round(panelW * scale);
        int h = (int) Math.round(panelH * scale);
        HudCorner corner = corner();
        int x = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_CENTER, BOTTOM_CENTER -> (screenW - w) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> (int) Math.round(screenW - panelW * scale - MARGIN);
        };
        int y = switch (corner) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> (int) Math.round(screenH - panelH * scale - MARGIN);
        };

        // ---- 背景与边框（背景透明度来自配置 BACKGROUND_ALPHA）----
        int bgColor = (backgroundAlpha << 24) | (BACKGROUND_COLOR & 0x00FFFFFF);
        gui.fill(x, y, x + w, y + h, bgColor);
        gui.fill(x, y, x + w, y + 1, accentColor);
        gui.fill(x, y + h - 1, x + w, y + h, accentColor);
        gui.fill(x, y, x + 1, y + h, accentColor);
        gui.fill(x + w - 1, y, x + w, y + h, accentColor);

        // ---- 文本 ----
        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            float lineX = x + PAD_X * scale;
            float lineY = y + (PAD_Y + i * (lineHeight + LINE_GAP)) * scale;
            gui.drawString(font, line.text, lineX, lineY, line.color, true);
        }
    }

    /** 按当前配置构建 HUD 行（直接读 SpeedometerConfig，数值为实时追踪数据）。 */
    private static List<HudLine> buildLines() {
        boolean show3D = SpeedometerConfig.SHOW_3D.get();
        boolean kmph3D = SpeedometerConfig.KMPH_3D.get();
        boolean showHorizontal = SpeedometerConfig.SHOW_HORIZONTAL.get();
        boolean kmphHorizontal = SpeedometerConfig.KMPH_HORIZONTAL.get();
        boolean showVertical = SpeedometerConfig.SHOW_VERTICAL.get();
        boolean kmphVertical = SpeedometerConfig.KMPH_VERTICAL.get();
        boolean showState = SpeedometerConfig.SHOW_STATE.get();
        boolean showPeak = SpeedometerConfig.SHOW_PEAK.get();
        boolean kmphPeak = SpeedometerConfig.KMPH_PEAK.get();

        List<HudLine> lines = new ArrayList<>();
        // 标题：未绑定时“速度仪”，绑定后追加“ 目标:<实体>”（本地化文本）
        String title;
        Entity target = PlayerSpeedTracker.getTarget();
        if (target != null) {
            title = Component.translatable("speedgun.hud.title_target", target.getName().getString()).getString();
        } else {
            title = Component.translatable("speedgun.hud.title").getString();
        }
        lines.add(new HudLine(title, SpeedometerConfig.ACCENT_COLOR.get()));
        // 总速度（原 3D 合速度，移到最上，可单独开关 km/h 换算）
        if (show3D) {
            String total = kmph3D
                    ? Component.translatable("speedgun.hud.total_kmph",
                            PlayerSpeedTracker.getSpeed3DText(),
                            String.format(java.util.Locale.ROOT, "%.1f", PlayerSpeedTracker.getSpeed3D() * 3.6)).getString()
                    : Component.translatable("speedgun.hud.total", PlayerSpeedTracker.getSpeed3DText()).getString();
            lines.add(new HudLine(total, TEXT_COLOR));
        }
        String horizontal = kmphHorizontal
                ? Component.translatable("speedgun.hud.horizontal_kmph",
                        PlayerSpeedTracker.getHorizontalText(),
                        String.format(java.util.Locale.ROOT, "%.1f", PlayerSpeedTracker.getHorizontalSpeed() * 3.6)).getString()
                : Component.translatable("speedgun.hud.horizontal", PlayerSpeedTracker.getHorizontalText()).getString();
        if (showHorizontal) {
            lines.add(new HudLine(horizontal, TEXT_COLOR));
        }
        if (showVertical) {
            double v = PlayerSpeedTracker.getVerticalSpeed();
            int color = v > 0.2 ? UP_COLOR : (v < -0.2 ? DOWN_COLOR : DIM_COLOR);
            String vertical = kmphVertical
                    ? Component.translatable("speedgun.hud.vertical_kmph",
                            PlayerSpeedTracker.getVerticalText(),
                            String.format(java.util.Locale.ROOT, "%+.1f", v * 3.6)).getString()
                    : Component.translatable("speedgun.hud.vertical", PlayerSpeedTracker.getVerticalText()).getString();
            lines.add(new HudLine(vertical, color));
        }
        if (showState) {
            lines.add(new HudLine(Component.translatable("speedgun.hud.state", PlayerSpeedTracker.getStateLabel()).getString(), TEXT_COLOR));
        }
        if (showPeak) {
            String peak = kmphPeak
                    ? Component.translatable("speedgun.hud.peak_kmph",
                            PlayerSpeedTracker.getPeakText(),
                            String.format(java.util.Locale.ROOT, "%.1f", PlayerSpeedTracker.getPeakHorizontal() * 3.6)).getString()
                    : Component.translatable("speedgun.hud.peak", PlayerSpeedTracker.getPeakText()).getString();
            lines.add(new HudLine(peak, DIM_COLOR));
        }
        return lines;
    }

    /** 设置界面右侧预览：面板右缘对齐 {@code right}（从右侧算起），按当前配置绘制实时 HUD 面板。 */
    public static void drawPreview(GuiGraphics gui, float right, float y) {
        Font font = Minecraft.getInstance().font;
        List<HudLine> lines = buildLines();
        float scale = SCALE;

        int lineHeight = font.lineHeight;
        int textWidth = 0;
        for (HudLine line : lines) {
            textWidth = Math.max(textWidth, font.width(line.text));
        }
        int panelW = textWidth + PAD_X * 2;
        int panelH = lines.size() * lineHeight + (lines.size() - 1) * LINE_GAP + PAD_Y * 2;
        int w = (int) Math.round(panelW * scale);
        int h = (int) Math.round(panelH * scale);
        float x = right - w; // 右缘对齐：从右侧向左计算起点

        int bgColor = (SpeedometerConfig.BACKGROUND_ALPHA.get() << 24) | (BACKGROUND_COLOR & 0x00FFFFFF);
        int accentColor = SpeedometerConfig.ACCENT_COLOR.get();
        gui.fill((int) x, (int) y, (int) x + w, (int) y + h, bgColor);
        gui.fill((int) x, (int) y, (int) x + w, (int) y + 1, accentColor);
        gui.fill((int) x, (int) y + h - 1, (int) x + w, (int) y + h, accentColor);
        gui.fill((int) x, (int) y, (int) x + 1, (int) y + h, accentColor);
        gui.fill((int) x + w - 1, (int) y, (int) x + w, (int) y + h, accentColor);

        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            float lineX = x + PAD_X * scale;
            float lineY = y + (PAD_Y + i * (lineHeight + LINE_GAP)) * scale;
            gui.drawString(font, line.text, lineX, lineY, line.color, true);
        }
    }

    /** 当前配置的面板位置（配置值非法时回退左上）。 */
    public static HudCorner corner() {
        try {
            return HudCorner.valueOf(SpeedometerConfig.CORNER.get());
        } catch (IllegalArgumentException e) {
            return HudCorner.TOP_LEFT;
        }
    }

    /** 一行 HUD 文本及其颜色。 */
    private record HudLine(String text, int color) {
    }

    /** 面板停靠位置（本地化显示名）。 */
    public enum HudCorner {
        TOP_LEFT("speedgun.corner.top_left"), TOP_CENTER("speedgun.corner.top_center"),
        TOP_RIGHT("speedgun.corner.top_right"),
        BOTTOM_LEFT("speedgun.corner.bottom_left"), BOTTOM_CENTER("speedgun.corner.bottom_center"),
        BOTTOM_RIGHT("speedgun.corner.bottom_right");

        private final String translationKey;

        HudCorner(String translationKey) {
            this.translationKey = translationKey;
        }

        @Override
        public String toString() {
            return Component.translatable(translationKey).getString();
        }
    }
}

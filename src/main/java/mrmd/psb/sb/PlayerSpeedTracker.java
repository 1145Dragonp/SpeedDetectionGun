package mrmd.psb.sb;

import java.util.Locale;

import mrmd.psb.MainClass;
import mrmd.psb.item.items;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 客户端速度追踪器。
 *
 * <p>每个客户端 tick 采样追踪对象（绑定目标实体，未绑定时为玩家自己）的位置，
 * 用真实时间差（{@link System#nanoTime()}）计算瞬时速度，单位：方块/秒，等价于 m/s。
 * 瞬时值再经过指数移动平均（EMA）平滑，避免 HUD 数字剧烈抖动。
 *
 * <p>目标绑定：手持 speedgun:sbg 潜行右键实体 → 绑定该实体为追踪目标；
 * 潜行右键空气 → 清空绑定，恢复追踪玩家自己。目标被移除/死亡时自动解绑。
 *
 * <p>设置菜单触发（优先级降级）：非潜行右键空气（没有任何可交互目标时）
 * → 打开速度仪设置界面；非潜行右键实体/方块一律放行，让原版交互优先
 * （村民交易、喂食、按按钮等）。
 *
 * <p>纯客户端类，通过 {@code @EventBusSubscriber(bus = GAME, value = CLIENT)} 自动注册，
 * 服务器端不会加载执行。
 */
@EventBusSubscriber(modid = MainClass.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PlayerSpeedTracker {

    /** 是否启用追踪。HUD 关闭时也保持追踪，重新打开即可看到连续数值。 */
    public static boolean TRACKING_ENABLED = true;

    /** 峰值重置间隔（秒）：达到峰值后若该时长内没有刷新出新的峰值，峰值自动清零重新记录。 */
    public static double PEAK_RESET_SECONDS = 10.0;

    // ---- 瞬时值（最近一个 tick 的原始速度，m/s）----
    private static double instantHorizontal;
    private static double instantVertical;
    private static double instant3D;

    // ---- 平滑值（EMA 结果，m/s）----
    private static double horizontalSpeed;
    private static double verticalSpeed;
    private static double speed3D;

    /** 会话峰值（水平速度，m/s）。达到 {@link #PEAK_RESET_SECONDS} 未刷新会自动清零。 */
    private static double peakHorizontal;

    /** 最近一次刷新峰值的时间（{@link System#nanoTime()}）。 */
    private static long lastPeakNanos;

    // ---- 绑定目标 ----
    /** 潜行右键绑定的目标实体；null 表示未绑定（追踪玩家自己）。 */
    private static Entity target;

    // ---- 采样状态 ----
    private static boolean hasSample;
    private static long lastNanos;
    private static Vec3 lastPos;
    /** 当前采样对象的实体 id（玩家或绑定目标），变化时重置采样基准与峰值。 */
    private static int lastTrackedId = Integer.MIN_VALUE;

    private PlayerSpeedTracker() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!TRACKING_ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        Entity tracked = resolveTrackedEntity();
        if (tracked == null) {
            reset();
            return;
        }
        // 追踪对象变化（绑定/解绑/重生/切换世界）时重置采样基准与峰值
        int trackedId = tracked.getId();
        if (trackedId != lastTrackedId) {
            reset();
            lastTrackedId = trackedId;
        }

        long now = System.nanoTime();
        Vec3 pos = tracked.position();
        if (!hasSample) {
            lastPos = pos;
            lastNanos = now;
            hasSample = true;
            return;
        }

        double dt = (now - lastNanos) / 1_000_000_000.0;
        if (dt <= 0.0) {
            return;
        }

        Vec3 delta = pos.subtract(lastPos);
        instantHorizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z) / dt;
        instantVertical = delta.y / dt;
        instant3D = delta.length() / dt;

        double alpha = SpeedometerHud.SMOOTHING_ALPHA;
        horizontalSpeed = ema(horizontalSpeed, instantHorizontal, alpha);
        verticalSpeed = ema(verticalSpeed, instantVertical, alpha);
        speed3D = ema(speed3D, instant3D, alpha);
        updatePeak(now);

        lastPos = pos;
        lastNanos = now;
    }

    // ============ 绑定交互（手持 sbg 潜行右键）============

    /**
     * 手持 sbg 右键实体：潜行 = 绑定为追踪目标；
     * 非潜行 = 完全放行，让原版实体交互优先（如打开村民交易、喂食、骑乘），不打开设置。
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player == null || !isHoldingSbg(player)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            bindTarget(event.getTarget());
        }
    }

    /**
     * 手持 sbg 右键空气：潜行 = 清空绑定目标；
     * 非潜行 = 没有任何可交互目标，打开设置界面（优先级降级为兜底）。
     */
    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        if (player == null || !isHoldingSbg(player)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            clearTarget();
        } else {
            openSettings();
        }
    }

    /** 打开速度仪设置界面（若当前没有其他界面）。 */
    private static void openSettings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new SpeedometerSettingsScreen());
        }
    }

    /** 主手或副手是否持有 sbg 物品。 */
    public static boolean isHoldingSbg(Player player) {
        return player.getMainHandItem().is(items.SBG)
                || player.getOffhandItem().is(items.SBG);
    }

    // ============ 目标管理 ============

    /** 绑定目标实体（潜行右键实体时调用）。 */
    public static void bindTarget(Entity entity) {
        target = entity;
    }

    /** 清空绑定目标（潜行右键空气时调用）。 */
    public static void clearTarget() {
        target = null;
    }

    /** 当前绑定的目标实体；null 表示未绑定。 */
    public static Entity getTarget() {
        return target;
    }

    /** 当前实际被追踪的实体：绑定目标有效则目标，否则玩家自己。 */
    public static Entity getTrackedEntity() {
        Entity t = target;
        if (t != null && !t.isRemoved()) {
            return t;
        }
        return Minecraft.getInstance().player;
    }

    /** 解析追踪对象：目标有效则返回目标，否则自动解绑并回退玩家。 */
    private static Entity resolveTrackedEntity() {
        Entity t = target;
        if (t != null) {
            if (!t.isRemoved() && t.isAlive()) {
                return t;
            }
            target = null; // 目标被移除 / 死亡，自动解绑
        }
        return Minecraft.getInstance().player;
    }

    // ============ 内部计算 ============

    private static double ema(double prev, double sample, double alpha) {
        return prev * (1.0 - alpha) + sample * alpha;
    }

    /** 更新峰值：创新高则刷新并记录时间；否则超过 {@link #PEAK_RESET_SECONDS} 未刷新则清零重新记录。 */
    private static void updatePeak(long now) {
        if (horizontalSpeed > peakHorizontal) {
            peakHorizontal = horizontalSpeed;
            lastPeakNanos = now;
        } else if (peakHorizontal > 0.0 && PEAK_RESET_SECONDS > 0.0
                && now - lastPeakNanos >= (long) (PEAK_RESET_SECONDS * 1_000_000_000.0)) {
            peakHorizontal = 0.0;
        }
    }

    private static void reset() {
        hasSample = false;
        instantHorizontal = 0.0;
        instantVertical = 0.0;
        instant3D = 0.0;
        horizontalSpeed = 0.0;
        verticalSpeed = 0.0;
        speed3D = 0.0;
        peakHorizontal = 0.0;
        lastPeakNanos = 0L;
    }

    /** 重置会话峰值（HUD 重新打开时调用）。 */
    public static void resetPeak() {
        peakHorizontal = 0.0;
        lastPeakNanos = 0L;
    }

    // ============ 只读访问 ============

    /** 平滑后的水平速度（m/s）。 */
    public static double getHorizontalSpeed() {
        return horizontalSpeed;
    }

    /** 平滑后的垂直速度（m/s，向上为正）。 */
    public static double getVerticalSpeed() {
        return verticalSpeed;
    }

    /** 平滑后的总速度 / 3D 合速度（m/s）。 */
    public static double getSpeed3D() {
        return speed3D;
    }

    /** 当前会话峰值水平速度（m/s）。 */
    public static double getPeakHorizontal() {
        return peakHorizontal;
    }

    /** 格式化：水平速度，两位小数。 */
    public static String getHorizontalText() {
        return String.format(Locale.ROOT, "%.2f", horizontalSpeed);
    }

    /** 格式化：垂直速度，带符号，两位小数。 */
    public static String getVerticalText() {
        return String.format(Locale.ROOT, "%+.2f", verticalSpeed);
    }

    /** 格式化：总速度，两位小数。 */
    public static String getSpeed3DText() {
        return String.format(Locale.ROOT, "%.2f", speed3D);
    }

    /** 格式化：峰值，两位小数。 */
    public static String getPeakText() {
        return String.format(Locale.ROOT, "%.2f", peakHorizontal);
    }

    /** 当前移动状态的中文描述（针对当前追踪对象，优先级从高到低）。 */
    public static String getStateLabel() {
        return buildStateLabel(getTrackedEntity());
    }

    private static String buildStateLabel(Entity e) {
        if (e == null) {
            return Component.translatable("speedgun.state.none").getString();
        }
        if (e instanceof Player p) {
            if (p.isFallFlying()) {
                return Component.translatable("speedgun.state.elytra").getString();
            }
            if (p.getAbilities().flying) {
                return Component.translatable("speedgun.state.creative_flying").getString();
            }
            if (p.getVehicle() != null) {
                return Component.translatable("speedgun.state.riding", p.getVehicle().getName().getString()).getString();
            }
            if (p.isSwimming()) {
                return Component.translatable("speedgun.state.swimming").getString();
            }
            if (p.isSprinting()) {
                return Component.translatable("speedgun.state.sprinting").getString();
            }
            if (p.isShiftKeyDown()) {
                return Component.translatable("speedgun.state.sneaking").getString();
            }
            if (p.onGround()) {
                return Component.translatable("speedgun.state.walking").getString();
            }
            if (p.isInWater()) {
                return Component.translatable("speedgun.state.in_water").getString();
            }
            return Component.translatable("speedgun.state.air").getString();
        }
        if (e instanceof LivingEntity le) {
            if (le.isFallFlying()) {
                return Component.translatable("speedgun.state.elytra").getString();
            }
            if (le.isSwimming()) {
                return Component.translatable("speedgun.state.swimming").getString();
            }
            if (le.isSprinting()) {
                return Component.translatable("speedgun.state.sprinting").getString();
            }
        }
        if (e.getVehicle() != null) {
            return Component.translatable("speedgun.state.riding", e.getVehicle().getName().getString()).getString();
        }
        if (e.onGround()) {
            return Component.translatable("speedgun.state.ground").getString();
        }
        if (e.isInWater()) {
            return Component.translatable("speedgun.state.in_water").getString();
        }
        return Component.translatable("speedgun.state.air").getString();
    }
}

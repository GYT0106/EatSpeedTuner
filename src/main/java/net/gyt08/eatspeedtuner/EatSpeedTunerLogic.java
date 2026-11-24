package net.gyt08.eatspeedtuner;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * 纯计算逻辑：判断物品是否属于“进食/饮用”，以及把原版使用时长换算成倍率之后的时长。
 * 这里刻意不引用任何客户端类，服务端也能安全使用。
 */
public final class EatSpeedTunerLogic {

    /** 允许的最小倍率（0.01 = 时间变成原来的 1/100）。 */
    public static final double MIN_MULTIPLIER = 0.01D;
    /** 允许的最大倍率（100 = 时间变成原来的 100 倍）。 */
    public static final double MAX_MULTIPLIER = 100.0D;

    private static final double EPSILON = 1.0E-6D;
    /** 单个物品使用时长上限（1 小时），防止异常数值把玩家卡住。 */
    private static final int MAX_TICKS = 72000;

    private EatSpeedTunerLogic() {
    }

    /** 把任意输入夹到合法区间，NaN / Infinity 一律退回 1.0。 */
    public static double sanitize(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        if (value < MIN_MULTIPLIER) {
            return MIN_MULTIPLIER;
        }
        if (value > MAX_MULTIPLIER) {
            return MAX_MULTIPLIER;
        }
        return value;
    }

    /**
     * 是否为“吃东西 / 喝东西”。只认 EAT 与 DRINK 两种动画，
     * 这样拉弓、举盾、望远镜、蜂蜜瓶以外的其它长按物品都不会被误伤。
     */
    public static boolean isEdible(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    /**
     * 时长换算：倍率 1 = 原版，0.5 = 时间减半（更快），2 = 时间翻倍（更慢）。
     * 结果以 tick 为单位并四舍五入，最少 1 tick。
     */
    public static int scaleDuration(int originalTicks, double multiplier) {
        if (originalTicks <= 0) {
            return originalTicks;
        }
        double m = sanitize(multiplier);
        if (Math.abs(m - 1.0D) < EPSILON) {
            return originalTicks;
        }
        long ticks = Math.round(originalTicks / m);
        if (ticks < 1L) {
            ticks = 1L;
        }
        if (ticks > MAX_TICKS) {
            ticks = MAX_TICKS;
        }
        return (int) ticks;
    }

    /** tick -> 秒，方便界面显示。 */
    public static double ticksToSeconds(int ticks) {
        return ticks / 20.0D;
    }
}



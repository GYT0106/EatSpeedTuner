package net.gyt08.eatspeedtuner;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 公共配置（COMMON），文件位于 config/eatspeedtuner-common.toml。
 * <p>
 * 客户端与服务端都会加载这个配置：
 * <ul>
 *   <li>单人 / 局域网主机：这一份配置就是权威值；</li>
 *   <li>远程联机客户端：会被服务端下发的值覆盖（见 {@link EatSpeedTunerState}）。</li>
 * </ul>
 */
public final class EatSpeedTunerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue EAT_TIME_MULTIPLIER = BUILDER
            .comment(
                    "吃/喝东西所花时间的倍率（倍率 = 原时长 / 新时长）。",
                    "1.0 = 原版速度；0.5 = 时间减半（吃得更快）；2.0 = 时间翻倍（吃得更慢）。",
                    "例如原版牛排 1.6 秒（32 tick）：0.5 -> 0.8 秒；干海带 0.8 秒（16 tick）：0.5 -> 0.4 秒。",
                    "只对 EAT / DRINK 类物品生效，弓箭、盾牌、望远镜等不受影响。",
                    "联机时以主机（服务端）的值为准，并会实时同步给所有玩家。")
            .defineInRange("eat_time_multiplier", 1.0D,
                    EatSpeedTunerLogic.MIN_MULTIPLIER, EatSpeedTunerLogic.MAX_MULTIPLIER);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EatSpeedTunerConfig() {
    }

    /** 读取配置文件中的倍率；配置尚未加载时安全地退回 1.0（原版行为）。 */
    public static double getMultiplier() {
        try {
            if (SPEC.isLoaded()) {
                return EatSpeedTunerLogic.sanitize(EAT_TIME_MULTIPLIER.get());
            }
        } catch (Throwable ignored) {
            // 配置尚未加载 / 已被卸载，忽略
        }
        return 1.0D;
    }

    /** 写入并立即落盘（改动即时生效，无需重启游戏）。 */
    public static void setMultiplier(double value) {
        double clamped = EatSpeedTunerLogic.sanitize(value);
        try {
            if (SPEC.isLoaded()) {
                EAT_TIME_MULTIPLIER.set(clamped);
                SPEC.save();
            }
        } catch (Throwable t) {
            EatSpeedTunerMod.LOGGER.warn("[EatSpeedTuner] 保存配置失败", t);
        }
    }

    public static double getDefaultMultiplier() {
        return EAT_TIME_MULTIPLIER.getDefault();
    }
}



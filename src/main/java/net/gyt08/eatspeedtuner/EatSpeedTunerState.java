package net.gyt08.eatspeedtuner;

/**
 * 当前生效的倍率（客户端与服务端共用）。
 * <p>
 * 单人 / 局域网主机：{@link #MULTIPLIER} 跟随配置文件。
 * 远程联机客户端：服务端会在玩家登录时下发倍率，之后每次主机改设置都会再下发一次，
 * 因此这里永远是“主机正在用的那个值”，改动即时生效，不需要重进世界或重启游戏。
 */
public final class EatSpeedTunerState {

    private static volatile double multiplier = 1.0D;
    /** 远程联机时，服务端是否已经下发过权威值。 */
    private static volatile boolean remoteAuthoritative = false;

    private EatSpeedTunerState() {
    }

    public static double get() {
        return multiplier;
    }

    public static void set(double value) {
        multiplier = EatSpeedTunerLogic.sanitize(value);
    }

    public static boolean isRemoteAuthoritative() {
        return remoteAuthoritative;
    }

    public static void setRemoteAuthoritative(boolean value) {
        remoteAuthoritative = value;
    }

    /** 从配置同步到运行时状态（服务端 / 单人主机使用）。 */
    public static void syncFromConfig() {
        set(EatSpeedTunerConfig.getMultiplier());
        remoteAuthoritative = false;
    }

    /** 断开连接时调用：远程覆盖值作废，退回本地配置文件。 */
    public static void resetToLocal() {
        remoteAuthoritative = false;
        set(EatSpeedTunerConfig.getMultiplier());
    }
}



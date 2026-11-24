package net.gyt08.eatspeedtuner;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义进食速度倍率。
 * <p>
 * 倍率语义：倍率 = 原时长 / 新时长。
 * <ul>
 *   <li>1.0 = 原版速度</li>
 *   <li>0.5 = 时间减半（吃得更快）</li>
 *   <li>2.0 = 时间翻倍（吃得更慢）</li>
 * </ul>
 * 例：原版牛排 1.6 秒 / 32 tick，倍率 0.5 → 0.8 秒 / 16 tick；甜浆果 0.8 秒 / 16 tick → 0.4 秒 / 8 tick。
 * <p>
 * 联机时以主机（服务端）的配置为准：主机每次修改都会广播给所有在线玩家，客户端收到后立刻生效，
 * 不需要重启游戏，也不需要重新进入世界。
 */
@Mod(EatSpeedTunerMod.MOD_ID)
public final class EatSpeedTunerMod {

    public static final String MOD_ID = "eatspeedtuner";
    public static final Logger LOGGER = LoggerFactory.getLogger("EatSpeedTuner");

    public EatSpeedTunerMod(IEventBus modEventBus, ModContainer modContainer) {
        // 公共配置：config/eatspeedtuner-common.toml
        modContainer.registerConfig(ModConfig.Type.COMMON, EatSpeedTunerConfig.SPEC);

        // 运行时状态先跟配置文件对齐一次
        EatSpeedTunerState.syncFromConfig();

        // ---- 模组总线 ----
        modEventBus.addListener(EatSpeedTunerMod::onRegisterPayloads);
        modEventBus.addListener(EatSpeedTunerMod::onConfigReload);

        // ---- 游戏总线（服务端与客户端都会走） ----
        // LOWEST：最后执行，缩放的是别人已经定好的最终时长
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                EatSpeedTunerMod::onUseItemStart);
        NeoForge.EVENT_BUS.addListener(EatSpeedTunerMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EatSpeedTunerMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(EatSpeedTunerMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(EatSpeedTunerMod::onServerTick);

        // ---- 客户端专属初始化（GUI / 按键） ----
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EatSpeedTunerClient.init(modEventBus, modContainer);
        }
    }

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // 服务端 -> 客户端：当前生效的倍率
        registrar.playToClient(
                EatSpeedTunerPayloads.SyncMultiplierPayload.TYPE,
                EatSpeedTunerPayloads.SyncMultiplierPayload.STREAM_CODEC,
                EatSpeedTunerMod::handleSyncMultiplier);

        // 客户端 -> 服务端：请求重新下发一次（登录后主动拉一次，保证一定拿到权威值）
        registrar.playToServer(
                EatSpeedTunerPayloads.RequestSyncPayload.TYPE,
                EatSpeedTunerPayloads.RequestSyncPayload.STREAM_CODEC,
                EatSpeedTunerMod::handleRequestSync);

        // 客户端 -> 服务端：请求修改倍率（仅主机本人 / OP 有效）
        registrar.playToServer(
                EatSpeedTunerPayloads.RequestSetMultiplierPayload.TYPE,
                EatSpeedTunerPayloads.RequestSetMultiplierPayload.STREAM_CODEC,
                EatSpeedTunerMod::handleRequestSet);

        // 服务端 -> 客户端：修改是否被接受
        registrar.playToClient(
                EatSpeedTunerPayloads.SetResultPayload.TYPE,
                EatSpeedTunerPayloads.SetResultPayload.STREAM_CODEC,
                EatSpeedTunerMod::handleSetResult);
    }

    /** 客户端收到主机下发的倍率：立即生效（无需重启 / 重进世界）。 */
    private static void handleSyncMultiplier(EatSpeedTunerPayloads.SyncMultiplierPayload payload,
                                             net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            EatSpeedTunerState.set(payload.multiplier());
            EatSpeedTunerState.setRemoteAuthoritative(true);
        });
    }

    /** 服务端收到“请把倍率发给我”。 */
    private static void handleRequestSync(EatSpeedTunerPayloads.RequestSyncPayload payload,
                                          net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendSyncTo(player);
            }
        });
    }

    /** 服务端收到“请把倍率改成 X”。 */
    private static void handleRequestSet(EatSpeedTunerPayloads.RequestSetMultiplierPayload payload,
                                         net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean allowed = canEditSettings(player);
            if (!allowed) {
                sendResultTo(player, false);
                return;
            }
            applyMultiplier(payload.multiplier());
            sendResultTo(player, true);
        });
    }

    /** 客户端收到“修改是否被接受”。 */
    private static void handleSetResult(EatSpeedTunerPayloads.SetResultPayload payload,
                                        net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!payload.success()) {
                context.player().displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("eatspeedtuner.message.not_allowed"), true);
            }
        });
    }

    /** 是否有权修改联机设置：主机本人、单人存档主人、或 2 级权限（OP）。 */
    public static boolean canEditSettings(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        if (server.isSingleplayer()) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        return server.isSingleplayerOwner(player.getGameProfile());
    }

    public static void sendSyncTo(ServerPlayer player) {
        // 只有装了本模组的客户端才有这个频道，否则会因未知频道报错
        if (player.connection.hasChannel(EatSpeedTunerPayloads.SyncMultiplierPayload.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new EatSpeedTunerPayloads.SyncMultiplierPayload(EatSpeedTunerState.get()));
        }
    }

    private static void sendResultTo(ServerPlayer player, boolean success) {
        if (player.connection.hasChannel(EatSpeedTunerPayloads.SetResultPayload.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new EatSpeedTunerPayloads.SetResultPayload(success));
        }
    }

    /** 把倍率广播给所有在线玩家（主机改设置后立即调用）。 */
    public static void broadcastMultiplier() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        double value = EatSpeedTunerState.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(EatSpeedTunerPayloads.SyncMultiplierPayload.TYPE)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new EatSpeedTunerPayloads.SyncMultiplierPayload(value));
            }
        }
    }

    /**
     * 修改倍率：写配置 -> 立即生效 -> 广播给所有玩家。
     * 服务端与单人主机都走这里，所以“改完立刻生效、全服同步”是同一条路径。
     */
    public static void applyMultiplier(double value) {
        double clamped = EatSpeedTunerLogic.sanitize(value);
        EatSpeedTunerConfig.setMultiplier(clamped);
        EatSpeedTunerState.set(clamped);
        broadcastMultiplier();
        LOGGER.info("[EatSpeedTuner] 进食速度倍率已设为 {}（{}）", clamped,
                clamped == 1.0D ? "原版速度" : (clamped < 1.0D ? "更快" : "更慢"));
    }

    // ------------------------------------------------------------------
    // 配置热重载
    // ------------------------------------------------------------------

    private static void onConfigReload(net.neoforged.fml.event.config.ModConfigEvent event) {
        if (!(event instanceof net.neoforged.fml.event.config.ModConfigEvent.Loading)
                && !(event instanceof net.neoforged.fml.event.config.ModConfigEvent.Reloading)) {
            return;
        }
        if (event.getConfig().getType() != ModConfig.Type.COMMON) {
            return;
        }
        // 远程联机时客户端不覆盖服务端下发的值
        if (EatSpeedTunerState.isRemoteAuthoritative()) {
            return;
        }
        EatSpeedTunerState.set(EatSpeedTunerConfig.getMultiplier());
        broadcastMultiplier();
    }

    // ------------------------------------------------------------------
    // 玩法逻辑
    // ------------------------------------------------------------------

    /**
     * 关键一步：NeoForge 在 {@code LivingEntity#startUsingItem} 里调用
     * {@code EventHooks.onItemUseStart(...)}，而这个方法在客户端与服务端都会执行，
     * 所以在这里改 duration 就等于改掉玩家本次进食的实际耗时，不需要 Mixin。
     * <p>
     * <b>为什么注册在 LOWEST 优先级</b>：倍率缩放必须作用在**最终的**基础时长上。
     * 如果别的模组（例如“万物皆可吃”给任意物品临时挂 FOOD 组件）在之后才设定时长，
     * 就会把这里缩放好的值覆盖回原始值，表现为倍率失效。
     * 放到 LOWEST 保证本处理器最后跑，拿到的是别人已经定好的时长再缩放。
     */
    private static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        ItemStack stack = event.getItem();
        if (!EatSpeedTunerLogic.isEdible(stack)) {
            return;
        }
        double multiplier = EatSpeedTunerState.get();
        if (Math.abs(multiplier - 1.0D) < 1.0E-6D) {
            return;
        }
        int original = event.getDuration();
        if (original <= 0) {
            return;
        }
        event.setDuration(EatSpeedTunerLogic.scaleDuration(original, multiplier));
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EatSpeedTunerCommands.register(event.getDispatcher());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSyncTo(player);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        // 服务端（含单人主机）启动时以配置文件为准
        EatSpeedTunerState.set(EatSpeedTunerConfig.getMultiplier());
        EatSpeedTunerState.setRemoteAuthoritative(false);
    }

    /** 兜底：每秒最多广播一次，防止配置文件被外部改动后不同步。 */
    private static int tickCounter;
    private static double lastBroadcastValue = Double.NaN;

    private static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        double current = EatSpeedTunerState.get();
        if (!Double.isNaN(lastBroadcastValue) && Math.abs(current - lastBroadcastValue) > 1.0E-6D) {
            broadcastMultiplier();
        }
        lastBroadcastValue = current;
    }
}




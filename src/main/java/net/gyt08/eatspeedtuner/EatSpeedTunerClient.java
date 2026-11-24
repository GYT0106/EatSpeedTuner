package net.gyt08.eatspeedtuner;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端专属初始化。只在物理客户端加载，独立成类可以避免服务端加载客户端类。
 */
public final class EatSpeedTunerClient {

    private EatSpeedTunerClient() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        // 模组列表里的“配置”按钮 + Configured 读取同一个界面工厂
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (container, parent) -> new EatSpeedTunerConfigScreen(parent));

        modEventBus.addListener(EatSpeedTunerClient::onRegisterKeys);

        NeoForge.EVENT_BUS.addListener(EatSpeedTunerClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EatSpeedTunerClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(EatSpeedTunerClient::onLoggingOut);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(EatSpeedTunerKeys.OPEN_CONFIG);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (EatSpeedTunerKeys.OPEN_CONFIG.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new EatSpeedTunerConfigScreen(null));
            }
        }
    }

    /** 连上服务器后主动拉一次倍率，保证拿到的是主机当前的值。 */
    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new EatSpeedTunerPayloads.RequestSyncPayload());
        } catch (Throwable ignored) {
            // 服务器没装本模组时忽略
        }
    }

    /** 断开连接后恢复成本地配置文件的值。 */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EatSpeedTunerState.resetToLocal();
    }
}



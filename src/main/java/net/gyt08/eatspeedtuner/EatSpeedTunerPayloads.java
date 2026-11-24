package net.gyt08.eatspeedtuner;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组用到的所有网络包。
 * <p>
 * 服务端 -> 客户端：广播当前生效的进食速度倍率。主机每次改配置都会发一次，客户端收到立刻生效。
 */
public final class EatSpeedTunerPayloads {

    private EatSpeedTunerPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(EatSpeedTunerMod.MOD_ID, path));
    }

    /** 服务端 -> 客户端：当前生效的倍率。 */
    public record SyncMultiplierPayload(double multiplier) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncMultiplierPayload> TYPE = payloadType("sync_multiplier");

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncMultiplierPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.DOUBLE, SyncMultiplierPayload::multiplier,
                        SyncMultiplierPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 -> 服务端：请求重新下发一次倍率（玩家登录时用）。 */
    public record RequestSyncPayload() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<RequestSyncPayload> TYPE = payloadType("request_sync");

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSyncPayload> STREAM_CODEC =
                StreamCodec.unit(new RequestSyncPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 -> 服务端：请求把倍率改成某个值（输入框提交时使用，仅主机/OP 有效）。 */
    public record RequestSetMultiplierPayload(double multiplier) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<RequestSetMultiplierPayload> TYPE =
                payloadType("request_set_multiplier");

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSetMultiplierPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.DOUBLE, RequestSetMultiplierPayload::multiplier,
                        RequestSetMultiplierPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 服务端 -> 客户端：修改是否被接受，用来提示玩家。 */
    public record SetResultPayload(boolean success) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SetResultPayload> TYPE = payloadType("set_result");

        public static final StreamCodec<RegistryFriendlyByteBuf, SetResultPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SetResultPayload::success,
                        SetResultPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}




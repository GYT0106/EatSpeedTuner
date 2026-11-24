package net.gyt08.eatspeedtuner;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 服务端 / 单人可用的命令：/eatspeedtuner [倍率]
 * <p>
 * 不带参数 = 查询当前倍率；带参数 = 修改并立即广播给所有玩家。
 * 需要 2 级权限（单人存档或主机默认拥有）。
 */
public final class EatSpeedTunerCommands {

    private EatSpeedTunerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eatspeedtuner")
                .requires(source -> source.hasPermission(2))
                .executes(EatSpeedTunerCommands::query)
                .then(Commands.argument("multiplier",
                                DoubleArgumentType.doubleArg(EatSpeedTunerLogic.MIN_MULTIPLIER,
                                        EatSpeedTunerLogic.MAX_MULTIPLIER))
                        .executes(EatSpeedTunerCommands::set)));
    }

    private static int query(CommandContext<CommandSourceStack> ctx) {
        double value = EatSpeedTunerState.get();
        ctx.getSource().sendSuccess(() -> Component.translatable("eatspeedtuner.command.query", fmt(value)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        double value = DoubleArgumentType.getDouble(ctx, "multiplier");
        EatSpeedTunerMod.applyMultiplier(value);
        double applied = EatSpeedTunerState.get();
        ctx.getSource().sendSuccess(() -> Component.translatable("eatspeedtuner.command.set", fmt(applied)), true);
        return 1;
    }

    private static String fmt(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}



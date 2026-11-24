package net.gyt08.eatspeedtuner;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * 按键：默认未绑定，玩家可自行在“选项 -> 控制 -> 按键绑定”里设置。
 * 游戏内按一下即可打开进食速度设置界面。
 */
public final class EatSpeedTunerKeys {

    private EatSpeedTunerKeys() {
    }

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.eatspeedtuner.open_config",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            "key.categories.eatspeedtuner");
}



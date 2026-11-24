package net.gyt08.eatspeedtuner;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 进食速度设置界面。
 * <p>
 * 既能从“模组列表 -> 配置”打开，也能被 Configured 读取，还能用按键在游戏内直接打开。
 * 输入框里填倍率即可：1 = 原版，0.5 = 时间减半，2 = 时间翻倍。
 * <p>
 * 单人 / 局域网主机：直接写配置并广播给所有玩家；
 * 远程联机：把请求发给主机，主机校验权限后统一修改并广播（改完立刻生效）。
 */
public class EatSpeedTunerConfigScreen extends Screen {

    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_GRAY = 0xA0A0A0;
    private static final int COLOR_OK = 0x55FF55;
    private static final int COLOR_WARN = 0xFFAA00;
    private static final int COLOR_ERROR = 0xFF5555;

    private final Screen parent;

    private EditBox input;
    private Button applyButton;

    private String statusText = "";
    private int statusColor = COLOR_GRAY;

    public EatSpeedTunerConfigScreen(Screen parent) {
        super(Component.translatable("eatspeedtuner.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 70;

        this.input = new EditBox(this.font, centerX - 60, top, 120, 20,
                Component.translatable("eatspeedtuner.screen.input"));
        this.input.setMaxLength(12);
        this.input.setValue(format(EatSpeedTunerState.get()));
        this.input.setResponder(text -> updateStatus());
        this.addRenderableWidget(this.input);

        this.applyButton = Button.builder(Component.translatable("eatspeedtuner.screen.apply"), b -> apply())
                .bounds(centerX - 60, top + 26, 120, 20)
                .build();
        this.addRenderableWidget(this.applyButton);

        this.addRenderableWidget(Button.builder(Component.translatable("eatspeedtuner.screen.reset"), b -> {
            this.input.setValue("1");
            apply();
        }).bounds(centerX - 100, top + 52, 96, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("eatspeedtuner.screen.refresh"), b -> {
            this.input.setValue(format(EatSpeedTunerState.get()));
            setStatus(Component.translatable("eatspeedtuner.screen.refreshed"), COLOR_GRAY);
        }).bounds(centerX + 4, top + 52, 96, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(centerX - 60, top + 96, 120, 20)
                .build());

        updateStatus();
    }

    private void updateStatus() {
        Double parsed = parse(this.input == null ? "" : this.input.getValue());
        if (parsed == null) {
            setStatus(Component.translatable("eatspeedtuner.screen.invalid"), COLOR_ERROR);
            if (this.applyButton != null) {
                this.applyButton.active = false;
            }
            return;
        }
        if (this.applyButton != null) {
            this.applyButton.active = true;
        }
        double m = EatSpeedTunerLogic.sanitize(parsed);
        setStatus(Component.translatable("eatspeedtuner.screen.preview", format(m),
                format(EatSpeedTunerLogic.ticksToSeconds(EatSpeedTunerLogic.scaleDuration(32, m))),
                format(EatSpeedTunerLogic.ticksToSeconds(EatSpeedTunerLogic.scaleDuration(16, m)))), COLOR_OK);
    }

    private void apply() {
        Double parsed = parse(this.input.getValue());
        if (parsed == null) {
            setStatus(Component.translatable("eatspeedtuner.screen.invalid"), COLOR_ERROR);
            return;
        }
        double value = EatSpeedTunerLogic.sanitize(parsed);
        this.input.setValue(format(value));

        boolean inMultiplayer = this.minecraft != null && this.minecraft.getConnection() != null
                && !this.minecraft.hasSingleplayerServer();

        if (inMultiplayer) {
            // 交给主机处理，主机校验权限后会广播给所有人
            PacketDistributor.sendToServer(new EatSpeedTunerPayloads.RequestSetMultiplierPayload(value));
            setStatus(Component.translatable("eatspeedtuner.screen.sent"), COLOR_WARN);
        } else {
            EatSpeedTunerMod.applyMultiplier(value);
            setStatus(Component.translatable("eatspeedtuner.screen.applied", format(EatSpeedTunerState.get())), COLOR_OK);
        }
    }

    private void setStatus(Component text, int color) {
        this.statusText = text.getString();
        this.statusColor = color;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景由 Screen#render 负责，这里只画文字
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int top = this.height / 2 - 70;

        graphics.drawCenteredString(this.font, this.title, centerX, top - 44, COLOR_WHITE);
        graphics.drawCenteredString(this.font,
                Component.translatable("eatspeedtuner.screen.current", format(EatSpeedTunerState.get()),
                        EatSpeedTunerState.isRemoteAuthoritative()
                                ? Component.translatable("eatspeedtuner.screen.source_remote")
                                : Component.translatable("eatspeedtuner.screen.source_local")),
                centerX, top - 28, COLOR_GRAY);

        graphics.drawString(this.font, Component.translatable("eatspeedtuner.screen.hint"), centerX - 150,
                top + 78, COLOR_GRAY, false);

        if (!this.statusText.isEmpty()) {
            graphics.drawCenteredString(this.font, this.statusText, centerX, top + 80, this.statusColor);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.input != null && this.input.isFocused()) {
                apply();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ------------------------------------------------------------------

    private static Double parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || value <= 0.0D) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return String.valueOf((long) Math.rint(value));
        }
        String text = String.format(java.util.Locale.ROOT, "%.3f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}



package com.thecguyyyy.staywithme.client;

import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.network.LlmConfigUpdatePacket;
import com.thecguyyyy.staywithme.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StayWithMeLlmConfigScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 360;
    private static final int MAX_PANEL_WIDTH = 620;
    private static final int LABEL_WIDTH = 92;

    private final Screen parent;
    private boolean enabled;
    private boolean usePlayerEngine;
    private boolean useSmartBrain;
    private boolean useBaritone;
    private EditBox baseUrlBox;
    private EditBox apiKeyBox;
    private EditBox modelBox;
    private EditBox timeoutBox;
    private EditBox cooldownBox;
    private Button enabledButton;
    private Button playerEngineButton;
    private Button smartBrainButton;
    private Button baritoneButton;
    private Component status = Component.literal("");

    public StayWithMeLlmConfigScreen(Screen parent) {
        super(Component.literal("StayWithMe API Config"));
        this.parent = parent;
        this.enabled = StayWithMeConfig.LLM_ENABLED.get();
        this.usePlayerEngine = StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get();
        this.useSmartBrain = StayWithMeConfig.USE_SMARTBRAINLIB_BEHAVIORS.get();
        this.useBaritone = StayWithMeConfig.USE_BARITONE_WHEN_AVAILABLE.get();
    }

    @Override
    protected void init() {
        int panelWidth = this.panelWidth();
        int left = this.panelLeft(panelWidth);
        int fieldX = left + LABEL_WIDTH;
        int fieldWidth = panelWidth - LABEL_WIDTH;
        int y = 42;

        this.enabledButton = this.addRenderableWidget(Button.builder(enabledLabel(), button -> {
            this.enabled = !this.enabled;
            button.setMessage(enabledLabel());
        }).bounds(left, y, 116, 20).build());

        int presetX = left + 124;
        int presetWidth = Math.max(70, (panelWidth - 124 - 12) / 3);
        int presetGap = 6;
        this.addRenderableWidget(Button.builder(Component.literal("OpenAI"), button -> {
            this.baseUrlBox.setValue("https://api.openai.com/v1");
            this.modelBox.setValue("gpt-4o-mini");
        }).bounds(presetX, y, presetWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("DeepSeek"), button -> {
            this.baseUrlBox.setValue("https://api.deepseek.com");
            this.modelBox.setValue("deepseek-chat");
        }).bounds(presetX + presetWidth + presetGap, y, presetWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Ollama"), button -> {
            this.baseUrlBox.setValue("http://localhost:11434/v1");
            this.modelBox.setValue("llama3.1");
            this.apiKeyBox.setValue("ollama");
        }).bounds(presetX + (presetWidth + presetGap) * 2, y, presetWidth, 20).build());

        y += 38;
        this.baseUrlBox = editBox(fieldX, y, fieldWidth, "Base URL", StayWithMeConfig.LLM_BASE_URL.get(), 2048);
        y += 30;
        this.apiKeyBox = editBox(fieldX, y, fieldWidth, "API key", "", 4096);
        this.apiKeyBox.setHint(Component.literal("empty keeps current; CLEAR removes"));
        y += 30;
        this.modelBox = editBox(fieldX, y, fieldWidth, "Model", StayWithMeConfig.LLM_MODEL.get(), 512);
        y += 30;
        int numberWidth = Math.min(100, Math.max(70, (fieldWidth - 92) / 2));
        this.timeoutBox = editBox(fieldX, y, numberWidth, "Timeout", String.valueOf(StayWithMeConfig.LLM_TIMEOUT_SECONDS.get()), 6);
        this.cooldownBox = editBox(fieldX + numberWidth + 92, y, numberWidth, "Cooldown", String.valueOf(StayWithMeConfig.LLM_COOLDOWN_SECONDS.get()), 6);

        y += 38;
        int toggleWidth = (panelWidth - 20) / 2;
        this.playerEngineButton = this.addRenderableWidget(Button.builder(playerEngineLabel(), button -> {
            this.usePlayerEngine = !this.usePlayerEngine;
            button.setMessage(playerEngineLabel());
        }).bounds(left, y, toggleWidth, 20).build());
        this.smartBrainButton = this.addRenderableWidget(Button.builder(smartBrainLabel(), button -> {
            this.useSmartBrain = !this.useSmartBrain;
            button.setMessage(smartBrainLabel());
        }).bounds(left + toggleWidth + 20, y, toggleWidth, 20).build());

        y += 26;
        this.baritoneButton = this.addRenderableWidget(Button.builder(baritoneLabel(), button -> {
            this.useBaritone = !this.useBaritone;
            button.setMessage(baritoneLabel());
        }).bounds(left, y, toggleWidth, 20).build());

        y += 34;
        this.addRenderableWidget(Button.builder(Component.literal("Save to Server"), button -> save()).bounds(left, y, toggleWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(left + toggleWidth + 20, y, toggleWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelWidth = this.panelWidth();
        int left = this.panelLeft(panelWidth);
        int cooldownLabelX = left + LABEL_WIDTH + Math.min(100, Math.max(70, (panelWidth - LABEL_WIDTH - 92) / 2)) + 22;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawString(this.font, "Provider presets", left, 31, 0xA0A0A0);
        graphics.drawString(this.font, "Base URL", left, 85, 0xA0A0A0);
        graphics.drawString(this.font, "API Key", left, 115, 0xA0A0A0);
        graphics.drawString(this.font, "Model", left, 145, 0xA0A0A0);
        graphics.drawString(this.font, "Timeout", left, 175, 0xA0A0A0);
        graphics.drawString(this.font, "Cooldown", cooldownLabelX, 175, 0xA0A0A0);
        graphics.drawString(this.font, "O key or /staywithmeconfig opens this screen.", left, this.height - 36, 0x808080);
        graphics.drawString(this.font, "API key is not shown. Leave empty to keep current value.", left, this.height - 24, 0x808080);
        graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 52, 0xE0E0E0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private EditBox editBox(int x, int y, int width, String label, String value, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        this.addRenderableWidget(box);
        return box;
    }

    private int panelWidth() {
        return Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, this.width - 40));
    }

    private int panelLeft(int panelWidth) {
        return (this.width - panelWidth) / 2;
    }

    private void save() {
        String apiKeyInput = this.apiKeyBox.getValue().trim();
        boolean updateApiKey = !apiKeyInput.isBlank();
        String apiKey = "CLEAR".equalsIgnoreCase(apiKeyInput) ? "" : apiKeyInput;

        ModNetworking.CHANNEL.sendToServer(new LlmConfigUpdatePacket(
                this.enabled,
                this.baseUrlBox.getValue(),
                updateApiKey,
                apiKey,
                this.modelBox.getValue(),
                parseInt(this.timeoutBox.getValue(), StayWithMeConfig.LLM_TIMEOUT_SECONDS.get()),
                parseInt(this.cooldownBox.getValue(), StayWithMeConfig.LLM_COOLDOWN_SECONDS.get()),
                this.usePlayerEngine,
                this.useSmartBrain,
                this.useBaritone
        ));
        this.status = Component.literal("Saved request sent to server.");
    }

    private Component enabledLabel() {
        return Component.literal("LLM: " + (this.enabled ? "ON" : "OFF"));
    }

    private Component playerEngineLabel() {
        return Component.literal("PlayerEngine: " + (this.usePlayerEngine ? "ON" : "OFF"));
    }

    private Component smartBrainLabel() {
        return Component.literal("SmartBrainLib: " + (this.useSmartBrain ? "ON" : "OFF"));
    }

    private Component baritoneLabel() {
        return Component.literal("Baritone: " + (this.useBaritone ? "ON" : "OFF"));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

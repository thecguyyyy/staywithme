package com.thecguyyyy.staywithme.client;

import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.network.LlmConfigRequestPacket;
import com.thecguyyyy.staywithme.network.LlmConfigSnapshotPacket;
import com.thecguyyyy.staywithme.network.LlmConfigUpdatePacket;
import com.thecguyyyy.staywithme.network.LlmConnectionTestPacket;
import com.thecguyyyy.staywithme.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class StayWithMeLlmConfigScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 360;
    private static final int MAX_PANEL_WIDTH = 680;
    private static final int LABEL_WIDTH = 92;

    private final Screen parent;
    private boolean enabled;
    private boolean usePlayerEngine;
    private boolean useSmartBrain;
    private boolean useBaritone;
    private boolean serverSnapshotLoaded;
    private boolean serverAllowed;
    private boolean apiKeyConfigured;
    private boolean clearApiKeyRequested;
    private boolean revealApiKey;
    private boolean testing;
    private boolean formDirty;
    private boolean applyingServerSnapshot;
    private boolean savePending;
    private boolean advancedView;
    private EditBox baseUrlBox;
    private EditBox apiKeyBox;
    private EditBox modelBox;
    private EditBox timeoutBox;
    private EditBox cooldownBox;
    private Button enabledButton;
    private Button apiTabButton;
    private Button advancedTabButton;
    private Button openAiPresetButton;
    private Button deepSeekPresetButton;
    private Button ollamaPresetButton;
    private Button apiKeyVisibilityButton;
    private Button clearApiKeyButton;
    private Button playerEngineButton;
    private Button smartBrainButton;
    private Button baritoneButton;
    private Button saveButton;
    private Button testButton;
    private Component status = Component.literal("Loading server configuration...");

    public StayWithMeLlmConfigScreen(Screen parent) {
        super(Component.literal("StayWithMe API Manager"));
        this.parent = parent;
        this.enabled = StayWithMeConfig.LLM_ENABLED.get();
        this.apiKeyConfigured = !StayWithMeConfig.LLM_API_KEY.get().isBlank();
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
        int tabWidth = (panelWidth - 8) / 2;
        this.apiTabButton = this.addRenderableWidget(Button.builder(Component.literal("API"), button -> {
            this.advancedView = false;
            this.applyViewVisibility();
        }).bounds(left, 36, tabWidth, 20).build());
        this.advancedTabButton = this.addRenderableWidget(Button.builder(Component.literal("Advanced"), button -> {
            this.advancedView = true;
            this.applyViewVisibility();
        }).bounds(left + tabWidth + 8, 36, tabWidth, 20).build());

        int y = 62;

        this.enabledButton = this.addRenderableWidget(Button.builder(this.enabledLabel(), button -> {
            this.enabled = !this.enabled;
            this.markFormDirty();
            button.setMessage(this.enabledLabel());
            this.updateButtonStates();
        }).bounds(left, y, 116, 20).build());

        int presetX = left + 124;
        int presetWidth = Math.max(74, (panelWidth - 124 - 12) / 3);
        int presetGap = 6;
        this.openAiPresetButton = this.addRenderableWidget(Button.builder(Component.literal("OpenAI"), button -> {
            this.baseUrlBox.setValue("https://api.openai.com/v1");
            this.modelBox.setValue("gpt-4o-mini");
        }).bounds(presetX, y, presetWidth, 20).build());
        this.deepSeekPresetButton = this.addRenderableWidget(Button.builder(Component.literal("DeepSeek"), button -> {
            this.baseUrlBox.setValue("https://api.deepseek.com");
            this.modelBox.setValue("deepseek-chat");
        }).bounds(presetX + presetWidth + presetGap, y, presetWidth, 20).build());
        this.ollamaPresetButton = this.addRenderableWidget(Button.builder(Component.literal("Ollama"), button -> {
            this.baseUrlBox.setValue("http://localhost:11434/v1");
            this.modelBox.setValue("llama3.1");
            this.apiKeyBox.setValue("ollama");
        }).bounds(presetX + (presetWidth + presetGap) * 2, y, presetWidth, 20).build());

        y += 26;
        this.baseUrlBox = this.editBox(fieldX, y, fieldWidth, "Base URL", StayWithMeConfig.LLM_BASE_URL.get(), 2048);
        this.baseUrlBox.setResponder(value -> this.markFormDirty());
        y += 24;
        int keyButtonWidth = 54;
        int keyGap = 6;
        int apiKeyWidth = fieldWidth - keyButtonWidth * 2 - keyGap * 2;
        this.apiKeyBox = this.editBox(fieldX, y, apiKeyWidth, "API key", "", 4096);
        this.apiKeyBox.setHint(Component.literal("enter a new key to replace the stored key"));
        this.apiKeyBox.setResponder(value -> {
            if (!value.isBlank()) {
                this.clearApiKeyRequested = false;
            }
            this.markFormDirty();
            this.updateApiKeyWidgets();
            this.updateButtonStates();
        });
        this.apiKeyVisibilityButton = this.addRenderableWidget(Button.builder(this.apiKeyVisibilityLabel(), button -> {
            this.revealApiKey = !this.revealApiKey;
            this.updateApiKeyFormatter();
            button.setMessage(this.apiKeyVisibilityLabel());
        }).bounds(fieldX + apiKeyWidth + keyGap, y, keyButtonWidth, 20).build());
        this.clearApiKeyButton = this.addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
            this.clearApiKeyRequested = true;
            this.apiKeyBox.setValue("");
            this.markFormDirty();
            this.status = Component.literal("API key will be removed when you save.");
            this.updateApiKeyWidgets();
            this.updateButtonStates();
        }).bounds(fieldX + apiKeyWidth + keyGap + keyButtonWidth + keyGap, y, keyButtonWidth, 20).build());
        this.updateApiKeyFormatter();

        y += 24;
        this.modelBox = this.editBox(fieldX, y, fieldWidth, "Model", StayWithMeConfig.LLM_MODEL.get(), 512);
        this.modelBox.setResponder(value -> this.markFormDirty());
        y += 24;
        int numberWidth = Math.min(100, Math.max(70, (fieldWidth - 92) / 2));
        this.timeoutBox = this.editBox(fieldX, y, numberWidth, "Timeout", String.valueOf(StayWithMeConfig.LLM_TIMEOUT_SECONDS.get()), 6);
        this.timeoutBox.setResponder(value -> this.markFormDirty());
        this.cooldownBox = this.editBox(fieldX + numberWidth + 92, y, numberWidth, "Cooldown", String.valueOf(StayWithMeConfig.LLM_COOLDOWN_SECONDS.get()), 6);
        this.cooldownBox.setResponder(value -> this.markFormDirty());

        y = 76;
        int toggleWidth = (panelWidth - 20) / 2;
        this.playerEngineButton = this.addRenderableWidget(Button.builder(this.playerEngineLabel(), button -> {
            this.usePlayerEngine = !this.usePlayerEngine;
            this.markFormDirty();
            button.setMessage(this.playerEngineLabel());
        }).bounds(left, y, toggleWidth, 20).build());
        this.smartBrainButton = this.addRenderableWidget(Button.builder(this.smartBrainLabel(), button -> {
            this.useSmartBrain = !this.useSmartBrain;
            this.markFormDirty();
            button.setMessage(this.smartBrainLabel());
        }).bounds(left + toggleWidth + 20, y, toggleWidth, 20).build());

        y += 26;
        this.baritoneButton = this.addRenderableWidget(Button.builder(this.baritoneLabel(), button -> {
            this.useBaritone = !this.useBaritone;
            this.markFormDirty();
            button.setMessage(this.baritoneLabel());
        }).bounds(left, y, toggleWidth, 20).build());

        y = 188;
        int actionGap = 8;
        int actionWidth = (panelWidth - actionGap * 2) / 3;
        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
                .bounds(left, y, actionWidth, 20)
                .build());
        this.testButton = this.addRenderableWidget(Button.builder(Component.literal("Test Connection"), button -> this.testConnection())
                .bounds(left + actionWidth + actionGap, y, actionWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(left + (actionWidth + actionGap) * 2, y, actionWidth, 20)
                .build());

        this.updateApiKeyWidgets();
        this.updateButtonStates();
        this.applyViewVisibility();
        this.requestServerSnapshot();
    }

    @Override
    public void tick() {
        this.baseUrlBox.tick();
        this.apiKeyBox.tick();
        this.modelBox.tick();
        this.timeoutBox.tick();
        this.cooldownBox.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelWidth = this.panelWidth();
        int left = this.panelLeft(panelWidth);
        int cooldownLabelX = left + LABEL_WIDTH + Math.min(100, Math.max(70, (panelWidth - LABEL_WIDTH - 92) / 2)) + 22;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                "Shared by chat planning, mining strategy, ore advice, and stuck-route construction.",
                this.width / 2,
                25,
                0xA0A0A0);
        if (this.advancedView) {
            graphics.drawString(this.font, "Experimental integration switches", left, 64, 0xA0A0A0);
            graphics.drawString(this.font, "PlayerEngine bridge is guarded; Forge-native movement remains active.", left, 132, 0x808080);
            graphics.drawString(this.font, "SmartBrainLib and direct Baritone switches are reserved for later executors.", left, 146, 0x808080);
            graphics.drawString(this.font, "Changes apply to newly spawned or reloaded companions.", left, 160, 0x808080);
        } else {
            graphics.drawString(this.font, this.apiKeyStatusText(), left, 54, this.apiKeyConfigured ? 0x80D080 : 0xD0A060);
            graphics.drawString(this.font, "Provider", left, 67, 0xA0A0A0);
            graphics.drawString(this.font, "Base URL", left, 93, 0xA0A0A0);
            graphics.drawString(this.font, "API Key", left, 117, 0xA0A0A0);
            graphics.drawString(this.font, "Model", left, 141, 0xA0A0A0);
            graphics.drawString(this.font, "Timeout", left, 165, 0xA0A0A0);
            graphics.drawString(this.font, "Cooldown", cooldownLabelX, 165, 0xA0A0A0);
        }
        graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 30, this.testing ? 0xE0C060 : 0xE0E0E0);
        graphics.drawString(this.font, "Open with O or /staywithmeconfig. Stored keys never return to clients.", left, this.height - 16, 0x808080);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    public void applyServerSnapshot(LlmConfigSnapshotPacket packet) {
        this.serverSnapshotLoaded = true;
        this.serverAllowed = packet.allowed();
        this.testing = packet.testing();
        this.apiKeyConfigured = packet.apiKeyConfigured();
        boolean saveAcknowledged = packet.saveAcknowledged();
        if (packet.refreshValues() && (!this.formDirty || saveAcknowledged)) {
            this.applyingServerSnapshot = true;
            try {
                this.enabled = packet.enabled();
                this.usePlayerEngine = packet.usePlayerEngineController();
                this.useSmartBrain = packet.useSmartBrainLibBehaviors();
                this.useBaritone = packet.useBaritoneWhenAvailable();
                this.baseUrlBox.setValue(packet.baseUrl());
                this.modelBox.setValue(packet.model());
                this.timeoutBox.setValue(String.valueOf(packet.timeoutSeconds()));
                this.cooldownBox.setValue(String.valueOf(packet.cooldownSeconds()));
                if (saveAcknowledged) {
                    this.apiKeyBox.setValue("");
                    this.clearApiKeyRequested = false;
                }
                this.updateToggleLabels();
            } finally {
                this.applyingServerSnapshot = false;
            }
        }
        if (saveAcknowledged) {
            this.savePending = false;
            this.formDirty = false;
        } else if (!packet.allowed()) {
            this.savePending = false;
        }
        if (!this.savePending || saveAcknowledged) {
            this.status = Component.literal(packet.status());
        }
        this.updateApiKeyWidgets();
        this.updateButtonStates();
    }

    private EditBox editBox(int x, int y, int width, String label, String value, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        this.addRenderableWidget(box);
        return box;
    }

    private void requestServerSnapshot() {
        this.serverSnapshotLoaded = false;
        this.status = Component.literal("Loading server configuration...");
        this.updateButtonStates();
        ModNetworking.CHANNEL.sendToServer(new LlmConfigRequestPacket());
    }

    private void save() {
        String baseUrl = this.baseUrlBox.getValue().trim();
        String model = this.modelBox.getValue().trim();
        if (baseUrl.isBlank() || model.isBlank()) {
            this.status = Component.literal("Base URL and model are required.");
            return;
        }

        String apiKeyInput = this.apiKeyBox.getValue().trim();
        boolean updateApiKey = this.clearApiKeyRequested || !apiKeyInput.isBlank();
        String apiKey = this.clearApiKeyRequested ? "" : apiKeyInput;
        this.savePending = true;
        this.status = Component.literal("Saving server configuration...");
        this.updateApiKeyWidgets();
        this.updateButtonStates();
        ModNetworking.CHANNEL.sendToServer(new LlmConfigUpdatePacket(
                this.enabled,
                baseUrl,
                updateApiKey,
                apiKey,
                model,
                parseInt(this.timeoutBox.getValue(), StayWithMeConfig.LLM_TIMEOUT_SECONDS.get()),
                parseInt(this.cooldownBox.getValue(), StayWithMeConfig.LLM_COOLDOWN_SECONDS.get()),
                this.usePlayerEngine,
                this.useSmartBrain,
                this.useBaritone
        ));
    }

    private void testConnection() {
        this.testing = true;
        this.status = Component.literal("Testing API connection...");
        this.updateButtonStates();
        ModNetworking.CHANNEL.sendToServer(new LlmConnectionTestPacket());
    }

    private void updateApiKeyFormatter() {
        if (this.apiKeyBox == null) {
            return;
        }
        this.apiKeyBox.setFormatter((value, index) -> FormattedCharSequence.forward(
                this.revealApiKey ? value : "*".repeat(value.length()),
                Style.EMPTY
        ));
    }

    private void updateApiKeyWidgets() {
        if (this.apiKeyVisibilityButton != null) {
            this.apiKeyVisibilityButton.setMessage(this.apiKeyVisibilityLabel());
            this.apiKeyVisibilityButton.active = !this.apiKeyBox.getValue().isBlank();
        }
        if (this.clearApiKeyButton != null) {
            this.clearApiKeyButton.active = this.serverSnapshotLoaded
                    && this.serverAllowed
                    && (this.apiKeyConfigured || !this.apiKeyBox.getValue().isBlank())
                    && !this.testing
                    && !this.savePending;
        }
    }

    private void updateButtonStates() {
        boolean editable = this.serverSnapshotLoaded && this.serverAllowed && !this.testing && !this.savePending;
        if (this.enabledButton != null) {
            this.enabledButton.active = editable;
        }
        if (this.playerEngineButton != null) {
            this.playerEngineButton.active = editable;
        }
        if (this.smartBrainButton != null) {
            this.smartBrainButton.active = editable;
        }
        if (this.baritoneButton != null) {
            this.baritoneButton.active = editable;
        }
        if (this.saveButton != null) {
            this.saveButton.active = editable;
        }
        if (this.testButton != null) {
            this.testButton.active = editable && this.apiKeyConfigured;
        }
        if (this.openAiPresetButton != null) {
            this.openAiPresetButton.active = editable;
            this.deepSeekPresetButton.active = editable;
            this.ollamaPresetButton.active = editable;
        }
        if (this.baseUrlBox != null) {
            boolean apiEditable = editable && !this.advancedView;
            this.baseUrlBox.setEditable(apiEditable);
            this.apiKeyBox.setEditable(apiEditable);
            this.modelBox.setEditable(apiEditable);
            this.timeoutBox.setEditable(apiEditable);
            this.cooldownBox.setEditable(apiEditable);
        }
        this.updateApiKeyWidgets();
    }

    private void applyViewVisibility() {
        boolean api = !this.advancedView;
        this.enabledButton.visible = api;
        this.openAiPresetButton.visible = api;
        this.deepSeekPresetButton.visible = api;
        this.ollamaPresetButton.visible = api;
        this.baseUrlBox.visible = api;
        this.apiKeyBox.visible = api;
        this.apiKeyVisibilityButton.visible = api;
        this.clearApiKeyButton.visible = api;
        this.modelBox.visible = api;
        this.timeoutBox.visible = api;
        this.cooldownBox.visible = api;
        this.testButton.visible = api;
        this.playerEngineButton.visible = this.advancedView;
        this.smartBrainButton.visible = this.advancedView;
        this.baritoneButton.visible = this.advancedView;
        this.apiTabButton.active = this.advancedView;
        this.advancedTabButton.active = !this.advancedView;
        this.updateButtonStates();
    }

    private void updateToggleLabels() {
        this.enabledButton.setMessage(this.enabledLabel());
        this.playerEngineButton.setMessage(this.playerEngineLabel());
        this.smartBrainButton.setMessage(this.smartBrainLabel());
        this.baritoneButton.setMessage(this.baritoneLabel());
    }

    private int panelWidth() {
        return Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, this.width - 40));
    }

    private int panelLeft(int panelWidth) {
        return (this.width - panelWidth) / 2;
    }

    private Component enabledLabel() {
        return Component.literal("LLM: " + (this.enabled ? "ON" : "OFF"));
    }

    private Component apiKeyVisibilityLabel() {
        return Component.literal(this.revealApiKey ? "Hide" : "Show");
    }

    private Component apiKeyStatusText() {
        if (!this.serverSnapshotLoaded) {
            return Component.literal("Server status: loading...");
        }
        if (!this.serverAllowed) {
            return Component.literal("Server status: read-only; operator permission required");
        }
        if (this.clearApiKeyRequested) {
            return Component.literal("API key: clear requested; save to apply");
        }
        if (!this.apiKeyBox.getValue().isBlank()) {
            return Component.literal("API key: replacement entered; save to apply");
        }
        return Component.literal("API key: " + (this.apiKeyConfigured ? "configured on server" : "not configured"));
    }

    private void markFormDirty() {
        if (!this.applyingServerSnapshot) {
            this.formDirty = true;
        }
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

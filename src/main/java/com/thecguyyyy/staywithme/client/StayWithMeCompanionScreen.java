package com.thecguyyyy.staywithme.client;

import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import com.thecguyyyy.staywithme.network.CompanionCharacterActionPacket;
import com.thecguyyyy.staywithme.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class StayWithMeCompanionScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 560;

    private final Screen parent;
    private List<CompanionCharacterProfile> characters = List.of();
    private int selectedIndex = -1;
    private boolean loading;
    private boolean requested;
    private Component status = Component.literal("Loading PlayerEngine characters...");
    private Button summonButton;
    private Button despawnButton;

    public StayWithMeCompanionScreen(Screen parent) {
        super(Component.literal("Companions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int panelWidth = this.panelWidth();
        int left = (this.width - panelWidth) / 2;
        int bottomY = this.height - 34;
        int actionWidth = Math.max(82, (panelWidth - 24) / 4);

        this.summonButton = this.addRenderableWidget(Button.builder(Component.literal("Summon"), button -> this.summonSelected())
                .bounds(left, bottomY, actionWidth, 20)
                .build());
        this.despawnButton = this.addRenderableWidget(Button.builder(Component.literal("Despawn"), button -> this.despawnSelected())
                .bounds(left + actionWidth + 8, bottomY, actionWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Reload"), button -> this.reloadCharacters())
                .bounds(left + (actionWidth + 8) * 2, bottomY, actionWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(left + (actionWidth + 8) * 3, bottomY, actionWidth, 20)
                .build());

        this.addCharacterButtons(left, panelWidth);
        this.updateButtonStates();
        if (!this.requested) {
            this.reloadCharacters();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelWidth = this.panelWidth();
        int left = (this.width - panelWidth) / 2;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawString(this.font, this.status, left, 36, this.loading ? 0xA0A0A0 : 0xD0D0D0);

        CompanionCharacterProfile selected = this.selectedProfile();
        if (selected != null) {
            int detailX = left + panelWidth / 2 + 12;
            int detailWidth = panelWidth / 2 - 12;
            graphics.drawString(this.font, selected.displayName(), detailX, 66, 0xFFFFFF);
            int y = 84;
            String description = selected.description().isBlank() ? selected.name() : selected.description();
            for (FormattedCharSequence line : this.font.split(Component.literal(description), detailWidth)) {
                graphics.drawString(this.font, line, detailX, y, 0xA0A0A0);
                y += this.font.lineHeight + 2;
                if (y > this.height - 54) {
                    break;
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private void reloadCharacters() {
        this.requested = true;
        this.loading = true;
        this.status = Component.literal("Loading PlayerEngine characters...");
        this.characters = List.of();
        this.selectedIndex = -1;
        this.rebuild();

        if (!IntegrationStatus.isPlayerEngineLoaded()) {
            this.loading = false;
            this.status = Component.literal("PlayerEngine is not loaded.");
            this.rebuild();
            return;
        }

        PlayerEngineCharacterClient.loadCharacters(Minecraft.getInstance().player)
                .whenCompleteAsync((profiles, error) -> {
                    this.loading = false;
                    if (error != null) {
                        this.characters = List.of();
                        this.selectedIndex = -1;
                        this.status = Component.literal("Failed to load PlayerEngine characters.");
                    } else {
                        this.characters = profiles == null ? List.of() : new ArrayList<>(profiles);
                        this.selectedIndex = this.characters.isEmpty() ? -1 : 0;
                        this.status = this.characters.isEmpty()
                                ? Component.literal("No PlayerEngine characters available.")
                                : Component.literal("Loaded " + this.characters.size() + " character(s).");
                    }
                    this.rebuild();
                }, Minecraft.getInstance());
    }

    private void rebuild() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            this.init(minecraft, this.width, this.height);
        }
    }

    private void addCharacterButtons(int left, int panelWidth) {
        if (this.loading || this.characters.isEmpty()) {
            return;
        }
        int listWidth = panelWidth / 2 - 12;
        int y = 62;
        int maxRows = Math.max(1, (this.height - 112) / 24);
        int rows = Math.min(maxRows, this.characters.size());
        for (int i = 0; i < rows; i++) {
            int index = i;
            CompanionCharacterProfile profile = this.characters.get(index);
            Component label = Component.literal((index == this.selectedIndex ? "> " : "") + profile.displayName());
            this.addRenderableWidget(Button.builder(label, button -> {
                        this.selectedIndex = index;
                        this.rebuild();
                    })
                    .bounds(left, y, listWidth, 20)
                    .build());
            y += 24;
        }
    }

    private void summonSelected() {
        CompanionCharacterProfile selected = this.selectedProfile();
        if (selected == null) {
            this.status = Component.literal("Select a character first.");
            return;
        }
        ModNetworking.CHANNEL.sendToServer(CompanionCharacterActionPacket.summon(selected));
        this.status = Component.literal("Summon request sent: " + selected.displayName());
    }

    private void despawnSelected() {
        CompanionCharacterProfile selected = this.selectedProfile();
        ModNetworking.CHANNEL.sendToServer(CompanionCharacterActionPacket.despawn(
                selected == null ? CompanionCharacterProfile.empty() : selected
        ));
        this.status = Component.literal("Despawn request sent.");
    }

    private CompanionCharacterProfile selectedProfile() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.characters.size()) {
            return null;
        }
        return this.characters.get(this.selectedIndex);
    }

    private void updateButtonStates() {
        boolean hasSelection = this.selectedProfile() != null;
        this.summonButton.active = hasSelection && !this.loading;
        this.despawnButton.active = !this.loading;
    }

    private int panelWidth() {
        return Math.min(PANEL_MAX_WIDTH, Math.max(300, this.width - 40));
    }
}

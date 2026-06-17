package com.thecguyyyy.staywithme.client;

import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import com.thecguyyyy.staywithme.network.CompanionCharacterActionPacket;
import com.thecguyyyy.staywithme.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

        this.addCharacterCards(left, panelWidth);
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
            int headSize = Math.min(64, Math.max(40, detailWidth / 3));
            int headY = 64;
            ResourceLocation skinTexture = CompanionSkinTextures.textureFor(null, selected.skinUrl());
            CompanionSkinTextures.renderHead(graphics, detailX, headY, headSize, skinTexture);

            int titleX = detailX + headSize + 12;
            int titleWidth = Math.max(80, detailWidth - headSize - 12);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(selected.displayName(), titleWidth), titleX, 66, 0xFFFFFF);

            int y = headY + headSize + 12;
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

    private void addCharacterCards(int left, int panelWidth) {
        if (this.loading || this.characters.isEmpty()) {
            return;
        }
        int listWidth = panelWidth / 2 - 12;
        int gap = 8;
        int columns = listWidth >= 220 ? 2 : 1;
        int cardWidth = (listWidth - gap * (columns - 1)) / columns;
        int cardHeight = 96;
        int startY = 62;
        int maxCards = Math.max(columns, ((this.height - 112) / (cardHeight + gap)) * columns);
        int rows = Math.min(maxCards, this.characters.size());
        for (int i = 0; i < rows; i++) {
            int index = i;
            CompanionCharacterProfile profile = this.characters.get(index);
            int column = i % columns;
            int row = i / columns;
            int cardX = left + column * (cardWidth + gap);
            int cardY = startY + row * (cardHeight + gap);
            this.addRenderableWidget(new CharacterCardWidget(cardX, cardY, cardWidth, cardHeight, profile, index == this.selectedIndex, () -> {
                        this.selectedIndex = index;
                        this.rebuild();
                    }));
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

    private final class CharacterCardWidget extends AbstractWidget {
        private final CompanionCharacterProfile profile;
        private final boolean selected;
        private final Runnable onClick;

        private CharacterCardWidget(int x, int y, int width, int height, CompanionCharacterProfile profile, boolean selected, Runnable onClick) {
            super(x, y, width, height, Component.literal(profile.displayName()));
            this.profile = profile;
            this.selected = selected;
            this.onClick = onClick;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = this.selected ? 0xFF26315C : (this.isHoveredOrFocused() ? 0xFF20264F : 0xFF171B38);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, background);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 24, 0x24FFFFFF);
            if (this.selected) {
                graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF7EA6FF);
            }

            String label = StayWithMeCompanionScreen.this.font.plainSubstrByWidth(this.profile.displayName(), this.width - 12);
            graphics.drawCenteredString(StayWithMeCompanionScreen.this.font, label, this.getX() + this.width / 2, this.getY() + 8, 0xFFFFFF);

            int headSize = Math.min(52, Math.max(32, Math.min(this.width - 20, this.height - 38)));
            int headX = this.getX() + (this.width - headSize) / 2;
            int headY = this.getY() + 32;
            ResourceLocation skinTexture = CompanionSkinTextures.textureFor(null, this.profile.skinUrl());
            CompanionSkinTextures.renderHead(graphics, headX, headY, headSize, skinTexture);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (this.active && this.visible) {
                this.onClick.run();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}

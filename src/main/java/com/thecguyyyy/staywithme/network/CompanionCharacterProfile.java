package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.memory.FriendMemory;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record CompanionCharacterProfile(
        String id,
        String name,
        String shortName,
        String greetingInfo,
        String description,
        String skinUrl,
        List<String> voiceIds
) {
    private static final int MAX_VOICE_IDS = 8;

    public CompanionCharacterProfile {
        id = clean(id, 64);
        name = clean(name, 64);
        shortName = clean(shortName, 32);
        greetingInfo = clean(greetingInfo, 512);
        description = clean(description, 2048);
        skinUrl = clean(skinUrl, 2048);
        voiceIds = cleanVoiceIds(voiceIds);
    }

    public static void encode(CompanionCharacterProfile profile, FriendlyByteBuf buffer) {
        CompanionCharacterProfile safe = profile == null ? empty() : profile;
        buffer.writeUtf(safe.id(), 64);
        buffer.writeUtf(safe.name(), 64);
        buffer.writeUtf(safe.shortName(), 32);
        buffer.writeUtf(safe.greetingInfo(), 512);
        buffer.writeUtf(safe.description(), 2048);
        buffer.writeUtf(safe.skinUrl(), 2048);
        buffer.writeVarInt(safe.voiceIds().size());
        for (String voiceId : safe.voiceIds()) {
            buffer.writeUtf(voiceId, 128);
        }
    }

    public static CompanionCharacterProfile decode(FriendlyByteBuf buffer) {
        String id = buffer.readUtf(64);
        String name = buffer.readUtf(64);
        String shortName = buffer.readUtf(32);
        String greetingInfo = buffer.readUtf(512);
        String description = buffer.readUtf(2048);
        String skinUrl = buffer.readUtf(2048);
        int declaredCount = Math.min(64, Math.max(0, buffer.readVarInt()));
        List<String> voiceIds = new ArrayList<>();
        for (int i = 0; i < declaredCount; i++) {
            String voiceId = buffer.readUtf(128);
            if (voiceIds.size() < MAX_VOICE_IDS) {
                voiceIds.add(voiceId);
            }
        }
        return new CompanionCharacterProfile(id, name, shortName, greetingInfo, description, skinUrl, voiceIds);
    }

    public static CompanionCharacterProfile empty() {
        return new CompanionCharacterProfile("", "Companion", "Companion", "", "", "", List.of());
    }

    public String displayName() {
        if (!this.shortName.isBlank()) {
            return this.shortName;
        }
        if (!this.name.isBlank()) {
            return this.name;
        }
        return "Companion";
    }

    public void applyTo(FriendMemory memory) {
        if (memory == null) {
            return;
        }
        memory.companionId = this.id.isBlank() ? memory.companionId : this.id;
        memory.companionName = this.name.isBlank() ? this.displayName() : this.name;
        memory.companionShortName = this.shortName;
        memory.companionGreetingInfo = this.greetingInfo;
        memory.companionDescription = this.description;
        memory.companionSkinUrl = this.skinUrl;
        memory.companionVoiceIds = new ArrayList<>(this.voiceIds);
        memory.touch();
    }

    private static String clean(String value, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.length() <= maxLength) {
            return result;
        }
        return result.substring(0, maxLength);
    }

    private static List<String> cleanVoiceIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String clean = clean(value, 128);
            if (!clean.isBlank()) {
                result.add(clean);
            }
            if (result.size() >= MAX_VOICE_IDS) {
                break;
            }
        }
        return List.copyOf(result);
    }
}

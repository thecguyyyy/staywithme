package com.thecguyyyy.staywithme.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class FriendMemory {
    public int schemaVersion = 7;
    public String playerUuid;
    public String playerName;
    public String companionId;
    public String companionName;
    public String companionShortName;
    public String companionGreetingInfo;
    public String companionDescription;
    public String companionSkinUrl;
    public List<String> companionVoiceIds = new ArrayList<>();
    public Map<String, String> companionEntityUuids = new LinkedHashMap<>();
    public long createdAtEpochMillis;
    public long updatedAtEpochMillis;
    public List<String> recentConversationSummaries = new ArrayList<>();
    public List<String> recentTaskHistory = new ArrayList<>();
    public Map<String, String> playerPreferences = new LinkedHashMap<>();
    public Map<String, ResourceKnowledge> learnedResources = new LinkedHashMap<>();
    public Map<String, ExpeditionMemory> expeditions = new LinkedHashMap<>();
    public List<String> portableNotes = new ArrayList<>();

    public static FriendMemory create(String playerUuid, String playerName) {
        FriendMemory memory = new FriendMemory();
        memory.playerUuid = playerUuid;
        memory.playerName = playerName;
        memory.companionId = UUID.randomUUID().toString();
        memory.companionName = "Companion";
        memory.createdAtEpochMillis = System.currentTimeMillis();
        memory.updatedAtEpochMillis = memory.createdAtEpochMillis;
        return memory;
    }

    public void normalize(String fallbackPlayerUuid, String fallbackPlayerName) {
        this.schemaVersion = Math.max(7, this.schemaVersion);
        if (this.playerUuid == null || this.playerUuid.isBlank()) {
            this.playerUuid = fallbackPlayerUuid;
        }
        if (this.playerName == null || this.playerName.isBlank()) {
            this.playerName = fallbackPlayerName;
        }
        if (this.companionId == null || this.companionId.isBlank()) {
            this.companionId = UUID.randomUUID().toString();
        }
        if (this.companionName == null || this.companionName.isBlank()) {
            this.companionName = "Companion";
        }
        if (this.companionShortName == null) {
            this.companionShortName = "";
        }
        if (this.companionGreetingInfo == null) {
            this.companionGreetingInfo = "";
        }
        if (this.companionDescription == null) {
            this.companionDescription = "";
        }
        if (this.companionSkinUrl == null) {
            this.companionSkinUrl = "";
        }
        if (this.companionVoiceIds == null) {
            this.companionVoiceIds = new ArrayList<>();
        }
        if (this.companionEntityUuids == null) {
            this.companionEntityUuids = new LinkedHashMap<>();
        }
        if (this.createdAtEpochMillis <= 0L) {
            this.createdAtEpochMillis = System.currentTimeMillis();
        }
        if (this.updatedAtEpochMillis <= 0L) {
            this.updatedAtEpochMillis = this.createdAtEpochMillis;
        }
        if (this.recentConversationSummaries == null) {
            this.recentConversationSummaries = new ArrayList<>();
        }
        if (this.recentTaskHistory == null) {
            this.recentTaskHistory = new ArrayList<>();
        }
        if (this.playerPreferences == null) {
            this.playerPreferences = new LinkedHashMap<>();
        }
        if (this.learnedResources == null) {
            this.learnedResources = new LinkedHashMap<>();
        }
        this.learnedResources.values().forEach(ResourceKnowledge::normalize);
        if (this.expeditions == null) {
            this.expeditions = new LinkedHashMap<>();
        }
        Map<String, ExpeditionMemory> normalizedExpeditions = new LinkedHashMap<>();
        for (ExpeditionMemory expedition : this.expeditions.values()) {
            if (expedition == null) {
                continue;
            }
            expedition.normalize(null, null);
            normalizedExpeditions.put(expedition.key, expedition);
        }
        this.expeditions = normalizedExpeditions;
        if (this.portableNotes == null) {
            this.portableNotes = new ArrayList<>();
        }
    }

    public void addConversation(String summary) {
        addBounded(this.recentConversationSummaries, summary, 20);
        this.touch();
    }

    public void addTask(String summary) {
        addBounded(this.recentTaskHistory, summary, 30);
        this.touch();
    }

    public void addPortableNote(String note) {
        addBounded(this.portableNotes, note, 40);
        this.touch();
    }

    public void rememberResource(String resourceId, String hint, String source) {
        if (resourceId == null || resourceId.isBlank()) {
            return;
        }
        String key = resourceId.trim();
        ResourceKnowledge existing = this.learnedResources.get(key);
        if (existing == null) {
            this.learnedResources.put(key, ResourceKnowledge.create(key, hint, source));
        } else {
            existing.reinforce(hint, source);
        }
        this.touch();
    }

    public void updateExpedition(String resourceId, String dimension, Consumer<ExpeditionMemory> updater) {
        if (resourceId == null || resourceId.isBlank() || updater == null) {
            return;
        }
        String key = ExpeditionMemory.keyFor(resourceId, dimension);
        ExpeditionMemory expedition = this.expeditions.get(key);
        if (expedition == null) {
            expedition = ExpeditionMemory.create(resourceId, dimension);
            this.expeditions.put(key, expedition);
        }
        updater.accept(expedition);
        expedition.normalize(resourceId, dimension);
        expedition.touch();
        this.expeditions.put(expedition.key, expedition);
        while (this.expeditions.size() > 12) {
            String firstKey = this.expeditions.keySet().iterator().next();
            this.expeditions.remove(firstKey);
        }
        this.touch();
    }

    public Optional<ExpeditionMemory> findExpedition(String resourceId, String dimension) {
        if (resourceId == null || resourceId.isBlank()) {
            return Optional.empty();
        }
        String key = ExpeditionMemory.keyFor(resourceId, dimension);
        ExpeditionMemory expedition = this.expeditions.get(key);
        if (expedition == null) {
            return Optional.empty();
        }
        expedition.normalize(resourceId, dimension);
        return Optional.of(expedition);
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("Player: ").append(this.playerName).append(" (").append(this.playerUuid).append(")\n");
        builder.append("Companion: ").append(this.companionName == null ? "Companion" : this.companionName);
        builder.append(" id=").append(this.companionId == null ? "unknown" : this.companionId).append("\n");
        builder.append("Recent conversations: ");
        builder.append(this.recentConversationSummaries.isEmpty() ? "none" : String.join(" | ", this.recentConversationSummaries));
        builder.append("\nRecent tasks: ");
        builder.append(this.recentTaskHistory.isEmpty() ? "none" : String.join(" | ", this.recentTaskHistory));
        builder.append("\nPreferences: ");
        builder.append(this.playerPreferences.isEmpty() ? "none" : this.playerPreferences.toString());
        builder.append("\nLearned resources: ");
        if (this.learnedResources.isEmpty()) {
            builder.append("none");
        } else {
            builder.append(String.join(" | ", this.learnedResources.values().stream()
                    .limit(8)
                    .map(ResourceKnowledge::summary)
                    .toList()));
        }
        builder.append("\nExpeditions: ");
        if (this.expeditions.isEmpty()) {
            builder.append("none");
        } else {
            builder.append(String.join(" | ", this.expeditions.values().stream()
                    .limit(5)
                    .map(ExpeditionMemory::summary)
                    .toList()));
        }
        builder.append("\nPortable notes: ");
        builder.append(this.portableNotes.isEmpty() ? "none" : String.join(" | ", this.portableNotes));
        return builder.toString();
    }

    public void touch() {
        this.updatedAtEpochMillis = System.currentTimeMillis();
    }

    private static void addBounded(List<String> values, String value, int maxSize) {
        if (value == null || value.isBlank()) {
            return;
        }
        values.add(value);
        while (values.size() > maxSize) {
            values.remove(0);
        }
    }
}

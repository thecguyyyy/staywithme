package com.thecguyyyy.staywithme.ai;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.UUID;

public class FriendTask {
    private final FriendTaskType type;
    private final UUID playerUuid;
    private final String playerName;
    private final String target;
    private final int amount;
    private final String message;
    private final String reason;

    public FriendTask(FriendTaskType type, UUID playerUuid, String playerName, String target, int amount, String message, String reason) {
        this.type = type;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.target = target;
        this.amount = Math.max(0, amount);
        this.message = message;
        this.reason = reason == null || reason.isBlank() ? "No reason provided." : reason;
    }

    public static FriendTask follow(UUID playerUuid, String playerName, String reason) {
        return new FriendTask(FriendTaskType.FOLLOW_PLAYER, playerUuid, playerName, null, 0, null, reason);
    }

    public static FriendTask stop(UUID playerUuid, String playerName, String reason) {
        return new FriendTask(FriendTaskType.STOP, playerUuid, playerName, null, 0, null, reason);
    }

    public static FriendTask say(UUID playerUuid, String playerName, String message, String reason) {
        return new FriendTask(FriendTaskType.SAY, playerUuid, playerName, null, 0, message, reason);
    }

    public static FriendTask unknown(UUID playerUuid, String playerName, String message, String reason) {
        return new FriendTask(FriendTaskType.UNKNOWN, playerUuid, playerName, null, 0, message, reason);
    }

    public FriendTaskType type() {
        return this.type;
    }

    public UUID playerUuid() {
        return this.playerUuid;
    }

    public String playerName() {
        return this.playerName;
    }

    public String target() {
        return this.target;
    }

    public int amount() {
        return this.amount;
    }

    public String message() {
        return this.message;
    }

    public String reason() {
        return this.reason;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", this.type.name());
        if (this.playerUuid != null) {
            tag.putUUID("PlayerUuid", this.playerUuid);
        }
        if (this.playerName != null && !this.playerName.isBlank()) {
            tag.putString("PlayerName", this.playerName);
        }
        if (this.target != null && !this.target.isBlank()) {
            tag.putString("Target", this.target);
        }
        tag.putInt("Amount", this.amount);
        if (this.message != null && !this.message.isBlank()) {
            tag.putString("Message", this.message);
        }
        tag.putString("Reason", this.reason);
        return tag;
    }

    public static Optional<FriendTask> load(CompoundTag tag) {
        if (tag == null || !tag.contains("Type")) {
            return Optional.empty();
        }
        FriendTaskType type;
        try {
            type = FriendTaskType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
        UUID playerUuid = tag.hasUUID("PlayerUuid") ? tag.getUUID("PlayerUuid") : null;
        String playerName = tag.contains("PlayerName") ? tag.getString("PlayerName") : null;
        String target = tag.contains("Target") ? tag.getString("Target") : null;
        int amount = tag.contains("Amount") ? tag.getInt("Amount") : 0;
        String message = tag.contains("Message") ? tag.getString("Message") : null;
        String reason = tag.contains("Reason") ? tag.getString("Reason") : "Recovered from entity save.";
        return Optional.of(new FriendTask(type, playerUuid, playerName, target, amount, message, reason));
    }

    public String summary() {
        StringBuilder builder = new StringBuilder(this.type.name());
        if (this.target != null && !this.target.isBlank()) {
            builder.append(" target=").append(this.target);
        }
        if (this.amount > 0) {
            builder.append(" amount=").append(this.amount);
        }
        if (this.message != null && !this.message.isBlank()) {
            builder.append(" message=\"").append(this.message).append('"');
        }
        builder.append(" reason=").append(this.reason);
        return builder.toString();
    }
}

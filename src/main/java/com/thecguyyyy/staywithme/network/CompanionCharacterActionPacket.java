package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.entity.CompanionLifecycle;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class CompanionCharacterActionPacket {
    private static final int SEARCH_RADIUS = 128;

    private final Action action;
    private final CompanionCharacterProfile profile;

    public CompanionCharacterActionPacket(Action action, CompanionCharacterProfile profile) {
        this.action = action == null ? Action.SUMMON : action;
        this.profile = profile == null ? CompanionCharacterProfile.empty() : profile;
    }

    public static CompanionCharacterActionPacket summon(CompanionCharacterProfile profile) {
        return new CompanionCharacterActionPacket(Action.SUMMON, profile);
    }

    public static CompanionCharacterActionPacket despawn(CompanionCharacterProfile profile) {
        return new CompanionCharacterActionPacket(Action.DESPAWN, profile);
    }

    public static void encode(CompanionCharacterActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        CompanionCharacterProfile.encode(packet.profile, buffer);
    }

    public static CompanionCharacterActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        CompanionCharacterProfile profile = CompanionCharacterProfile.decode(buffer);
        return new CompanionCharacterActionPacket(action, profile);
    }

    public static void handle(CompanionCharacterActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            if (packet.action == Action.DESPAWN) {
                if (CompanionLifecycle.dismissNearestOwnedCompanion(player, SEARCH_RADIUS)) {
                    player.sendSystemMessage(Component.translatable("commands.staywithme.dismissed"));
                } else {
                    player.sendSystemMessage(Component.translatable("commands.staywithme.no_friend"));
                }
                return;
            }

            FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
            packet.profile.applyTo(memory);
            JsonMemoryStore.save(memory);

            Optional<FriendEntity> companion = CompanionLifecycle.replaceCompanionFor(player, true);
            if (companion.isPresent()) {
                player.sendSystemMessage(Component.literal("Companion selected: " + packet.profile.displayName()));
            } else {
                player.sendSystemMessage(Component.literal("Could not create companion entity."));
            }
        });
        context.setPacketHandled(true);
    }

    public enum Action {
        SUMMON,
        DESPAWN
    }
}

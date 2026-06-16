package com.thecguyyyy.staywithme.entity;

import com.thecguyyyy.staywithme.ai.FriendState;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CompanionLifecycle {
    private static final int EXISTING_COMPANION_SEARCH_RADIUS = 128;
    private static final ConcurrentMap<UUID, UUID> SESSION_COMPANIONS = new ConcurrentHashMap<>();

    private CompanionLifecycle() {
    }

    public static Optional<FriendEntity> ensureCompanionFor(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }

        Optional<FriendEntity> sessionCompanion = resolveSessionCompanion(player);
        if (sessionCompanion.isPresent()) {
            applyCompanionName(sessionCompanion.get(), player);
            moveNearPlayer(sessionCompanion.get(), player);
            return sessionCompanion;
        }

        Optional<FriendEntity> ownedCompanion = findNearestOwnedCompanion(player, EXISTING_COMPANION_SEARCH_RADIUS);
        if (ownedCompanion.isPresent()) {
            applyCompanionName(ownedCompanion.get(), player);
            moveNearPlayer(ownedCompanion.get(), player);
            return ownedCompanion;
        }

        return Optional.ofNullable(spawnCompanionFor(player, true));
    }

    public static FriendEntity spawnCompanionFor(ServerPlayer player, boolean sessionManaged) {
        if (player == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        FriendEntity friend = ModEntities.FRIEND.get().create(level);
        if (friend == null) {
            return null;
        }

        moveNearPlayer(friend, player);
        applyCompanionName(friend, player);
        friend.setOwner(player);
        friend.setFriendState(FriendState.IDLE);
        level.addFreshEntity(friend);
        if (sessionManaged) {
            SESSION_COMPANIONS.put(player.getUUID(), friend.getUUID());
        }
        return friend;
    }

    public static Optional<FriendEntity> replaceCompanionFor(ServerPlayer player, boolean sessionManaged) {
        if (player == null) {
            return Optional.empty();
        }
        dismissNearestOwnedCompanion(player, EXISTING_COMPANION_SEARCH_RADIUS);
        return Optional.ofNullable(spawnCompanionFor(player, sessionManaged));
    }

    public static boolean dismissSessionCompanion(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        UUID companionId = SESSION_COMPANIONS.remove(player.getUUID());
        if (companionId == null) {
            return false;
        }
        return dismissCompanion(player.getServer(), companionId);
    }

    public static boolean dismissNearestOwnedCompanion(ServerPlayer player, int radius) {
        Optional<FriendEntity> companion = findNearestOwnedCompanion(player, radius);
        if (companion.isEmpty()) {
            return false;
        }
        FriendEntity friend = companion.get();
        SESSION_COMPANIONS.values().removeIf(uuid -> uuid.equals(friend.getUUID()));
        friend.stopTask();
        friend.discard();
        return true;
    }

    public static Optional<FriendEntity> findNearestOwnedCompanion(ServerPlayer player, int radius) {
        if (player == null) {
            return Optional.empty();
        }
        return player.serverLevel()
                .getEntitiesOfClass(
                        FriendEntity.class,
                        player.getBoundingBox().inflate(Math.max(1, radius)),
                        friend -> friend.isAlive() && friend.isOwnedBy(player)
                )
                .stream()
                .min(Comparator.comparingDouble(friend -> friend.distanceToSqr(player)));
    }

    private static Optional<FriendEntity> resolveSessionCompanion(ServerPlayer player) {
        UUID companionId = SESSION_COMPANIONS.get(player.getUUID());
        if (companionId == null) {
            return Optional.empty();
        }
        Entity entity = player.serverLevel().getEntity(companionId);
        if (entity instanceof FriendEntity friend && friend.isAlive() && friend.isOwnedBy(player)) {
            return Optional.of(friend);
        }
        SESSION_COMPANIONS.remove(player.getUUID(), companionId);
        return Optional.empty();
    }

    private static boolean dismissCompanion(MinecraftServer server, UUID companionId) {
        if (server == null || companionId == null) {
            return false;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(companionId);
            if (entity instanceof FriendEntity friend) {
                friend.stopTask();
                friend.discard();
                return true;
            }
        }
        return false;
    }

    private static void moveNearPlayer(FriendEntity friend, ServerPlayer player) {
        Vec3 spawnPos = player.position().add(player.getLookAngle().normalize().scale(2.0D));
        friend.moveTo(spawnPos.x, player.getY(), spawnPos.z, player.getYRot(), 0.0F);
    }

    private static void applyCompanionName(FriendEntity friend, ServerPlayer player) {
        friend.setCustomName(Component.literal(companionDisplayName(player)));
    }

    private static String companionDisplayName(ServerPlayer player) {
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        String name = memory.companionName == null ? "" : memory.companionName.trim();
        if (name.isBlank()) {
            return "Companion";
        }
        return name.length() > 32 ? name.substring(0, 32) : name;
    }
}

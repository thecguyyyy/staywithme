package com.thecguyyyy.staywithme.entity;

import com.thecguyyyy.staywithme.ai.FriendState;
import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CompanionLifecycle {
    private static final int EXISTING_COMPANION_SEARCH_RADIUS = 128;
    private static final ConcurrentMap<UUID, ConcurrentMap<String, UUID>> SESSION_COMPANIONS = new ConcurrentHashMap<>();

    private CompanionLifecycle() {
    }

    public static Optional<FriendEntity> ensureCompanionFor(ServerPlayer player) {
        return ensureCompanionFor(player, profileFromMemory(player), true);
    }

    public static Optional<FriendEntity> ensureCompanionFor(
            ServerPlayer player,
            CompanionCharacterProfile profile,
            boolean sessionManaged
    ) {
        if (player == null) {
            return Optional.empty();
        }
        CompanionCharacterProfile safeProfile = safeProfile(player, profile);
        String profileKey = safeProfile.key();

        Optional<FriendEntity> sessionCompanion = resolveSessionCompanion(player, profileKey);
        if (sessionCompanion.isPresent()) {
            applyCompanionProfile(sessionCompanion.get(), safeProfile);
            moveNearPlayer(sessionCompanion.get(), player);
            return sessionCompanion;
        }

        Optional<FriendEntity> ownedCompanion = findNearestOwnedCompanion(player, safeProfile, EXISTING_COMPANION_SEARCH_RADIUS);
        if (ownedCompanion.isPresent()) {
            applyCompanionProfile(ownedCompanion.get(), safeProfile);
            moveNearPlayer(ownedCompanion.get(), player);
            if (sessionManaged) {
                sessionCompanionsFor(player).put(profileKey, ownedCompanion.get().getUUID());
            }
            return ownedCompanion;
        }

        return Optional.ofNullable(spawnCompanionFor(player, safeProfile, sessionManaged));
    }

    public static FriendEntity spawnCompanionFor(ServerPlayer player, boolean sessionManaged) {
        return spawnCompanionFor(player, profileFromMemory(player), sessionManaged);
    }

    public static FriendEntity spawnCompanionFor(
            ServerPlayer player,
            CompanionCharacterProfile profile,
            boolean sessionManaged
    ) {
        if (player == null) {
            return null;
        }
        CompanionCharacterProfile safeProfile = safeProfile(player, profile);
        ServerLevel level = player.serverLevel();
        FriendEntity friend = ModEntities.FRIEND.get().create(level);
        if (friend == null) {
            return null;
        }

        moveNearPlayer(friend, player);
        applyCompanionProfile(friend, safeProfile);
        friend.setOwner(player);
        friend.setFriendState(FriendState.IDLE);
        level.addFreshEntity(friend);
        if (sessionManaged) {
            sessionCompanionsFor(player).put(safeProfile.key(), friend.getUUID());
        }
        return friend;
    }

    public static Optional<FriendEntity> replaceCompanionFor(ServerPlayer player, boolean sessionManaged) {
        if (player == null) {
            return Optional.empty();
        }
        CompanionCharacterProfile profile = profileFromMemory(player);
        dismissCompanion(player, profile);
        return Optional.ofNullable(spawnCompanionFor(player, profile, sessionManaged));
    }

    public static boolean dismissSessionCompanion(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ConcurrentMap<String, UUID> companions = SESSION_COMPANIONS.remove(player.getUUID());
        if (companions == null || companions.isEmpty()) {
            return false;
        }
        boolean dismissed = false;
        for (UUID companionId : companions.values()) {
            dismissed |= dismissCompanion(player.getServer(), companionId);
        }
        return dismissed;
    }

    public static void syncSessionCompanionsFor(ServerPlayer player, List<CompanionCharacterProfile> profiles) {
        if (player == null || profiles == null || profiles.isEmpty()) {
            return;
        }
        Set<String> assignedKeys = new HashSet<>();
        for (CompanionCharacterProfile profile : profiles) {
            if (profile != null && profile.hasIdentity()) {
                assignedKeys.add(profile.key());
            }
        }
        if (assignedKeys.isEmpty()) {
            return;
        }

        ConcurrentMap<String, UUID> companions = sessionCompanionsFor(player);
        companions.forEach((key, uuid) -> {
            if (!assignedKeys.contains(key)) {
                dismissCompanion(player.getServer(), uuid);
                companions.remove(key, uuid);
            }
        });

        for (CompanionCharacterProfile profile : profiles) {
            if (profile != null && profile.hasIdentity()) {
                ensureCompanionFor(player, profile, true);
            }
        }
    }

    public static boolean dismissCompanion(ServerPlayer player, CompanionCharacterProfile profile) {
        if (player == null) {
            return false;
        }
        CompanionCharacterProfile safeProfile = safeProfile(player, profile);
        ConcurrentMap<String, UUID> companions = SESSION_COMPANIONS.get(player.getUUID());
        UUID sessionUuid = companions == null ? null : companions.remove(safeProfile.key());
        if (dismissCompanion(player.getServer(), sessionUuid)) {
            return true;
        }

        Optional<FriendEntity> ownedCompanion = findNearestOwnedCompanion(player, safeProfile, EXISTING_COMPANION_SEARCH_RADIUS);
        if (ownedCompanion.isEmpty()) {
            return false;
        }
        FriendEntity friend = ownedCompanion.get();
        removeSessionCompanion(friend.getUUID());
        friend.stopTask();
        friend.discard();
        return true;
    }

    public static boolean dismissNearestOwnedCompanion(ServerPlayer player, int radius) {
        Optional<FriendEntity> companion = findNearestOwnedCompanion(player, radius);
        if (companion.isEmpty()) {
            return false;
        }
        FriendEntity friend = companion.get();
        removeSessionCompanion(friend.getUUID());
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

    public static Optional<FriendEntity> findNearestOwnedCompanion(
            ServerPlayer player,
            CompanionCharacterProfile profile,
            int radius
    ) {
        if (player == null) {
            return Optional.empty();
        }
        CompanionCharacterProfile safeProfile = safeProfile(player, profile);
        return player.serverLevel()
                .getEntitiesOfClass(
                        FriendEntity.class,
                        player.getBoundingBox().inflate(Math.max(1, radius)),
                        friend -> friend.isAlive()
                                && friend.isOwnedBy(player)
                                && friend.matchesCompanionProfile(safeProfile)
                )
                .stream()
                .min(Comparator.comparingDouble(friend -> friend.distanceToSqr(player)));
    }

    private static Optional<FriendEntity> resolveSessionCompanion(ServerPlayer player, String profileKey) {
        ConcurrentMap<String, UUID> companions = SESSION_COMPANIONS.get(player.getUUID());
        UUID companionId = companions == null ? null : companions.get(profileKey);
        if (companionId == null) {
            return Optional.empty();
        }
        Entity entity = player.serverLevel().getEntity(companionId);
        if (entity instanceof FriendEntity friend
                && friend.isAlive()
                && friend.isOwnedBy(player)
                && friend.getCompanionProfileKey().equals(profileKey)) {
            return Optional.of(friend);
        }
        companions.remove(profileKey, companionId);
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

    private static void applyCompanionProfile(FriendEntity friend, CompanionCharacterProfile profile) {
        friend.setCompanionProfile(profile);
        friend.setCustomName(Component.literal(profile.displayName()));
    }

    private static CompanionCharacterProfile profileFromMemory(ServerPlayer player) {
        if (player == null) {
            return CompanionCharacterProfile.empty();
        }
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        return CompanionCharacterProfile.fromMemory(memory);
    }

    private static CompanionCharacterProfile safeProfile(ServerPlayer player, CompanionCharacterProfile profile) {
        if (profile != null && profile.hasIdentity()) {
            return profile;
        }
        return profileFromMemory(player);
    }

    private static ConcurrentMap<String, UUID> sessionCompanionsFor(ServerPlayer player) {
        return SESSION_COMPANIONS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
    }

    private static void removeSessionCompanion(UUID companionUuid) {
        if (companionUuid == null) {
            return;
        }
        SESSION_COMPANIONS.values().forEach(map -> map.values().removeIf(uuid -> uuid.equals(companionUuid)));
    }
}

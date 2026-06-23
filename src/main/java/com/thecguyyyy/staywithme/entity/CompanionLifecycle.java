package com.thecguyyyy.staywithme.entity;

import com.thecguyyyy.staywithme.ai.FriendState;
import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import com.thecguyyyy.staywithme.perception.FriendPerception;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CompanionLifecycle {
    private static final int EXISTING_COMPANION_SEARCH_RADIUS = 128;
    private static final int NEARBY_SPAWN_SEARCH_RADIUS = 2;
    private static final int[] NEARBY_SPAWN_Y_OFFSETS = {0, 1, -1};
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

        Optional<FriendEntity> rememberedCompanion = sessionManaged
                ? resolveRememberedCompanion(player, safeProfile, profileKey)
                : Optional.empty();
        if (rememberedCompanion.isPresent()) {
            applyCompanionProfile(rememberedCompanion.get(), safeProfile);
            moveNearPlayer(rememberedCompanion.get(), player);
            sessionCompanionsFor(player).put(profileKey, rememberedCompanion.get().getUUID());
            return rememberedCompanion;
        }

        Optional<FriendEntity> ownedCompanion = findNearestOwnedCompanion(player, safeProfile, EXISTING_COMPANION_SEARCH_RADIUS);
        if (ownedCompanion.isPresent()) {
            applyCompanionProfile(ownedCompanion.get(), safeProfile);
            moveNearPlayer(ownedCompanion.get(), player);
            if (sessionManaged) {
                sessionCompanionsFor(player).put(profileKey, ownedCompanion.get().getUUID());
                rememberCompanion(player, profileKey, ownedCompanion.get().getUUID());
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
            rememberCompanion(player, safeProfile.key(), friend.getUUID());
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
        for (Map.Entry<String, UUID> companion : companions.entrySet()) {
            dismissed |= dismissCompanion(player, companion.getValue());
            forgetCompanion(player, companion.getKey());
        }
        return dismissed;
    }

    public static void syncSessionCompanionsFor(ServerPlayer player, List<CompanionCharacterProfile> profiles) {
        if (player == null) {
            return;
        }
        Set<String> assignedKeys = new HashSet<>();
        List<CompanionCharacterProfile> safeProfiles = profiles == null ? List.of() : profiles;
        for (CompanionCharacterProfile profile : safeProfiles) {
            if (profile != null && profile.hasIdentity()) {
                assignedKeys.add(profile.key());
            }
        }

        dismissUnassignedRememberedCompanions(player, assignedKeys);

        ConcurrentMap<String, UUID> companions = sessionCompanionsFor(player);
        companions.forEach((key, uuid) -> {
            if (!assignedKeys.contains(key)) {
                dismissCompanion(player, uuid);
                companions.remove(key, uuid);
                forgetCompanion(player, key);
            }
        });

        for (CompanionCharacterProfile profile : safeProfiles) {
            if (profile != null && profile.hasIdentity()) {
                ensureCompanionFor(player, profile, true);
            }
        }
    }

    private static void dismissUnassignedRememberedCompanions(ServerPlayer player, Set<String> assignedKeys) {
        if (player == null || assignedKeys == null) {
            return;
        }
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        if (memory.companionEntityUuids.isEmpty()) {
            return;
        }

        List<Map.Entry<String, String>> staleEntries = memory.companionEntityUuids.entrySet().stream()
                .filter(entry -> !assignedKeys.contains(entry.getKey()))
                .toList();
        if (staleEntries.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> staleEntry : staleEntries) {
            parseUuid(staleEntry.getValue()).ifPresent(companionId -> {
                dismissCompanion(player, companionId);
                removeSessionCompanion(companionId);
            });
            memory.companionEntityUuids.remove(staleEntry.getKey());
        }
        memory.touch();
        JsonMemoryStore.save(memory);
    }

    public static boolean dismissCompanion(ServerPlayer player, CompanionCharacterProfile profile) {
        if (player == null) {
            return false;
        }
        CompanionCharacterProfile safeProfile = safeProfile(player, profile);
        String profileKey = safeProfile.key();
        ConcurrentMap<String, UUID> companions = SESSION_COMPANIONS.get(player.getUUID());
        UUID sessionUuid = companions == null ? null : companions.remove(profileKey);
        if (dismissCompanion(player, sessionUuid)) {
            forgetCompanion(player, profileKey);
            return true;
        }

        Optional<UUID> rememberedUuid = rememberedCompanionUuid(player, profileKey);
        if (rememberedUuid.isPresent()) {
            removeSessionCompanion(rememberedUuid.get());
            forgetCompanion(player, profileKey);
            if (dismissCompanion(player, rememberedUuid.get())) {
                return true;
            }
        }

        Optional<FriendEntity> ownedCompanion = findNearestOwnedCompanion(player, safeProfile, EXISTING_COMPANION_SEARCH_RADIUS);
        if (ownedCompanion.isEmpty()) {
            return false;
        }
        FriendEntity friend = ownedCompanion.get();
        removeSessionCompanion(friend.getUUID());
        forgetCompanion(player, profileKey);
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
        forgetCompanion(player, friend.getUUID());
        friend.stopTask();
        friend.discard();
        return true;
    }

    public static int dismissAllCompanions(ServerPlayer player) {
        if (player == null) {
            return 0;
        }

        Set<UUID> trackedUuids = new HashSet<>();
        ConcurrentMap<String, UUID> sessionCompanions = SESSION_COMPANIONS.remove(player.getUUID());
        if (sessionCompanions != null) {
            trackedUuids.addAll(sessionCompanions.values());
        }

        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        for (String rememberedValue : memory.companionEntityUuids.values()) {
            parseUuid(rememberedValue).ifPresent(trackedUuids::add);
        }
        if (!memory.companionEntityUuids.isEmpty()) {
            memory.companionEntityUuids.clear();
            memory.touch();
            JsonMemoryStore.save(memory);
        }

        int dismissed = 0;
        for (UUID companionUuid : trackedUuids) {
            if (dismissCompanion(player, companionUuid)) {
                dismissed++;
            }
            removeSessionCompanion(companionUuid);
        }
        return dismissed;
    }

    public static List<FriendEntity> getActiveCompanions(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return List.of();
        }
        Map<String, UUID> trackedCompanions = new ConcurrentHashMap<>();
        ConcurrentMap<String, UUID> sessionCompanions = SESSION_COMPANIONS.get(player.getUUID());
        if (sessionCompanions != null) {
            trackedCompanions.putAll(sessionCompanions);
        }

        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        List<String> invalidRememberedKeys = new ArrayList<>();
        memory.companionEntityUuids.forEach((key, value) -> {
            Optional<UUID> companionUuid = parseUuid(value);
            if (companionUuid.isPresent()) {
                trackedCompanions.putIfAbsent(key, companionUuid.get());
            } else {
                invalidRememberedKeys.add(key);
            }
        });
        if (!invalidRememberedKeys.isEmpty()) {
            invalidRememberedKeys.forEach(memory.companionEntityUuids::remove);
            memory.touch();
            JsonMemoryStore.save(memory);
        }

        Set<UUID> seen = new HashSet<>();
        List<FriendEntity> companions = new ArrayList<>();
        trackedCompanions.forEach((key, uuid) -> {
            if (uuid == null || !seen.add(uuid)) {
                return;
            }
            findCompanionByUuid(player.getServer(), uuid)
                    .filter(friend -> friend.isAlive() && friend.isOwnedBy(player))
                    .ifPresent(friend -> {
                        companions.add(friend);
                        sessionCompanionsFor(player).putIfAbsent(key, friend.getUUID());
                    });
        });
        companions.sort(Comparator.comparingDouble(friend -> friend.distanceToSqr(player)));
        return companions;
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
        Optional<FriendEntity> remembered = findCompanionByUuid(player.serverLevel(), companionId);
        if (remembered.isPresent()
                && remembered.get().isAlive()
                && remembered.get().isOwnedBy(player)
                && remembered.get().getCompanionProfileKey().equals(profileKey)) {
            FriendEntity friend = remembered.get();
            return Optional.of(friend);
        }
        companions.remove(profileKey, companionId);
        dismissCompanion(player, companionId);
        forgetCompanion(player, profileKey);
        return Optional.empty();
    }

    private static Optional<FriendEntity> resolveRememberedCompanion(
            ServerPlayer player,
            CompanionCharacterProfile profile,
            String profileKey
    ) {
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        String rememberedValue = memory.companionEntityUuids.get(profileKey);
        UUID companionId = parseUuid(rememberedValue).orElse(null);
        if (companionId == null) {
            if (rememberedValue != null) {
                memory.companionEntityUuids.remove(profileKey);
                memory.touch();
                JsonMemoryStore.save(memory);
            }
            return Optional.empty();
        }
        Optional<FriendEntity> remembered = findCompanionByUuid(player.serverLevel(), companionId)
                .filter(friend -> friend.isAlive()
                        && friend.isOwnedBy(player)
                        && friend.matchesCompanionProfile(profile));
        if (remembered.isPresent()) {
            return remembered;
        }
        dismissCompanion(player, companionId);
        memory.companionEntityUuids.remove(profileKey);
        memory.touch();
        JsonMemoryStore.save(memory);
        return Optional.empty();
    }

    private static boolean dismissCompanion(ServerPlayer owner, UUID companionId) {
        if (owner == null || owner.getServer() == null || companionId == null) {
            return false;
        }
        for (ServerLevel level : owner.getServer().getAllLevels()) {
            Entity entity = level.getEntity(companionId);
            if (entity instanceof FriendEntity friend && friend.isOwnedBy(owner)) {
                friend.stopTask();
                friend.discard();
                return true;
            }
        }
        return false;
    }

    private static Optional<FriendEntity> findCompanionByUuid(ServerLevel level, UUID companionId) {
        if (level == null || companionId == null) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(companionId);
        if (entity instanceof FriendEntity friend) {
            return Optional.of(friend);
        }
        return Optional.empty();
    }

    private static Optional<FriendEntity> findCompanionByUuid(MinecraftServer server, UUID companionId) {
        if (server == null || companionId == null) {
            return Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            Optional<FriendEntity> companion = findCompanionByUuid(level, companionId);
            if (companion.isPresent()) {
                return companion;
            }
        }
        return Optional.empty();
    }

    private static void moveNearPlayer(FriendEntity friend, ServerPlayer player) {
        BlockPos spawnPos = findNearbyCompanionStandPosition(player)
                .orElseGet(() -> player.blockPosition().offset(
                        player.getRandom().nextInt(3) - 1,
                        1,
                        player.getRandom().nextInt(3) - 1
                ));
        friend.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot(), 0.0F);
        friend.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private static Optional<BlockPos> findNearbyCompanionStandPosition(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = 1; radius <= NEARBY_SPAWN_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int yOffset : NEARBY_SPAWN_Y_OFFSETS) {
                        candidates.add(origin.offset(dx, yOffset, dz));
                    }
                }
            }
        }

        while (!candidates.isEmpty()) {
            BlockPos candidate = candidates.remove(player.getRandom().nextInt(candidates.size()));
            if (FriendPerception.canStandAt(level, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
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

    private static void rememberCompanion(ServerPlayer player, String profileKey, UUID companionUuid) {
        if (player == null || profileKey == null || profileKey.isBlank() || companionUuid == null) {
            return;
        }
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        memory.companionEntityUuids.put(profileKey, companionUuid.toString());
        memory.touch();
        JsonMemoryStore.save(memory);
    }

    private static Optional<UUID> rememberedCompanionUuid(ServerPlayer player, String profileKey) {
        if (player == null || profileKey == null || profileKey.isBlank()) {
            return Optional.empty();
        }
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        String rememberedValue = memory.companionEntityUuids.get(profileKey);
        Optional<UUID> companionUuid = parseUuid(rememberedValue);
        if (rememberedValue != null && companionUuid.isEmpty()) {
            memory.companionEntityUuids.remove(profileKey);
            memory.touch();
            JsonMemoryStore.save(memory);
        }
        return companionUuid;
    }

    private static void forgetCompanion(ServerPlayer player, String profileKey) {
        if (player == null || profileKey == null || profileKey.isBlank()) {
            return;
        }
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        if (memory.companionEntityUuids.remove(profileKey) != null) {
            memory.touch();
            JsonMemoryStore.save(memory);
        }
    }

    private static void forgetCompanion(ServerPlayer player, UUID companionUuid) {
        if (player == null || companionUuid == null) {
            return;
        }
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        boolean removed = memory.companionEntityUuids.values().removeIf(value -> companionUuid.toString().equals(value));
        if (removed) {
            memory.touch();
            JsonMemoryStore.save(memory);
        }
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class WorldSnapshot {
    public final String playerName;
    public final String playerUuid;
    public final String playerPos;
    public final String npcPos;
    public final String npcState;
    public final double distanceToPlayer;
    public final int nearbyHostileCount;
    public final int nearbyDroppedItemCount;
    public final int nearbyLogBlockCount;
    public final String dimension;
    public final String inventorySummary;
    public final String npcInventorySummary;

    private WorldSnapshot(
            String playerName,
            String playerUuid,
            String playerPos,
            String npcPos,
            String npcState,
            double distanceToPlayer,
            int nearbyHostileCount,
            int nearbyDroppedItemCount,
            int nearbyLogBlockCount,
            String dimension,
            String inventorySummary,
            String npcInventorySummary
    ) {
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.playerPos = playerPos;
        this.npcPos = npcPos;
        this.npcState = npcState;
        this.distanceToPlayer = distanceToPlayer;
        this.nearbyHostileCount = nearbyHostileCount;
        this.nearbyDroppedItemCount = nearbyDroppedItemCount;
        this.nearbyLogBlockCount = nearbyLogBlockCount;
        this.dimension = dimension;
        this.inventorySummary = inventorySummary;
        this.npcInventorySummary = npcInventorySummary;
    }

    public static WorldSnapshot capture(ServerPlayer player, FriendEntity friend) {
        ServerLevel level = player.serverLevel();
        AABB searchBox = friend.getBoundingBox().inflate(16.0D);
        int hostileCount = level.getEntitiesOfClass(Monster.class, searchBox, entity -> entity.isAlive()).size();
        int droppedItemCount = level.getEntitiesOfClass(ItemEntity.class, searchBox, entity -> entity.isAlive()).size();
        int logCount = countNearbyLogs(level, friend.blockPosition(), 8);

        return new WorldSnapshot(
                player.getGameProfile().getName(),
                player.getUUID().toString(),
                formatPos(player.blockPosition()),
                formatPos(friend.blockPosition()),
                friend.getFriendState().name(),
                friend.distanceTo(player),
                hostileCount,
                droppedItemCount,
                logCount,
                level.dimension().location().toString(),
                summarizeInventory(player),
                friend.getInventorySummary()
        );
    }

    private static int countNearbyLogs(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        BlockPos min = center.offset(-radius, -4, -radius);
        BlockPos max = center.offset(radius, 6, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                count++;
            }
        }
        return count;
    }

    private static String summarizeInventory(ServerPlayer player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                continue;
            }
            String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(key, stack.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            return "empty";
        }
        return counts.entrySet().stream()
                .limit(12)
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}

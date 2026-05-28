package com.thecguyyyy.staywithme.perception;

import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

public record PerceptionSnapshot(
        long gameTime,
        String dimension,
        BlockPos npcPos,
        int nearbyHostileCount,
        Optional<UUID> nearestHostileUuid,
        String nearestHostileName,
        double nearestHostileDistance,
        int nearbyDroppedItemCount,
        int nearbyLogBlockCount,
        Optional<BlockPos> nearestLogPos,
        double nearestLogDistance,
        int nearbyStandableBlockCount,
        String inventorySummary
) {
    public static PerceptionSnapshot empty() {
        return new PerceptionSnapshot(
                0L,
                "unknown",
                BlockPos.ZERO,
                0,
                Optional.empty(),
                "none",
                -1.0D,
                0,
                0,
                Optional.empty(),
                -1.0D,
                0,
                "empty"
        );
    }

    public String summary() {
        return "dim=" + this.dimension
                + ", pos=" + formatPos(this.npcPos)
                + ", logs=" + this.nearbyLogBlockCount
                + (this.nearestLogPos.isPresent() ? " nearestLog=" + formatPos(this.nearestLogPos.get()) : " nearestLog=none")
                + ", hostiles=" + this.nearbyHostileCount
                + ("none".equals(this.nearestHostileName) ? "" : " nearestHostile=" + this.nearestHostileName)
                + ", items=" + this.nearbyDroppedItemCount
                + ", standable=" + this.nearbyStandableBlockCount
                + ", inv=" + this.inventorySummary;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}

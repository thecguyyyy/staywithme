package com.thecguyyyy.staywithme.perception;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FriendPerception {
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final int ENTITY_RADIUS = 16;
    private static final int BLOCK_RADIUS = 50;
    private static final int BLOCK_SEARCH_DOWN = 4;
    private static final int BLOCK_SEARCH_UP = 8;

    private final FriendEntity friend;
    private PerceptionSnapshot snapshot = PerceptionSnapshot.empty();
    private long lastRefreshGameTime = -1L;

    public FriendPerception(FriendEntity friend) {
        this.friend = friend;
    }

    public void tick() {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime - this.lastRefreshGameTime >= REFRESH_INTERVAL_TICKS) {
            this.refresh(level);
        }
    }

    public PerceptionSnapshot current() {
        return this.snapshot;
    }

    public PerceptionSnapshot refreshNow() {
        if (this.friend.level() instanceof ServerLevel level) {
            this.refresh(level);
        }
        return this.snapshot;
    }

    public Optional<BlockPos> nearestBreakableLog(int radius) {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        this.refresh(level);
        return this.findNearestBreakableLog(level, radius).map(ScoredBlock::pos);
    }

    public Optional<LivingEntity> nearestHostile(int radius) {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        AABB searchBox = this.friend.getBoundingBox().inflate(radius);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, searchBox, LivingEntity::isAlive);
        return monsters.stream()
                .min(Comparator.comparingDouble(this.friend::distanceToSqr))
                .map(entity -> (LivingEntity) entity);
    }

    private void refresh(ServerLevel level) {
        AABB entitySearchBox = this.friend.getBoundingBox().inflate(ENTITY_RADIUS);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, entitySearchBox, LivingEntity::isAlive);
        List<ItemEntity> droppedItems = level.getEntitiesOfClass(ItemEntity.class, entitySearchBox,
                entity -> entity.isAlive() && !entity.getItem().isEmpty());

        Optional<LivingEntity> nearestHostile = monsters.stream()
                .min(Comparator.comparingDouble(this.friend::distanceToSqr))
                .map(entity -> (LivingEntity) entity);
        Optional<ScoredBlock> nearestLog = this.findNearestBreakableLog(level, BLOCK_RADIUS);
        int logCount = this.countLogs(level, BLOCK_RADIUS);
        int standableCount = this.countStandableBlocks(level, 6);

        this.snapshot = new PerceptionSnapshot(
                level.getGameTime(),
                level.dimension().location().toString(),
                this.friend.blockPosition(),
                monsters.size(),
                nearestHostile.map(LivingEntity::getUUID),
                nearestHostile.map(entity -> entity.getType().getDescription().getString()).orElse("none"),
                nearestHostile.map(entity -> (double) this.friend.distanceTo(entity)).orElse(-1.0D),
                droppedItems.size(),
                logCount,
                nearestLog.map(ScoredBlock::pos),
                nearestLog.map(ScoredBlock::distance).orElse(-1.0D),
                standableCount,
                this.friend.getInventorySummary()
        );
        this.lastRefreshGameTime = level.getGameTime();
    }

    private Optional<ScoredBlock> findNearestBreakableLog(ServerLevel level, int radius) {
        BlockPos center = this.friend.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = -BLOCK_SEARCH_DOWN; y <= BLOCK_SEARCH_UP; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!isBreakableLog(level, cursor)) {
                        continue;
                    }
                    double distance = center.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }

        return best == null ? Optional.empty() : Optional.of(new ScoredBlock(best, Math.sqrt(bestDistance)));
    }

    private int countLogs(ServerLevel level, int radius) {
        BlockPos center = this.friend.blockPosition();
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = -BLOCK_SEARCH_DOWN; y <= BLOCK_SEARCH_UP; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (level.getBlockState(cursor).is(BlockTags.LOGS)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countStandableBlocks(ServerLevel level, int radius) {
        BlockPos center = this.friend.blockPosition();
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = -2; y <= 2; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (canStandAt(level, cursor)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static boolean isBreakableLog(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.LOGS)
                && !state.hasBlockEntity()
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    public static boolean canStandAt(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());
        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !below.isAir()
                && below.getFluidState().isEmpty()
                && below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private record ScoredBlock(BlockPos pos, double distance) {
    }
}

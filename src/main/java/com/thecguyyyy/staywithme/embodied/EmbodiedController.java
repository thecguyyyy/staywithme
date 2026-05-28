package com.thecguyyyy.staywithme.embodied;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

public interface EmbodiedController {
    default void tick() {
    }

    default String name() {
        return this.getClass().getSimpleName();
    }

    default String status() {
        return this.name();
    }

    void stop();

    void moveTo(double x, double y, double z, double speed);

    void moveTo(Entity target, double speed);

    default void moveTo(BlockPos pos, double speed) {
        this.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, speed);
    }

    void say(String message);

    boolean attack(Entity target);

    boolean breakBlock(BlockPos pos);

    default boolean mineBlocks(int count, Block... blocks) {
        return false;
    }

    default boolean isMining() {
        return false;
    }
}

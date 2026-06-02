package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.playerengine.FriendInteractionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class PlaceActionAdapter {
    private final EmbodiedController body;
    private final FriendInteractionProvider interaction;

    public PlaceActionAdapter(EmbodiedController body, FriendInteractionProvider interaction) {
        this.body = body;
        this.interaction = interaction;
    }

    public PlaceResult placeBlock(
            ServerLevel level,
            BlockPos target,
            Block block,
            Predicate<ItemStack> inventoryMatcher,
            Supplier<Optional<BlockPos>> approachTarget,
            double speed
    ) {
        if (!this.interaction.canReachBlock(target)) {
            Optional<BlockPos> approach = approachTarget.get();
            if (approach.isEmpty()) {
                return PlaceResult.FAILED;
            }
            this.body.moveTo(approach.get(), speed);
            return PlaceResult.WORKING;
        }

        this.body.stop();
        return this.interaction.placeBlock(level, target, block, inventoryMatcher)
                ? PlaceResult.PLACED
                : PlaceResult.FAILED;
    }

    public enum PlaceResult {
        WORKING,
        PLACED,
        FAILED
    }
}

package com.thecguyyyy.staywithme.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ConstructionPathSnapshot {
    public String dimension;
    public RelativePos target;
    public int horizontalRadius;
    public int verticalRadius;
    public int repairBlocks;
    public String legend = ".=open s=solid_breakable #=blocked_or_unbreakable ~=fluid ?=unloaded";
    public List<Layer> layers = new ArrayList<>();

    public static ConstructionPathSnapshot capture(
            ServerLevel level,
            BlockPos origin,
            BlockPos target,
            int horizontalRadius,
            int verticalRadius,
            int repairBlocks
    ) {
        ConstructionPathSnapshot snapshot = new ConstructionPathSnapshot();
        snapshot.dimension = level.dimension().location().toString();
        snapshot.target = RelativePos.from(origin, target);
        snapshot.horizontalRadius = Math.max(1, horizontalRadius);
        snapshot.verticalRadius = Math.max(1, verticalRadius);
        snapshot.repairBlocks = Math.max(0, repairBlocks);

        for (int dy = -snapshot.verticalRadius; dy <= snapshot.verticalRadius; dy++) {
            Layer layer = new Layer();
            layer.y = dy;
            for (int dz = -snapshot.horizontalRadius; dz <= snapshot.horizontalRadius; dz++) {
                StringBuilder row = new StringBuilder();
                for (int dx = -snapshot.horizontalRadius; dx <= snapshot.horizontalRadius; dx++) {
                    row.append(classify(level, origin.offset(dx, dy, dz)));
                }
                layer.rows.add(row.toString());
            }
            snapshot.layers.add(layer);
        }
        return snapshot;
    }

    private static char classify(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return '?';
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return '~';
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return '.';
        }
        return state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F ? '#' : 's';
    }

    public static class Layer {
        public int y;
        public List<String> rows = new ArrayList<>();
    }

    public static class RelativePos {
        public int x;
        public int y;
        public int z;

        public static RelativePos from(BlockPos origin, BlockPos pos) {
            RelativePos result = new RelativePos();
            result.x = pos.getX() - origin.getX();
            result.y = pos.getY() - origin.getY();
            result.z = pos.getZ() - origin.getZ();
            return result;
        }
    }
}

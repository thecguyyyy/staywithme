package com.thecguyyyy.staywithme.ai.navigation;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ConstructionRoutePlan {
    public String source = "unknown";
    public String reason = "";
    public List<Step> steps = new ArrayList<>();

    public void normalize() {
        if (this.source == null || this.source.isBlank()) {
            this.source = "unknown";
        }
        if (this.reason == null) {
            this.reason = "";
        }
        if (this.steps == null) {
            this.steps = new ArrayList<>();
        }
        this.steps.removeIf(step -> step == null);
        if (this.steps.size() > 64) {
            this.steps = new ArrayList<>(this.steps.subList(0, 64));
        }
    }

    public static Step relativeTo(BlockPos origin, BlockPos feetPos) {
        return new Step(
                feetPos.getX() - origin.getX(),
                feetPos.getY() - origin.getY(),
                feetPos.getZ() - origin.getZ()
        );
    }

    public static class Step {
        public int x;
        public int y;
        public int z;

        public Step() {
        }

        public Step(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos absoluteFrom(BlockPos origin) {
            return origin.offset(this.x, this.y, this.z);
        }
    }
}

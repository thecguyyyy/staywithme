package com.thecguyyyy.staywithme.memory;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExpeditionMemory {
    private static final int MAX_ROUTE_NOTES = 24;
    private static final int MAX_BRANCH_ROUTES = 32;
    private static final int MAX_HAZARDS = 24;
    private static final int MAX_EVENTS = 32;

    public String key;
    public String resourceId;
    public int requestedAmount;
    public String status;
    public String strategyMode;
    public String targetDimension;
    public int targetYMin;
    public int targetYMax;
    public String requiredTool;
    public Position supplyPoint;
    public Position supplyChest;
    public Position supplyFurnace;
    public Position supplyCraftingTable;
    public Position mineEntrance;
    public Position lastKnownPosition;
    public String lastTunnelDirection;
    public int minedAmount;
    public long startedAtEpochMillis;
    public long updatedAtEpochMillis;
    public long completedAtEpochMillis;
    public List<String> routeNotes = new ArrayList<>();
    public List<BranchRoute> branchRoutes = new ArrayList<>();
    public List<HazardNote> hazards = new ArrayList<>();
    public List<String> events = new ArrayList<>();

    public static ExpeditionMemory create(String resourceId, String dimension) {
        ExpeditionMemory memory = new ExpeditionMemory();
        memory.resourceId = normalizeResource(resourceId);
        memory.targetDimension = normalizeDimension(dimension);
        memory.key = keyFor(memory.resourceId, memory.targetDimension);
        memory.status = "known";
        memory.startedAtEpochMillis = System.currentTimeMillis();
        memory.updatedAtEpochMillis = memory.startedAtEpochMillis;
        return memory;
    }

    public static String keyFor(String resourceId, String dimension) {
        return normalizeDimension(dimension) + "|" + normalizeResource(resourceId);
    }

    public void normalize(String fallbackResourceId, String fallbackDimension) {
        if (this.resourceId == null || this.resourceId.isBlank()) {
            this.resourceId = normalizeResource(fallbackResourceId);
        }
        if (this.targetDimension == null || this.targetDimension.isBlank()) {
            this.targetDimension = normalizeDimension(fallbackDimension);
        }
        this.key = keyFor(this.resourceId, this.targetDimension);
        if (this.status == null || this.status.isBlank()) {
            this.status = "known";
        }
        if (this.targetYMin > this.targetYMax) {
            int swap = this.targetYMin;
            this.targetYMin = this.targetYMax;
            this.targetYMax = swap;
        }
        if (this.startedAtEpochMillis <= 0L) {
            this.startedAtEpochMillis = System.currentTimeMillis();
        }
        if (this.updatedAtEpochMillis <= 0L) {
            this.updatedAtEpochMillis = this.startedAtEpochMillis;
        }
        if (this.routeNotes == null) {
            this.routeNotes = new ArrayList<>();
        }
        if (this.branchRoutes == null) {
            this.branchRoutes = new ArrayList<>();
        }
        if (this.hazards == null) {
            this.hazards = new ArrayList<>();
        }
        if (this.events == null) {
            this.events = new ArrayList<>();
        }
        normalizePosition(this.supplyPoint);
        normalizePosition(this.supplyChest);
        normalizePosition(this.supplyFurnace);
        normalizePosition(this.supplyCraftingTable);
        normalizePosition(this.mineEntrance);
        normalizePosition(this.lastKnownPosition);
        this.branchRoutes.forEach(route -> {
            if (route != null) {
                route.normalize(this.targetDimension);
            }
        });
        this.hazards.forEach(hazard -> {
            if (hazard != null) {
                hazard.normalize(this.targetDimension);
            }
        });
        this.branchRoutes.removeIf(route -> route == null);
        this.hazards.removeIf(hazard -> hazard == null);
        trim(this.routeNotes, MAX_ROUTE_NOTES);
        trim(this.branchRoutes, MAX_BRANCH_ROUTES);
        trim(this.hazards, MAX_HAZARDS);
        trim(this.events, MAX_EVENTS);
    }

    public void touch() {
        this.updatedAtEpochMillis = System.currentTimeMillis();
    }

    public void addRouteNote(String note) {
        addBounded(this.routeNotes, note, MAX_ROUTE_NOTES);
        this.touch();
    }

    public void addEvent(String event) {
        addBounded(this.events, event, MAX_EVENTS);
        this.touch();
    }

    public void addBranchRoute(BranchRoute route) {
        if (route == null) {
            return;
        }
        route.normalize(this.targetDimension);
        this.branchRoutes.add(route);
        trim(this.branchRoutes, MAX_BRANCH_ROUTES);
        this.touch();
    }

    public void addHazard(HazardNote hazard) {
        if (hazard == null) {
            return;
        }
        hazard.normalize(this.targetDimension);
        for (HazardNote existing : this.hazards) {
            if (existing == null) {
                continue;
            }
            existing.normalize(this.targetDimension);
            if (samePosition(existing.position, hazard.position)
                    && existing.type.equalsIgnoreCase(hazard.type)) {
                existing.note = hazard.note;
                existing.updatedAtEpochMillis = System.currentTimeMillis();
                this.touch();
                return;
            }
        }
        this.hazards.add(hazard);
        trim(this.hazards, MAX_HAZARDS);
        this.touch();
    }

    public String summary() {
        StringBuilder builder = new StringBuilder(this.resourceId == null ? "unknown" : this.resourceId);
        builder.append(" status=").append(this.status == null ? "known" : this.status);
        if (this.requestedAmount > 0) {
            builder.append(" target=").append(this.requestedAmount);
        }
        if (this.minedAmount > 0) {
            builder.append(" mined=").append(this.minedAmount);
        }
        if (this.targetYMin != 0 || this.targetYMax != 0) {
            builder.append(" y=").append(this.targetYMin).append("..").append(this.targetYMax);
        }
        if (this.supplyPoint != null) {
            builder.append(" supply=").append(this.supplyPoint.summary());
        }
        if (this.supplyChest != null) {
            builder.append(" chest=").append(this.supplyChest.summary());
        }
        if (this.supplyCraftingTable != null) {
            builder.append(" table=").append(this.supplyCraftingTable.summary());
        }
        if (this.mineEntrance != null) {
            builder.append(" entrance=").append(this.mineEntrance.summary());
        }
        if (this.lastTunnelDirection != null && !this.lastTunnelDirection.isBlank()) {
            builder.append(" dir=").append(this.lastTunnelDirection);
        }
        if (!this.branchRoutes.isEmpty()) {
            int completed = 0;
            int interrupted = 0;
            int invalidated = 0;
            for (BranchRoute route : this.branchRoutes) {
                if (route == null || route.status == null) {
                    continue;
                }
                if ("completed".equalsIgnoreCase(route.status)) {
                    completed++;
                } else if ("interrupted".equalsIgnoreCase(route.status)) {
                    interrupted++;
                } else if ("invalidated".equalsIgnoreCase(route.status)) {
                    invalidated++;
                }
            }
            builder.append(" branchRoutes=")
                    .append(this.branchRoutes.size())
                    .append("(completed=")
                    .append(completed)
                    .append(",interrupted=")
                    .append(interrupted)
                    .append(",invalidated=")
                    .append(invalidated)
                    .append(')');
        }
        if (!this.hazards.isEmpty()) {
            int lava = 0;
            int risky = 0;
            int other = 0;
            for (HazardNote hazard : this.hazards) {
                if (hazard == null || hazard.type == null) {
                    other++;
                } else if ("lava".equalsIgnoreCase(hazard.type)) {
                    lava++;
                } else if ("risky_block".equalsIgnoreCase(hazard.type)) {
                    risky++;
                } else {
                    other++;
                }
            }
            builder.append(" hazards=")
                    .append(this.hazards.size())
                    .append("(lava=")
                    .append(lava)
                    .append(",risky=")
                    .append(risky)
                    .append(",other=")
                    .append(other)
                    .append(')');
        }
        return builder.toString();
    }

    private void normalizePosition(Position position) {
        if (position != null) {
            position.normalize(this.targetDimension);
        }
    }

    private static String normalizeResource(String resourceId) {
        String normalized = resourceId == null ? "unknown" : resourceId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static String normalizeDimension(String dimension) {
        String normalized = dimension == null ? "minecraft:overworld" : dimension.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "minecraft:overworld" : normalized;
    }

    private static void addBounded(List<String> values, String value, int maxSize) {
        if (value == null || value.isBlank()) {
            return;
        }
        values.add(value);
        trim(values, maxSize);
    }

    private static <T> void trim(List<T> values, int maxSize) {
        while (values.size() > maxSize) {
            values.remove(0);
        }
    }

    private static boolean samePosition(Position first, Position second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.x == second.x
                && first.y == second.y
                && first.z == second.z
                && normalizeDimension(first.dimension).equals(normalizeDimension(second.dimension));
    }

    public static class Position {
        public String dimension;
        public int x;
        public int y;
        public int z;

        public static Position of(String dimension, BlockPos pos) {
            if (pos == null) {
                return null;
            }
            Position position = new Position();
            position.dimension = normalizeDimension(dimension);
            position.x = pos.getX();
            position.y = pos.getY();
            position.z = pos.getZ();
            return position;
        }

        public void normalize(String fallbackDimension) {
            this.dimension = normalizeDimension(this.dimension == null || this.dimension.isBlank() ? fallbackDimension : this.dimension);
        }

        public BlockPos asBlockPos() {
            return new BlockPos(this.x, this.y, this.z);
        }

        public boolean isInDimension(String dimension) {
            return normalizeDimension(dimension).equals(normalizeDimension(this.dimension));
        }

        public String summary() {
            return this.dimension + "@" + this.x + "," + this.y + "," + this.z;
        }
    }

    public static class BranchRoute {
        public String type;
        public String direction;
        public Position start;
        public Position end;
        public int plannedSteps;
        public int completedSteps;
        public String status;
        public long updatedAtEpochMillis;

        public static BranchRoute create(
                String type,
                String direction,
                String dimension,
                BlockPos start,
                BlockPos end,
                int plannedSteps,
                int completedSteps,
                String status
        ) {
            BranchRoute route = new BranchRoute();
            route.type = type == null || type.isBlank() ? "unknown" : type;
            route.direction = direction == null || direction.isBlank() ? "unknown" : direction;
            route.start = Position.of(dimension, start);
            route.end = Position.of(dimension, end);
            route.plannedSteps = Math.max(0, plannedSteps);
            route.completedSteps = Math.max(0, completedSteps);
            route.status = status == null || status.isBlank() ? "known" : status;
            route.updatedAtEpochMillis = System.currentTimeMillis();
            return route;
        }

        public void normalize(String fallbackDimension) {
            if (this.type == null || this.type.isBlank()) {
                this.type = "unknown";
            }
            if (this.direction == null || this.direction.isBlank()) {
                this.direction = "unknown";
            }
            if (this.status == null || this.status.isBlank()) {
                this.status = "known";
            }
            if (this.start != null) {
                this.start.normalize(fallbackDimension);
            }
            if (this.end != null) {
                this.end.normalize(fallbackDimension);
            }
            this.plannedSteps = Math.max(0, this.plannedSteps);
            this.completedSteps = Math.max(0, this.completedSteps);
            if (this.updatedAtEpochMillis <= 0L) {
                this.updatedAtEpochMillis = System.currentTimeMillis();
            }
        }
    }

    public static class HazardNote {
        public String type;
        public Position position;
        public String note;
        public long updatedAtEpochMillis;

        public static HazardNote create(String type, String dimension, BlockPos position, String note) {
            HazardNote hazard = new HazardNote();
            hazard.type = type == null || type.isBlank() ? "hazard" : type;
            hazard.position = Position.of(dimension, position);
            hazard.note = note == null ? "" : note;
            hazard.updatedAtEpochMillis = System.currentTimeMillis();
            return hazard;
        }

        public void normalize(String fallbackDimension) {
            if (this.type == null || this.type.isBlank()) {
                this.type = "hazard";
            }
            if (this.note == null) {
                this.note = "";
            }
            if (this.position != null) {
                this.position.normalize(fallbackDimension);
            }
            if (this.updatedAtEpochMillis <= 0L) {
                this.updatedAtEpochMillis = System.currentTimeMillis();
            }
        }
    }
}

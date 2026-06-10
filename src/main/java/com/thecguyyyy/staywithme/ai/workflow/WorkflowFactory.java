package com.thecguyyyy.staywithme.ai.workflow;

import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;

import java.util.List;

public final class WorkflowFactory {
    public static final String LOGS = "minecraft:logs";
    public static final String PLANKS = "minecraft:planks";
    public static final String CRAFTING_TABLE = "minecraft:crafting_table";
    public static final String STICKS = "minecraft:sticks";
    public static final String CHEST = "minecraft:chest";
    public static final String WOODEN_AXE = "minecraft:wooden_axe";
    public static final String WOODEN_PICKAXE = "minecraft:wooden_pickaxe";
    public static final String COBBLESTONE = "minecraft:cobblestone";
    public static final String STONE_PICKAXE = "minecraft:stone_pickaxe";
    public static final String FURNACE = "minecraft:furnace";
    public static final String COAL = "minecraft:coal";
    public static final String CHARCOAL = "minecraft:charcoal";
    public static final String RAW_IRON = "minecraft:raw_iron";
    public static final String IRON_INGOT = "minecraft:iron_ingot";
    public static final String IRON_PICKAXE = "minecraft:iron_pickaxe";
    public static final String TORCH = "minecraft:torch";
    public static final int EXPEDITION_TORCH_TARGET = 16;

    private WorkflowFactory() {
    }

    public static LongTaskWorkflow craftingTable() {
        return new LongTaskWorkflow("make_crafting_table", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 4),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1)
        ));
    }

    public static LongTaskWorkflow sticks() {
        return new LongTaskWorkflow("make_sticks", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 2),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 4)
        ));
    }

    public static LongTaskWorkflow chest() {
        return new LongTaskWorkflow("make_chest", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, CHEST, 1)
        ));
    }

    public static LongTaskWorkflow woodenAxe() {
        return new LongTaskWorkflow("make_wooden_axe", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 2),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_AXE, 1)
        ));
    }

    public static LongTaskWorkflow woodenPickaxe() {
        return new LongTaskWorkflow("make_wooden_pickaxe", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 2),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1)
        ));
    }

    public static LongTaskWorkflow stonePickaxe() {
        return new LongTaskWorkflow("make_stone_pickaxe", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 4),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1)
        ));
    }

    public static LongTaskWorkflow furnace() {
        return new LongTaskWorkflow("make_furnace", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 2),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 8),
                new WorkStep(WorkStepType.CRAFT_ITEM, FURNACE, 1)
        ));
    }

    public static LongTaskWorkflow ironIngot() {
        return new LongTaskWorkflow("make_iron_ingot", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 4),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 11),
                new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, FURNACE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, FURNACE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COAL, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, RAW_IRON, 1),
                new WorkStep(WorkStepType.SMELT_ITEM, IRON_INGOT, 1)
        ));
    }

    public static LongTaskWorkflow ironPickaxe() {
        return new LongTaskWorkflow("make_iron_pickaxe", List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 6),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 11),
                new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, FURNACE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, FURNACE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COAL, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, RAW_IRON, 3),
                new WorkStep(WorkStepType.SMELT_ITEM, IRON_INGOT, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, IRON_PICKAXE, 1)
        ));
    }

    public static LongTaskWorkflow mineWithWoodenPickaxe(String target, int amount) {
        return new LongTaskWorkflow("mine_with_wooden_pickaxe_" + workflowSafeTarget(target), List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 2),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, target, Math.max(1, amount))
        ));
    }

    public static LongTaskWorkflow mineWithStonePickaxe(String target, int amount) {
        return new LongTaskWorkflow("mine_with_stone_pickaxe_" + workflowSafeTarget(target), List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 4),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, target, Math.max(1, amount))
        ));
    }

    public static LongTaskWorkflow mineWithIronPickaxe(String target, int amount) {
        return new LongTaskWorkflow("mine_with_iron_pickaxe_" + workflowSafeTarget(target), List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 6),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 11),
                new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, FURNACE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, FURNACE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COAL, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, RAW_IRON, 3),
                new WorkStep(WorkStepType.SMELT_ITEM, IRON_INGOT, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, IRON_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, target, Math.max(1, amount))
        ));
    }

    public static LongTaskWorkflow mineDirect(String target, int amount) {
        return new LongTaskWorkflow(
                "mine_resource_" + workflowSafeTarget(target),
                List.of(new WorkStep(WorkStepType.ACQUIRE_ITEM, target, Math.max(1, amount)))
        );
    }

    public static LongTaskWorkflow miningExpeditionDirect(String target, int amount, String layerTarget) {
        return new LongTaskWorkflow("expedition_direct_" + workflowSafeTarget(target), List.of(
                new WorkStep(WorkStepType.DESCEND_TO_LAYER, layerTarget, 1),
                new WorkStep(WorkStepType.BRANCH_MINE_RESOURCE, target, Math.max(1, amount))
        ));
    }

    public static LongTaskWorkflow miningExpeditionWithWoodenPickaxe(String target, int amount, String layerTarget) {
        return new LongTaskWorkflow("expedition_wooden_pickaxe_" + workflowSafeTarget(target), List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 5),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 20),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, CHEST, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CHEST, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 2),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.DESCEND_TO_LAYER, layerTarget, 1),
                new WorkStep(WorkStepType.BRANCH_MINE_RESOURCE, target, Math.max(1, amount))
        ));
    }

    public static LongTaskWorkflow miningExpeditionWithStonePickaxe(String target, int amount, String layerTarget) {
        return new LongTaskWorkflow("expedition_stone_pickaxe_" + workflowSafeTarget(target), List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 5),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 20),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, CHEST, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CHEST, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 8),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COAL, 4),
                new WorkStep(WorkStepType.CRAFT_ITEM, TORCH, EXPEDITION_TORCH_TARGET),
                new WorkStep(WorkStepType.DESCEND_TO_LAYER, layerTarget, 1),
                new WorkStep(WorkStepType.BRANCH_MINE_RESOURCE, target, Math.max(1, amount))
        ));
    }

    public static LongTaskWorkflow miningExpeditionWithIronPickaxe(String target, int amount, String layerTarget) {
        return new LongTaskWorkflow("expedition_iron_pickaxe_" + workflowSafeTarget(target), List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 5),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 20),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, CHEST, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CHEST, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 10),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 11),
                new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, FURNACE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, FURNACE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COAL, 5),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, RAW_IRON, 3),
                new WorkStep(WorkStepType.SMELT_ITEM, IRON_INGOT, 3),
                new WorkStep(WorkStepType.CRAFT_ITEM, TORCH, EXPEDITION_TORCH_TARGET),
                new WorkStep(WorkStepType.CRAFT_ITEM, IRON_PICKAXE, 1),
                new WorkStep(WorkStepType.DESCEND_TO_LAYER, layerTarget, 1),
                new WorkStep(WorkStepType.BRANCH_MINE_RESOURCE, target, Math.max(1, amount))
        ));
    }

    public static List<WorkStep> toolRecovery(MiningTargetRegistry.ToolRequirement requirement) {
        return switch (requirement) {
            case NONE -> List.of();
            case WOODEN_PICKAXE -> List.of(
                    new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                    new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                    new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                    new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                    new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 2),
                    new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1)
            );
            case STONE_PICKAXE -> List.of(
                    new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                    new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                    new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                    new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                    new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 4),
                    new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                    new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 3),
                    new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1)
            );
            case IRON_PICKAXE -> List.of(
                    new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, 3),
                    new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, 12),
                    new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                    new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                    new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 6),
                    new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                    new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 11),
                    new WorkStep(WorkStepType.CRAFT_ITEM, STONE_PICKAXE, 1),
                    new WorkStep(WorkStepType.CRAFT_ITEM, FURNACE, 1),
                    new WorkStep(WorkStepType.PLACE_BLOCK, FURNACE, 1),
                    new WorkStep(WorkStepType.ACQUIRE_ITEM, COAL, 1),
                    new WorkStep(WorkStepType.ACQUIRE_ITEM, RAW_IRON, 3),
                    new WorkStep(WorkStepType.SMELT_ITEM, IRON_INGOT, 3),
                    new WorkStep(WorkStepType.CRAFT_ITEM, IRON_PICKAXE, 1)
            );
        };
    }

    public static List<WorkStep> charcoalRecovery(int amount) {
        int charcoal = Math.max(1, amount);
        int fuelPlanks = charcoal;
        int fuelLogs = (fuelPlanks + 3) / 4;
        int requiredLogs = charcoal + fuelLogs;
        return List.of(
                new WorkStep(WorkStepType.ACQUIRE_ITEM, LOGS, requiredLogs),
                new WorkStep(WorkStepType.CRAFT_ITEM, PLANKS, fuelPlanks),
                new WorkStep(WorkStepType.CRAFT_ITEM, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, CRAFTING_TABLE, 1),
                new WorkStep(WorkStepType.CRAFT_ITEM, STICKS, 2),
                new WorkStep(WorkStepType.CRAFT_ITEM, WOODEN_PICKAXE, 1),
                new WorkStep(WorkStepType.ACQUIRE_ITEM, COBBLESTONE, 8),
                new WorkStep(WorkStepType.CRAFT_ITEM, FURNACE, 1),
                new WorkStep(WorkStepType.PLACE_BLOCK, FURNACE, 1),
                new WorkStep(WorkStepType.SMELT_ITEM, CHARCOAL, charcoal)
        );
    }

    public static LongTaskWorkflow collectFuelCharcoal(int amount) {
        return new LongTaskWorkflow("collect_fuel_charcoal", charcoalRecovery(amount));
    }

    private static String workflowSafeTarget(String target) {
        return target == null ? "unknown" : target.replace(':', '_').replace('/', '_');
    }
}

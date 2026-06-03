package com.thecguyyyy.staywithme.ai.mining;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class MiningTargetRegistry {
    private static final Map<String, MiningTarget> TARGETS = new LinkedHashMap<>();

    static {
        register(new MiningTarget(
                "minecraft:cobblestone",
                "cobblestone",
                stack -> stack.is(Items.COBBLESTONE),
                "wooden pickaxe or better",
                ToolRequirement.WOODEN_PICKAXE,
                new ExplorationProfile("minecraft:overworld", 48, 60, 4),
                Blocks.STONE,
                Blocks.COBBLESTONE
        ));
        register(new MiningTarget(
                "minecraft:coal",
                "coal or charcoal",
                stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL),
                "wooden pickaxe or better",
                ToolRequirement.WOODEN_PICKAXE,
                new ExplorationProfile("minecraft:overworld", 32, 64, 8),
                Blocks.COAL_ORE,
                Blocks.DEEPSLATE_COAL_ORE
        ));
        register(new MiningTarget(
                "minecraft:raw_iron",
                "raw iron",
                stack -> stack.is(Items.RAW_IRON),
                "stone pickaxe or better",
                ToolRequirement.STONE_PICKAXE,
                new ExplorationProfile("minecraft:overworld", 0, 32, 10),
                Blocks.IRON_ORE,
                Blocks.DEEPSLATE_IRON_ORE
        ));
        registerAlias("minecraft:iron_ore", "minecraft:raw_iron");
        register(new MiningTarget(
                "minecraft:diamond",
                "diamond",
                stack -> stack.is(Items.DIAMOND),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile("minecraft:overworld", -59, -53, 14),
                Blocks.DIAMOND_ORE,
                Blocks.DEEPSLATE_DIAMOND_ORE
        ));
        register(new MiningTarget(
                "minecraft:lapis_lazuli",
                "lapis lazuli",
                stack -> stack.is(Items.LAPIS_LAZULI),
                "stone pickaxe or better",
                ToolRequirement.STONE_PICKAXE,
                new ExplorationProfile("minecraft:overworld", -4, 4, 10),
                Blocks.LAPIS_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE
        ));
        registerAlias("minecraft:lapis", "minecraft:lapis_lazuli");
        register(new MiningTarget(
                "minecraft:redstone",
                "redstone",
                stack -> stack.is(Items.REDSTONE),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile("minecraft:overworld", -59, -53, 12),
                Blocks.REDSTONE_ORE,
                Blocks.DEEPSLATE_REDSTONE_ORE
        ));
        register(new MiningTarget(
                "minecraft:raw_gold",
                "raw gold",
                stack -> stack.is(Items.RAW_GOLD),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile("minecraft:overworld", -32, -16, 12),
                Blocks.GOLD_ORE,
                Blocks.DEEPSLATE_GOLD_ORE
        ));
        registerAlias("minecraft:gold_ore", "minecraft:raw_gold");
        register(new MiningTarget(
                "minecraft:emerald",
                "emerald",
                stack -> stack.is(Items.EMERALD),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile("minecraft:overworld", 80, 160, 12),
                Blocks.EMERALD_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE
        ));
        register(new MiningTarget(
                "minecraft:raw_copper",
                "raw copper",
                stack -> stack.is(Items.RAW_COPPER),
                "stone pickaxe or better",
                ToolRequirement.STONE_PICKAXE,
                new ExplorationProfile("minecraft:overworld", 40, 56, 8),
                Blocks.COPPER_ORE,
                Blocks.DEEPSLATE_COPPER_ORE
        ));
        registerAlias("minecraft:copper_ore", "minecraft:raw_copper");
        register(new MiningTarget(
                "minecraft:quartz",
                "nether quartz",
                stack -> stack.is(Items.QUARTZ),
                "wooden pickaxe or better",
                ToolRequirement.WOODEN_PICKAXE,
                new ExplorationProfile("minecraft:the_nether", 16, 80, 8),
                Blocks.NETHER_QUARTZ_ORE
        ));
    }

    private MiningTargetRegistry() {
    }

    public static Optional<MiningTarget> find(String rawResourceId) {
        String normalized = normalize(rawResourceId);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TARGETS.get(normalized));
    }

    public static String normalize(String rawResourceId) {
        if (rawResourceId == null) {
            return "";
        }
        String trimmed = rawResourceId.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    public static String supportedTargetsSummary() {
        return String.join(", ", TARGETS.keySet());
    }

    private static void register(MiningTarget target) {
        TARGETS.put(target.resourceId(), target);
    }

    private static void registerAlias(String alias, String targetId) {
        MiningTarget target = TARGETS.get(targetId);
        if (target != null) {
            TARGETS.put(alias, target);
        }
    }

    public record MiningTarget(
            String resourceId,
            String displayName,
            Predicate<ItemStack> inventoryMatcher,
            String requiredToolHint,
            ToolRequirement toolRequirement,
            ExplorationProfile explorationProfile,
            Block... sourceBlocks
    ) {
    }

    public record ExplorationProfile(
            String dimension,
            int preferredYMin,
            int preferredYMax,
            int traversalSegmentLength
    ) {
    }

    public enum ToolRequirement {
        NONE,
        WOODEN_PICKAXE,
        STONE_PICKAXE,
        IRON_PICKAXE
    }
}

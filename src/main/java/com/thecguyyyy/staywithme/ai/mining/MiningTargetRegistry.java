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
                new ExplorationProfile(
                        "minecraft:overworld",
                        48,
                        60,
                        4,
                        "Stone is common below most Overworld surfaces; this is a practical shallow tunnel band, not an ore peak."
                ),
                Blocks.STONE,
                Blocks.COBBLESTONE
        ));
        registerAlias("minecraft:cobble", "minecraft:cobblestone");
        registerAlias("minecraft:cobbles", "minecraft:cobblestone");
        register(new MiningTarget(
                "minecraft:coal",
                "coal or charcoal",
                stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL),
                "wooden pickaxe or better",
                ToolRequirement.WOODEN_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        48,
                        64,
                        8,
                        "Coal's reliable underground batch spans Y=0..192 and peaks at Y=96; an additional high-altitude batch spans Y=136..320 where terrain exists."
                ),
                Blocks.COAL_ORE,
                Blocks.DEEPSLATE_COAL_ORE
        ));
        register(new MiningTarget(
                "minecraft:raw_iron",
                "raw iron",
                stack -> stack.is(Items.RAW_IRON),
                "stone pickaxe or better",
                ToolRequirement.STONE_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        12,
                        20,
                        10,
                        "Iron's reliable underground triangular batch spans Y=-24..56 and peaks at Y=16; a much richer high-altitude batch peaks at Y=232 where mountain terrain exists."
                ),
                Blocks.IRON_ORE,
                Blocks.DEEPSLATE_IRON_ORE
        ));
        registerAlias("minecraft:iron_ore", "minecraft:raw_iron");
        registerAlias("minecraft:deepslate_iron_ore", "minecraft:raw_iron");
        registerAlias("minecraft:iron", "minecraft:raw_iron");
        registerAlias("minecraft:raw_irons", "minecraft:raw_iron");
        register(new MiningTarget(
                "minecraft:diamond",
                "diamond",
                stack -> stack.is(Items.DIAMOND),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        -59,
                        -53,
                        14,
                        "Diamond generates below Y=16 and becomes more common toward bedrock; Y=-59..-53 stays near the maximum while avoiding most bedrock."
                ),
                Blocks.DIAMOND_ORE,
                Blocks.DEEPSLATE_DIAMOND_ORE
        ));
        registerAlias("minecraft:diamond_ore", "minecraft:diamond");
        registerAlias("minecraft:deepslate_diamond_ore", "minecraft:diamond");
        registerAlias("minecraft:diamonds", "minecraft:diamond");
        register(new MiningTarget(
                "minecraft:lapis_lazuli",
                "lapis lazuli",
                stack -> stack.is(Items.LAPIS_LAZULI),
                "stone pickaxe or better",
                ToolRequirement.STONE_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        -4,
                        4,
                        10,
                        "Lapis has a triangular exposed batch spanning Y=-32..32 with its peak at Y=0, plus a buried batch spanning Y=-64..64."
                ),
                Blocks.LAPIS_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE
        ));
        registerAlias("minecraft:lapis", "minecraft:lapis_lazuli");
        registerAlias("minecraft:lapis_ore", "minecraft:lapis_lazuli");
        registerAlias("minecraft:deepslate_lapis_ore", "minecraft:lapis_lazuli");
        register(new MiningTarget(
                "minecraft:redstone",
                "redstone",
                stack -> stack.is(Items.REDSTONE),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        -59,
                        -53,
                        12,
                        "Redstone generates uniformly below Y=15 and has an additional lower triangular batch that becomes denser toward bedrock."
                ),
                Blocks.REDSTONE_ORE,
                Blocks.DEEPSLATE_REDSTONE_ORE
        ));
        registerAlias("minecraft:redstone_ore", "minecraft:redstone");
        registerAlias("minecraft:deepslate_redstone_ore", "minecraft:redstone");
        registerAlias("minecraft:redstones", "minecraft:redstone");
        register(new MiningTarget(
                "minecraft:raw_gold",
                "raw gold",
                stack -> stack.is(Items.RAW_GOLD),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        -20,
                        -12,
                        12,
                        "Normal Overworld gold has a triangular batch spanning Y=-64..32 with its peak at Y=-16; badlands biomes also have abundant extra gold at Y=32..256."
                ),
                Blocks.GOLD_ORE,
                Blocks.DEEPSLATE_GOLD_ORE
        ));
        registerAlias("minecraft:gold_ore", "minecraft:raw_gold");
        registerAlias("minecraft:deepslate_gold_ore", "minecraft:raw_gold");
        registerAlias("minecraft:gold", "minecraft:raw_gold");
        registerAlias("minecraft:raw_golds", "minecraft:raw_gold");
        register(new MiningTarget(
                "minecraft:emerald",
                "emerald",
                stack -> stack.is(Items.EMERALD),
                "iron pickaxe or better",
                ToolRequirement.IRON_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        84,
                        104,
                        12,
                        "Emerald generates only in mountain biomes. Its configured distribution peaks at Y=232, while actual terrain-limited sampling is commonly strongest around Y=90."
                ),
                Blocks.EMERALD_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE
        ));
        registerAlias("minecraft:emerald_ore", "minecraft:emerald");
        registerAlias("minecraft:deepslate_emerald_ore", "minecraft:emerald");
        registerAlias("minecraft:emeralds", "minecraft:emerald");
        register(new MiningTarget(
                "minecraft:raw_copper",
                "raw copper",
                stack -> stack.is(Items.RAW_COPPER),
                "stone pickaxe or better",
                ToolRequirement.STONE_PICKAXE,
                new ExplorationProfile(
                        "minecraft:overworld",
                        40,
                        56,
                        8,
                        "Copper has a triangular distribution spanning Y=-16..112 with its peak at Y=48; dripstone caves use larger copper veins."
                ),
                Blocks.COPPER_ORE,
                Blocks.DEEPSLATE_COPPER_ORE
        ));
        registerAlias("minecraft:copper_ore", "minecraft:raw_copper");
        registerAlias("minecraft:deepslate_copper_ore", "minecraft:raw_copper");
        registerAlias("minecraft:copper", "minecraft:raw_copper");
        registerAlias("minecraft:raw_coppers", "minecraft:raw_copper");
        register(new MiningTarget(
                "minecraft:quartz",
                "nether quartz",
                stack -> stack.is(Items.QUARTZ),
                "wooden pickaxe or better",
                ToolRequirement.WOODEN_PICKAXE,
                new ExplorationProfile(
                        "minecraft:the_nether",
                        10,
                        117,
                        8,
                        "Nether quartz is distributed uniformly from Y=10..117 in netherrack, with twice as many placement attempts in basalt deltas."
                ),
                Blocks.NETHER_QUARTZ_ORE
        ));
        registerAlias("minecraft:nether_quartz", "minecraft:quartz");
        registerAlias("minecraft:nether_quartz_ore", "minecraft:quartz");
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
            int traversalSegmentLength,
            String distributionHint
    ) {
    }

    public enum ToolRequirement {
        NONE,
        WOODEN_PICKAXE,
        STONE_PICKAXE,
        IRON_PICKAXE
    }
}

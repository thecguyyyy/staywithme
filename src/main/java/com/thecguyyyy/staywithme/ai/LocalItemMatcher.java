package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.ai.workflow.WorkflowFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

final class LocalItemMatcher {
    private LocalItemMatcher() {
    }

    static Predicate<ItemStack> recipeMatcherFor(String target) {
        String key = inventoryMatcherKey(target);
        return switch (key) {
            case "log", "logs", "wood", "woods" -> stack -> stack.is(ItemTags.LOGS);
            case "plank", "planks" -> stack -> stack.is(ItemTags.PLANKS);
            case "leaf", "leaves" -> stack -> isBlockItemInTag(stack, BlockTags.LEAVES);
            case "crafting_table", "crafting_tables" -> stack -> stack.is(Items.CRAFTING_TABLE);
            case "stick", "sticks" -> stack -> stack.is(Items.STICK);
            case "chest", "chests" -> stack -> stack.is(Items.CHEST);
            case "wooden_axe", "wood_axe", "wooden_axes", "wood_axes" -> stack -> stack.is(Items.WOODEN_AXE);
            case "wooden_pick", "wood_pick", "wood_pickaxe", "wooden_pickaxe", "wood_picks", "wood_pickaxes", "wooden_pickaxes" -> stack -> stack.is(Items.WOODEN_PICKAXE);
            case "cobble", "cobbles", "cobblestone" -> stack -> stack.is(Items.COBBLESTONE);
            case "stone_pick", "stone_pickaxe", "stone_picks", "stone_pickaxes" -> stack -> stack.is(Items.STONE_PICKAXE);
            case "furnace", "furnaces" -> stack -> stack.is(Items.FURNACE);
            case "coal", "coal_ore", "deepslate_coal_ore" -> stack -> !stack.isEmpty()
                    && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL));
            case "charcoal" -> stack -> stack.is(Items.CHARCOAL);
            case "raw_iron", "iron", "iron_ore", "deepslate_iron_ore" -> stack -> stack.is(Items.RAW_IRON);
            case "iron_ingot" -> stack -> stack.is(Items.IRON_INGOT);
            case "iron_pick", "iron_pickaxe", "iron_picks", "iron_pickaxes" -> stack -> stack.is(Items.IRON_PICKAXE);
            case "raw_gold", "gold", "gold_ore", "deepslate_gold_ore" -> stack -> stack.is(Items.RAW_GOLD);
            case "raw_copper", "copper", "copper_ore", "deepslate_copper_ore" -> stack -> stack.is(Items.RAW_COPPER);
            case "diamond", "diamond_ore", "deepslate_diamond_ore" -> stack -> stack.is(Items.DIAMOND);
            case "emerald", "emerald_ore", "deepslate_emerald_ore" -> stack -> stack.is(Items.EMERALD);
            case "redstone", "redstone_ore", "deepslate_redstone_ore" -> stack -> stack.is(Items.REDSTONE);
            case "lapis", "lapis_lazuli", "lapis_ore", "deepslate_lapis_ore" -> stack -> stack.is(Items.LAPIS_LAZULI);
            case "quartz", "nether_quartz", "nether_quartz_ore", "quartz_ore" -> stack -> stack.is(Items.QUARTZ);
            case "gold_pick", "gold_pickaxe", "gold_picks", "gold_pickaxes", "golden_pickaxe", "golden_pickaxes" -> stack -> stack.is(Items.GOLDEN_PICKAXE);
            case "torch", "torches" -> stack -> stack.is(Items.TORCH);
            default -> parseItemId(target)
                    .<Predicate<ItemStack>>map(id -> stack -> !stack.isEmpty()
                            && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    .orElse(stack -> false);
        };
    }

    static String inventoryMatcherKey(String target) {
        if (target == null) {
            return "";
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        int namespace = normalized.indexOf(':');
        if (namespace >= 0 && namespace + 1 < normalized.length()) {
            normalized = normalized.substring(namespace + 1);
        }
        return normalized;
    }

    static boolean isFoodTarget(String target) {
        String key = inventoryMatcherKey(target);
        return "food".equals(key) || "foods".equals(key);
    }

    static boolean isMeatTarget(String target) {
        String key = inventoryMatcherKey(target);
        return "meat".equals(key) || "meats".equals(key);
    }

    static Optional<ResourceLocation> parseItemId(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawTarget.contains(":") ? rawTarget.trim() : "minecraft:" + rawTarget.trim();
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    static Optional<ResourceLocation> craftingPlannerTargetId(String rawTarget) {
        String key = inventoryMatcherKey(rawTarget);
        String alias = switch (key) {
            case "log", "logs", "wood", "woods" -> WorkflowFactory.LOGS;
            case "plank", "planks" -> WorkflowFactory.PLANKS;
            case "stick", "sticks" -> "minecraft:stick";
            case "torch", "torches" -> WorkflowFactory.TORCH;
            case "crafting_table", "crafting_tables" -> WorkflowFactory.CRAFTING_TABLE;
            case "wooden_axe", "wood_axe", "wooden_axes", "wood_axes" -> WorkflowFactory.WOODEN_AXE;
            case "wooden_pick", "wood_pick", "wood_pickaxe", "wooden_pickaxe", "wood_picks", "wood_pickaxes", "wooden_pickaxes" -> WorkflowFactory.WOODEN_PICKAXE;
            case "stone_pick", "stone_pickaxe", "stone_picks", "stone_pickaxes" -> WorkflowFactory.STONE_PICKAXE;
            case "iron_pick", "iron_pickaxe", "iron_picks", "iron_pickaxes" -> WorkflowFactory.IRON_PICKAXE;
            case "furnace", "furnaces" -> WorkflowFactory.FURNACE;
            case "chest", "chests" -> WorkflowFactory.CHEST;
            default -> null;
        };
        if (alias != null) {
            return Optional.ofNullable(ResourceLocation.tryParse(alias));
        }
        return parseItemId(rawTarget);
    }

    private static boolean isBlockItemInTag(ItemStack stack, TagKey<Block> tag) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(tag);
    }
}

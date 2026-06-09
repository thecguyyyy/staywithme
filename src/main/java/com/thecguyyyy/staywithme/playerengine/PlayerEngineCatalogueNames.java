package com.thecguyyyy.staywithme.playerengine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PlayerEngineCatalogueNames {
    private PlayerEngineCatalogueNames() {
    }

    public static String normalize(String rawName) {
        List<String> candidates = candidates(rawName);
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    public static List<String> candidates(String rawName) {
        String base = baseName(rawName);
        if (base.isBlank()) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        String preferred = preferredAlias(base);
        if (!preferred.isBlank()) {
            candidates.add(preferred);
        }
        candidates.add(base);
        simplePluralFallback(base).forEach(candidates::add);
        return new ArrayList<>(candidates);
    }

    private static String baseName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String normalized = rawName.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        int namespace = normalized.indexOf(':');
        if (namespace >= 0 && namespace + 1 < normalized.length()) {
            normalized = normalized.substring(namespace + 1);
        }
        return normalized;
    }

    private static String preferredAlias(String base) {
        return switch (base) {
            case "log", "logs", "wood", "woods" -> "log";
            case "plank", "planks" -> "planks";
            case "stick", "sticks" -> "stick";
            case "torch", "torches" -> "torch";
            case "cobble", "cobbles" -> "cobblestone";
            case "crafting_tables" -> "crafting_table";
            case "chests" -> "chest";
            case "furnaces" -> "furnace";
            case "stripped_log", "stripped_logs" -> "stripped_logs";
            case "iron", "iron_ore", "deepslate_iron_ore" -> "raw_iron";
            case "gold", "gold_ore", "deepslate_gold_ore" -> "raw_gold";
            case "copper", "copper_ore", "deepslate_copper_ore" -> "raw_copper";
            case "coal_ore", "deepslate_coal_ore" -> "coal";
            case "diamond_ore", "deepslate_diamond_ore" -> "diamond";
            case "emerald_ore", "deepslate_emerald_ore" -> "emerald";
            case "redstone_ore", "deepslate_redstone_ore" -> "redstone";
            case "lapis", "lapis_ore", "deepslate_lapis_ore" -> "lapis_lazuli";
            case "nether_quartz", "nether_quartz_ore", "quartz_ore" -> "quartz";
            case "wood_pick", "wood_pickaxe" -> "wooden_pickaxe";
            case "stone_pick" -> "stone_pickaxe";
            case "iron_pick" -> "iron_pickaxe";
            case "gold_pick", "gold_pickaxe" -> "golden_pickaxe";
            case "diamond_pick" -> "diamond_pickaxe";
            case "netherite_pick" -> "netherite_pickaxe";
            default -> suffixAlias(base);
        };
    }

    private static String suffixAlias(String base) {
        if (base.startsWith("stripped_")
                && (base.endsWith("_log") || base.endsWith("_wood") || base.endsWith("_stem") || base.endsWith("_hyphae"))) {
            return "stripped_logs";
        }
        if (base.endsWith("_log") || base.endsWith("_wood") || base.endsWith("_stem") || base.endsWith("_hyphae")) {
            return "log";
        }
        if (base.endsWith("_plank") || base.endsWith("_planks")) {
            return "planks";
        }
        if (base.endsWith("_leaves")) {
            return "leaves";
        }
        if (base.endsWith("_iron_ore")) {
            return "raw_iron";
        }
        if (base.endsWith("_gold_ore")) {
            return "raw_gold";
        }
        if (base.endsWith("_copper_ore")) {
            return "raw_copper";
        }
        if (base.endsWith("_coal_ore")) {
            return "coal";
        }
        if (base.endsWith("_diamond_ore")) {
            return "diamond";
        }
        if (base.endsWith("_emerald_ore")) {
            return "emerald";
        }
        if (base.endsWith("_redstone_ore")) {
            return "redstone";
        }
        if (base.endsWith("_lapis_ore")) {
            return "lapis_lazuli";
        }
        if (base.endsWith("_quartz_ore")) {
            return "quartz";
        }
        return base;
    }

    private static List<String> simplePluralFallback(String base) {
        if (base.endsWith("ies") && base.length() > 3) {
            return List.of(base.substring(0, base.length() - 3) + "y");
        }
        if ((base.endsWith("ches") || base.endsWith("shes") || base.endsWith("xes")) && base.length() > 2) {
            return List.of(base.substring(0, base.length() - 2));
        }
        if (base.endsWith("s") && !base.endsWith("ss") && base.length() > 1) {
            return List.of(base.substring(0, base.length() - 1));
        }
        return List.of();
    }
}

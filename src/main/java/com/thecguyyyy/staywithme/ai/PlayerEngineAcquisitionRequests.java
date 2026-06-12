package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;
import com.thecguyyyy.staywithme.playerengine.PlayerEngineCatalogueNames;

import java.util.Optional;

final class PlayerEngineAcquisitionRequests {
    private PlayerEngineAcquisitionRequests() {
    }

    static Optional<PlayerEngineAcquisitionRequest> from(FriendTask task) {
        if (task == null) {
            return Optional.empty();
        }
        int amount = Math.max(1, task.amount());
        return switch (task.type()) {
            case GET_ITEM, CRAFT_ITEM -> catalogueName(task.target())
                    .map(name -> new PlayerEngineAcquisitionRequest(name, amount));
            case MINE_RESOURCE -> MiningTargetRegistry.find(task.target())
                    .flatMap(target -> catalogueName(target.resourceId()))
                    .or(() -> catalogueName(task.target()))
                    .map(name -> new PlayerEngineAcquisitionRequest(name, amount));
            case MINING_EXPEDITION -> MiningTargetRegistry.find(task.target())
                    .flatMap(target -> catalogueName(target.resourceId()))
                    .map(name -> new PlayerEngineAcquisitionRequest(name, amount));
            case MAKE_CRAFTING_TABLE -> Optional.of(new PlayerEngineAcquisitionRequest("crafting_table", 1));
            case MAKE_STICKS -> Optional.of(new PlayerEngineAcquisitionRequest("stick", Math.max(4, amount)));
            case MAKE_CHEST -> Optional.of(new PlayerEngineAcquisitionRequest("chest", 1));
            case MAKE_WOODEN_AXE -> Optional.of(new PlayerEngineAcquisitionRequest("wooden_axe", 1));
            case MAKE_WOODEN_PICKAXE -> Optional.of(new PlayerEngineAcquisitionRequest("wooden_pickaxe", 1));
            case MAKE_STONE_PICKAXE -> Optional.of(new PlayerEngineAcquisitionRequest("stone_pickaxe", 1));
            case MAKE_FURNACE -> Optional.of(new PlayerEngineAcquisitionRequest("furnace", 1));
            case MAKE_IRON_INGOT -> Optional.of(new PlayerEngineAcquisitionRequest("iron_ingot", 1));
            case MAKE_IRON_PICKAXE -> Optional.of(new PlayerEngineAcquisitionRequest("iron_pickaxe", 1));
            default -> Optional.empty();
        };
    }

    static Optional<String> catalogueName(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return Optional.empty();
        }
        String normalized = PlayerEngineCatalogueNames.normalize(rawTarget);
        return normalized.isBlank() ? Optional.empty() : Optional.of(normalized);
    }
}

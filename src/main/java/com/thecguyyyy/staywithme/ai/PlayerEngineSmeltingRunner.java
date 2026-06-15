package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.Locale;
import java.util.Optional;
import java.util.function.ToIntFunction;

final class PlayerEngineSmeltingRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineCountedTaskRunner countedRunner;
    private final ToIntFunction<String> outputCounter;

    PlayerEngineSmeltingRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineCountedTaskRunner countedRunner,
            ToIntFunction<String> outputCounter
    ) {
        this.body = body;
        this.friend = friend;
        this.countedRunner = countedRunner;
        this.outputCounter = outputCounter;
    }

    void smelt(FriendTask task) {
        Optional<String> normalizedTarget = normalizeOutputTarget(task == null ? null : task.target());
        if (normalizedTarget.isEmpty()) {
            this.friend.getFriendBrain().failTask("I can only smelt iron_ingot, gold_ingot, copper_ingot, or charcoal right now.");
            return;
        }

        String target = normalizedTarget.get();
        int amount = this.amount(task);
        this.countedRunner.run(
                "smelt:" + target,
                amount,
                () -> this.outputCounter.applyAsInt(target) >= amount,
                () -> this.body.hasSmeltItemFinished(target, amount),
                () -> this.body.smeltItem(target, amount),
                "Smelting " + target + " needs PlayerEngine right now; Forge fallback only completes if the output is already carried.",
                "PlayerEngine smelting finished, but I still do not have enough " + target + ".",
                "PlayerEngine smelting did not start for " + target + " x" + amount + ": ",
                "Using PlayerEngine furnace smelting for " + target + " x" + amount + "."
        );
    }

    boolean isSatisfied(FriendTask task) {
        if (task == null) {
            return false;
        }
        Optional<String> target = normalizeOutputTarget(task.target());
        return target.isPresent()
                && this.outputCounter.applyAsInt(target.get()) >= this.amount(task);
    }

    private int amount(FriendTask task) {
        return Math.max(1, task == null || task.amount() <= 0 ? 1 : task.amount());
    }

    private static Optional<String> normalizeOutputTarget(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawTarget.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return Optional.empty();
        }
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return switch (normalized) {
            case "iron", "raw_iron", "iron_ingot" -> Optional.of("iron_ingot");
            case "gold", "raw_gold", "gold_ingot" -> Optional.of("gold_ingot");
            case "copper", "raw_copper", "copper_ingot" -> Optional.of("copper_ingot");
            case "charcoal" -> Optional.of("charcoal");
            default -> Optional.empty();
        };
    }
}

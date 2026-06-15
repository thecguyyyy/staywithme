package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Function;

final class PlayerEngineItemTransferRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineConfirmedTaskRunner confirmedRunner;
    private final PlayerEngineCountedTaskRunner countedRunner;
    private final Function<FriendTask, Optional<ServerPlayer>> taskPlayerResolver;

    PlayerEngineItemTransferRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineConfirmedTaskRunner confirmedRunner,
            PlayerEngineCountedTaskRunner countedRunner,
            Function<FriendTask, Optional<ServerPlayer>> taskPlayerResolver
    ) {
        this.body = body;
        this.friend = friend;
        this.confirmedRunner = confirmedRunner;
        this.countedRunner = countedRunner;
        this.taskPlayerResolver = taskPlayerResolver;
    }

    void giveItemToPlayer(FriendTask task) {
        String target = task == null ? null : task.target();
        if (target == null || target.isBlank()) {
            this.friend.getFriendBrain().failTask("I need an item target before I can give items.");
            return;
        }
        if (LocalItemMatcher.isFoodTarget(target) || LocalItemMatcher.isMeatTarget(target)) {
            this.friend.getFriendBrain().failTask("Giving items needs a concrete item target such as bread, cooked_beef, or torch.");
            return;
        }
        String playerName = task == null ? null : task.playerName();
        if (playerName == null || playerName.isBlank()) {
            Optional<ServerPlayer> owner = this.taskPlayerResolver.apply(task);
            if (owner.isEmpty()) {
                this.friend.getFriendBrain().failTask("I cannot find the player to give items to.");
                return;
            }
            playerName = owner.get().getGameProfile().getName();
        }

        int amount = Math.max(1, task == null || task.amount() <= 0 ? 1 : task.amount());
        String stateName = "give:" + target;
        String targetPlayer = playerName;
        this.confirmedRunner.run(
                stateName,
                amount,
                () -> this.body.hasGiveItemFinished(targetPlayer, target, amount),
                () -> this.body.giveItemToPlayer(targetPlayer, target, amount),
                PlayerEngineConfirmedTaskRunner::callbackFinished,
                "Giving items needs PlayerEngine right now; Forge fallback does not implement inventory handoff.",
                "PlayerEngine give stopped before delivery was confirmed: ",
                "PlayerEngine give did not start for " + target + " x" + amount + ": ",
                "Using PlayerEngine to give " + target + " x" + amount + " to " + targetPlayer + "."
        );
    }

    void pickupDroppedItem(FriendTask task) {
        String target = task == null ? null : task.target();
        int amount = Math.max(1, task == null || task.amount() <= 0 ? 1 : task.amount());
        if (target == null || target.isBlank()) {
            this.friend.getFriendBrain().failTask("I need an item target before I can pick up drops.");
            return;
        }
        this.countedRunner.run(
                "pickup:" + target,
                amount,
                () -> this.isPickupDroppedItemSatisfied(task),
                () -> this.body.hasPickupDroppedItemFinished(target, amount),
                () -> this.body.pickupDroppedItem(target, amount),
                "Picking up targeted drops needs PlayerEngine right now; Forge fallback only auto-picks items within three blocks.",
                "PlayerEngine pickup finished, but I still do not have enough " + target + ".",
                "PlayerEngine pickup did not start for " + target + ": ",
                "Using PlayerEngine to pick up dropped " + target + " x" + amount + "."
        );
    }

    boolean isPickupDroppedItemSatisfied(FriendTask task) {
        if (task == null || task.target() == null || task.target().isBlank()) {
            return false;
        }
        return this.friend.countInventoryItems(LocalItemMatcher.recipeMatcherFor(task.target())) >= Math.max(1, task.amount());
    }

    void depositInventory() {
        this.confirmedRunner.run(
                "deposit_inventory",
                0,
                this.body::hasDepositInventoryFinished,
                this.body::depositInventory,
                PlayerEngineConfirmedTaskRunner::callbackFinishedOrAlreadySatisfied,
                "Depositing inventory needs PlayerEngine right now; Forge fallback does not implement generic container storage.",
                "PlayerEngine deposit stopped before storage was confirmed: ",
                "PlayerEngine deposit did not start: ",
                "Using PlayerEngine to deposit non-tool inventory into a container."
        );
    }
}

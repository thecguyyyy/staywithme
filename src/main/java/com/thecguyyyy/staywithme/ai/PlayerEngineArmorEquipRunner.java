package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.Consumer;

final class PlayerEngineArmorEquipRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetState;
    private final Consumer<String> announcer;
    private final LocalArmorEquipFallback fallback;

    PlayerEngineArmorEquipRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetState,
            Consumer<String> announcer,
            LocalArmorEquipFallback fallback
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetState = resetState;
        this.announcer = announcer;
        this.fallback = fallback;
    }

    void run(String target) {
        if (this.fallback.isEquipped(target)) {
            this.body.stop();
            this.resetState.run();
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (this.taskState.active("equip_armor")
                && this.body.hasArmorEquipmentFinished(target)) {
            this.body.stop();
            this.resetState.run();
            if (this.fallback.equip(target)) {
                this.announcer.accept("I equipped carried armor after PlayerEngine finished.");
                this.friend.getFriendBrain().completeTask();
            } else {
                this.friend.getFriendBrain().failTask("PlayerEngine armor equip finished, but the requested armor is not equipped.");
            }
            return;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            if (this.fallback.equip(target)) {
                this.announcer.accept("Equipped carried armor: " + target + ".");
                this.friend.getFriendBrain().completeTask();
                return;
            }
            this.friend.getFriendBrain().failTask("Equipping armor needs PlayerEngine to obtain missing armor; Forge fallback can only equip armor already in my inventory.");
            return;
        }
        if (!this.body.equipArmor(target)) {
            String status = PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 160);
            this.resetState.run();
            if (this.fallback.equip(target)) {
                this.announcer.accept("PlayerEngine armor equip did not start (" + status + "), so I equipped carried armor locally.");
                this.friend.getFriendBrain().completeTask();
                return;
            }
            this.friend.getFriendBrain().failTask("PlayerEngine armor equip did not start for "
                    + target
                    + ": "
                    + status
                    + ".");
            return;
        }
        this.taskState.startTask(
                "equip_armor",
                1,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to equip armor: " + target + "."
        );
    }
}

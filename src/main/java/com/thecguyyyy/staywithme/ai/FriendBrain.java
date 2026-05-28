package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.embodied.DummyEmbodiedController;
import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.embodied.PlayerEngineEmbodiedController;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

public class FriendBrain {
    private final FriendEntity friend;
    private final LocalBehaviorController localController;

    public FriendBrain(FriendEntity friend) {
        this.friend = friend;
        EmbodiedController embodiedController = createEmbodiedController(friend);
        this.localController = new LocalBehaviorController(friend, embodiedController);
    }

    public void tick() {
        this.localController.tick();
    }

    public void startTask(FriendTask task) {
        this.friend.setCurrentTask(task);
        this.localController.onTaskStarted(task);
    }

    public void stopTask() {
        this.localController.stop();
        this.friend.setCurrentTask(null);
        this.friend.setFriendState(FriendState.IDLE);
    }

    public void completeTask() {
        this.localController.stopTransientTargets();
        this.friend.setCurrentTask(null);
        this.friend.setFriendState(FriendState.IDLE);
    }

    public void failTask(String message) {
        this.localController.stopTransientTargets();
        this.friend.setCurrentTask(null);
        this.friend.setFriendState(FriendState.ERROR);
        this.localController.say(message);
    }

    public void saveControllerState(CompoundTag tag) {
        this.localController.saveControllerState(tag);
    }

    public void loadControllerState(CompoundTag tag) {
        this.localController.loadControllerState(tag);
    }

    public Optional<String> validateRecoveredTask(FriendTask task) {
        return this.localController.validateRecoveredTask(task);
    }

    public void say(String message) {
        this.localController.say(message);
    }

    public String getControllerName() {
        return this.localController.getControllerName();
    }

    public String getControllerStatus() {
        return this.localController.getControllerStatus();
    }

    private static EmbodiedController createEmbodiedController(FriendEntity friend) {
        if (StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get() && IntegrationStatus.isPlayerEngineLoaded()) {
            return new PlayerEngineEmbodiedController(friend);
        }
        return new DummyEmbodiedController(friend);
    }
}

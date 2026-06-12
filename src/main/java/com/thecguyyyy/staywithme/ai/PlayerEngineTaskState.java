package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.Consumer;

final class PlayerEngineTaskState {
    private String name;
    private int amount;
    private boolean announced;

    String name() {
        return this.name;
    }

    int amount() {
        return this.amount;
    }

    boolean active() {
        return this.name != null;
    }

    boolean active(String expectedName) {
        return expectedName != null && expectedName.equals(this.name);
    }

    void startTask(
            String name,
            int amount,
            FriendEntity friend,
            FriendState state,
            Consumer<String> announcer,
            String message
    ) {
        this.start(name, amount);
        friend.setFriendState(state);
        this.announce(announcer, message);
    }

    void announce(Consumer<String> announcer, String message) {
        if (!this.announced) {
            announcer.accept(message);
            this.announced = true;
        }
    }

    private void start(String name, int amount) {
        this.name = name;
        this.amount = amount;
    }

    void reset() {
        this.name = null;
        this.amount = 0;
        this.announced = false;
    }
}

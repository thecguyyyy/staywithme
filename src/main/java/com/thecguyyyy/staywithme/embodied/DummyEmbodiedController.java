package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class DummyEmbodiedController implements EmbodiedController {
    private final FriendEntity friend;

    public DummyEmbodiedController(FriendEntity friend) {
        this.friend = friend;
    }

    @Override
    public String name() {
        return "forge_native";
    }

    @Override
    public String status() {
        return "fallback=forge_native";
    }

    @Override
    public void stop() {
        this.friend.getNavigation().stop();
    }

    @Override
    public void moveTo(double x, double y, double z, double speed) {
        this.friend.getNavigation().moveTo(x, y, z, speed);
    }

    @Override
    public void moveTo(Entity target, double speed) {
        this.friend.getNavigation().moveTo(target, speed);
    }

    @Override
    public void moveToNearby(BlockPos pos, double speed) {
        this.friend.getNavigation().stop();
        this.friend.getMoveControl().setWantedPosition(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                speed
        );
        if (pos.getY() > this.friend.blockPosition().getY()) {
            this.friend.getJumpControl().jump();
        }
    }

    @Override
    public void say(String message) {
        Component component = Component.literal("<" + this.friend.getDisplayName().getString() + "> " + message);
        if (this.friend.getOwnerPlayer().isPresent()) {
            this.friend.getOwnerPlayer().get().sendSystemMessage(component);
            return;
        }
        if (this.friend.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                player.sendSystemMessage(component);
            }
        }
    }

    @Override
    public boolean attack(Entity target) {
        return this.friend.attackAsCompanion(target);
    }

    @Override
    public boolean breakBlock(BlockPos pos) {
        return this.friend.level().destroyBlock(pos, true, this.friend, 512);
    }
}

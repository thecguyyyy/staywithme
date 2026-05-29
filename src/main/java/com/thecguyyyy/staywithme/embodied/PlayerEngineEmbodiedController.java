package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.playerengine.FriendPlayerEngineController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

public class PlayerEngineEmbodiedController implements EmbodiedController {
    private final FriendEntity friend;
    private final DummyEmbodiedController fallback;
    private final FriendPlayerEngineController playerEngine;

    public PlayerEngineEmbodiedController(FriendEntity friend) {
        this.friend = friend;
        this.fallback = new DummyEmbodiedController(friend);
        this.playerEngine = new FriendPlayerEngineController(friend);
    }

    @Override
    public void tick() {
        this.playerEngine.tick();
    }

    @Override
    public String name() {
        return this.playerEngine.isPathingAvailable() ? "forge_native_with_playerengine_bridge" : "forge_native";
    }

    @Override
    public String status() {
        return this.playerEngine.status() + ", nativeMovement=active, peActions=disabled_until_entity_bound";
    }

    @Override
    public void stop() {
        this.playerEngine.stop();
        this.fallback.stop();
    }

    @Override
    public void moveTo(double x, double y, double z, double speed) {
        this.playerEngine.stop();
        this.fallback.moveTo(x, y, z, speed);
    }

    @Override
    public void moveTo(Entity target, double speed) {
        this.playerEngine.stop();
        this.fallback.moveTo(target, speed);
    }

    @Override
    public void say(String message) {
        this.fallback.say(message);
    }

    @Override
    public boolean attack(Entity target) {
        if (target instanceof LivingEntity livingEntity) {
            this.friend.setTarget(livingEntity);
        }
        return this.fallback.attack(target);
    }

    @Override
    public boolean breakBlock(BlockPos pos) {
        return this.fallback.breakBlock(pos);
    }

    @Override
    public boolean mineBlocks(int count, Block... blocks) {
        return false;
    }

    @Override
    public boolean isMining() {
        return false;
    }
}

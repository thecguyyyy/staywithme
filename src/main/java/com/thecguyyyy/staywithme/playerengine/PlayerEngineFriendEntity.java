package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.automaton.api.entity.IHungerManagerProvider;
import com.player2.playerengine.automaton.api.entity.IAutomatone;
import com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInteractionManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class PlayerEngineFriendEntity extends FriendEntity
        implements IAutomatone, IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider, PlayerEngineProviderHost {
    private FriendAutomatoneBridge playerEngineBridge;

    public PlayerEngineFriendEntity(EntityType<? extends FriendEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public LivingEntityInventory getLivingInventory() {
        return this.bridge().getLivingInventory();
    }

    @Override
    public LivingEntityInteractionManager getInteractionManager() {
        return this.bridge().getInteractionManager();
    }

    @Override
    public LivingEntityHungerManager getHungerManager() {
        return this.bridge().getHungerManager();
    }

    @Override
    public void syncPlayerEngineStateFromFriend() {
        this.bridge().syncFromFriend();
    }

    @Override
    public void tickPlayerEngineManagers(ServerLevel level) {
        this.bridge().serverTick();
    }

    @Override
    public void syncPlayerEngineStateToFriend() {
        this.bridge().syncToFriend();
    }

    @Override
    public String playerEngineProviderStatus() {
        return this.bridge().status();
    }

    private FriendAutomatoneBridge bridge() {
        if (this.playerEngineBridge == null) {
            this.playerEngineBridge = new FriendAutomatoneBridge(this);
        }
        return this.playerEngineBridge;
    }
}

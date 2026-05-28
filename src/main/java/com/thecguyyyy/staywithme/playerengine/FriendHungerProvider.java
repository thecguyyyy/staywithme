package com.thecguyyyy.staywithme.playerengine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;

public class FriendHungerProvider {
    private static final float EXHAUSTION_PER_FOOD_POINT = 4.0F;
    private static final int NATURAL_REGEN_FOOD_LEVEL = 18;
    private static final int NATURAL_REGEN_TICKS = 80;

    private int foodLevel = 20;
    private int previousFoodLevel = 20;
    private float saturationLevel = 5.0F;
    private float exhaustion;
    private int tickTimer;

    public void tick(LivingEntity entity) {
        this.previousFoodLevel = this.foodLevel;
        this.applyExhaustion();
        if (entity == null || entity.level().isClientSide) {
            return;
        }

        if (this.canNaturallyRegenerate(entity)) {
            this.tickTimer++;
            if (this.tickTimer >= NATURAL_REGEN_TICKS) {
                entity.heal(1.0F);
                this.addExhaustion(1.5F);
                this.tickTimer = 0;
            }
            return;
        }

        this.tickTimer = 0;
    }

    public int getFoodLevel() {
        return this.foodLevel;
    }

    public int getPreviousFoodLevel() {
        return this.previousFoodLevel;
    }

    public float getSaturationLevel() {
        return this.saturationLevel;
    }

    public float getExhaustion() {
        return this.exhaustion;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = clamp(foodLevel, 0, 20);
    }

    public void setSaturationLevel(float saturationLevel) {
        this.saturationLevel = Math.max(0.0F, Math.min(saturationLevel, 20.0F));
    }

    public void setExhaustion(float exhaustion) {
        this.exhaustion = Math.max(0.0F, exhaustion);
    }

    public void addExhaustion(float amount) {
        this.exhaustion = Math.max(0.0F, this.exhaustion + amount);
    }

    public void save(CompoundTag tag) {
        tag.putInt("FoodLevel", this.foodLevel);
        tag.putInt("PreviousFoodLevel", this.previousFoodLevel);
        tag.putFloat("SaturationLevel", this.saturationLevel);
        tag.putFloat("Exhaustion", this.exhaustion);
        tag.putInt("TickTimer", this.tickTimer);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("FoodLevel")) {
            this.foodLevel = clamp(tag.getInt("FoodLevel"), 0, 20);
        }
        if (tag.contains("PreviousFoodLevel")) {
            this.previousFoodLevel = clamp(tag.getInt("PreviousFoodLevel"), 0, 20);
        }
        if (tag.contains("SaturationLevel")) {
            this.saturationLevel = Math.max(0.0F, Math.min(tag.getFloat("SaturationLevel"), 20.0F));
        }
        if (tag.contains("Exhaustion")) {
            this.exhaustion = Math.max(0.0F, tag.getFloat("Exhaustion"));
        }
        if (tag.contains("TickTimer")) {
            this.tickTimer = Math.max(0, tag.getInt("TickTimer"));
        }
    }

    public String summary() {
        return "food="
                + this.foodLevel
                + ", saturation="
                + Math.round(this.saturationLevel * 10.0F) / 10.0F
                + ", exhaustion="
                + Math.round(this.exhaustion * 10.0F) / 10.0F
                + ", regenTicks="
                + this.tickTimer;
    }

    private void applyExhaustion() {
        while (this.exhaustion >= EXHAUSTION_PER_FOOD_POINT) {
            this.exhaustion -= EXHAUSTION_PER_FOOD_POINT;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(0.0F, this.saturationLevel - 1.0F);
            } else if (this.foodLevel > 0) {
                this.foodLevel--;
            }
        }
    }

    private boolean canNaturallyRegenerate(LivingEntity entity) {
        return entity.isAlive()
                && entity.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                && this.foodLevel >= NATURAL_REGEN_FOOD_LEVEL
                && entity.getHealth() < entity.getMaxHealth();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

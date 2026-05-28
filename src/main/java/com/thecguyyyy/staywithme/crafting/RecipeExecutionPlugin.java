package com.thecguyyyy.staywithme.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;

public interface RecipeExecutionPlugin {
    ResourceLocation id();

    boolean supports(ServerLevel level, Recipe<?> recipe);

    RecipeExecutionResult execute(RecipeExecutionContext context, Recipe<?> recipe);
}

package com.thecguyyyy.staywithme.entity;

import com.thecguyyyy.staywithme.client.CompanionSkinTextures;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public class FriendRenderer extends HumanoidMobRenderer<FriendEntity, HumanoidModel<FriendEntity>> {
    public FriendRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(FriendEntity entity) {
        String skinUrl = entity.getCompanionSkinUrl();
        if (skinUrl != null && !skinUrl.isBlank()) {
            return CompanionSkinTextures.textureFor(entity.getUUID(), skinUrl);
        }
        return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }
}

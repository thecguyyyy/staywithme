package com.thecguyyyy.staywithme.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thecguyyyy.staywithme.client.CompanionSkinTextures;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class FriendRenderer extends HumanoidMobRenderer<FriendEntity, PlayerModel<FriendEntity>> {
    public FriendRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
        this.addLayer(new ArrowLayer<>(context, this));
        this.addLayer(new SpinAttackEffectLayer<>(this, context.getModelSet()));
        this.addLayer(new BeeStingerLayer<>(this));
    }

    @Override
    public void render(
            FriendEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        this.setModelProperties(entity);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public Vec3 getRenderOffset(FriendEntity entity, float partialTicks) {
        return entity.isCrouching() ? new Vec3(0.0D, -0.125D, 0.0D) : super.getRenderOffset(entity, partialTicks);
    }

    @Override
    public ResourceLocation getTextureLocation(FriendEntity entity) {
        String skinUrl = entity.getCompanionSkinUrl();
        if (skinUrl != null && !skinUrl.isBlank()) {
            return CompanionSkinTextures.textureFor(entity.getUUID(), skinUrl);
        }
        return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }

    @Override
    protected void scale(FriendEntity entity, PoseStack poseStack, float partialTicks) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected void setupRotations(
            FriendEntity entity,
            PoseStack poseStack,
            float bob,
            float bodyYRot,
            float partialTicks
    ) {
        float swimAmount = entity.getSwimAmount(partialTicks);
        if (entity.isFallFlying()) {
            super.setupRotations(entity, poseStack, bob, bodyYRot, partialTicks);
            float flyingTicks = (float) entity.getFallFlyingTicks() + partialTicks;
            float flightRotation = Mth.clamp(flyingTicks * flyingTicks / 100.0F, 0.0F, 1.0F);
            if (!entity.isAutoSpinAttack()) {
                poseStack.mulPose(Axis.XP.rotationDegrees(flightRotation * (-90.0F - entity.getXRot())));
            }

            Vec3 view = entity.getViewVector(partialTicks);
            Vec3 motion = entity.getDeltaMovement();
            double motionHorizontal = motion.horizontalDistanceSqr();
            double viewHorizontal = view.horizontalDistanceSqr();
            if (motionHorizontal > 0.0D && viewHorizontal > 0.0D) {
                double dot = (motion.x * view.x + motion.z * view.z) / Math.sqrt(motionHorizontal * viewHorizontal);
                double cross = motion.x * view.z - motion.z * view.x;
                poseStack.mulPose(Axis.YP.rotation((float) (Math.signum(cross) * Math.acos(dot))));
            }
            return;
        }

        if (swimAmount > 0.0F) {
            super.setupRotations(entity, poseStack, bob, bodyYRot, partialTicks);
            float swimRotation = entity.isInWater() ? -90.0F - entity.getXRot() : -90.0F;
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swimAmount, 0.0F, swimRotation)));
            if (entity.isVisuallySwimming()) {
                poseStack.translate(0.0F, -1.0F, 0.3F);
            }
            return;
        }

        super.setupRotations(entity, poseStack, bob, bodyYRot, partialTicks);
    }

    private void setModelProperties(FriendEntity entity) {
        PlayerModel<FriendEntity> model = this.getModel();
        model.setAllVisible(true);
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.crouching = entity.isCrouching();

        HumanoidModel.ArmPose mainArmPose = getArmPose(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offArmPose = getArmPose(entity, InteractionHand.OFF_HAND);
        if (mainArmPose.isTwoHanded()) {
            offArmPose = entity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }

        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainArmPose;
            model.leftArmPose = offArmPose;
        } else {
            model.rightArmPose = offArmPose;
            model.leftArmPose = mainArmPose;
        }
    }

    private static HumanoidModel.ArmPose getArmPose(FriendEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }

        if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            UseAnim useAnim = stack.getUseAnimation();
            if (useAnim == UseAnim.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (useAnim == UseAnim.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (useAnim == UseAnim.SPEAR) {
                return HumanoidModel.ArmPose.THROW_SPEAR;
            }
            if (useAnim == UseAnim.CROSSBOW) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
            if (useAnim == UseAnim.SPYGLASS) {
                return HumanoidModel.ArmPose.SPYGLASS;
            }
            if (useAnim == UseAnim.TOOT_HORN) {
                return HumanoidModel.ArmPose.TOOT_HORN;
            }
            if (useAnim == UseAnim.BRUSH) {
                return HumanoidModel.ArmPose.BRUSH;
            }
        } else if (!entity.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        HumanoidModel.ArmPose forgeArmPose = IClientItemExtensions.of(stack).getArmPose(entity, hand, stack);
        return forgeArmPose == null ? HumanoidModel.ArmPose.ITEM : forgeArmPose;
    }
}

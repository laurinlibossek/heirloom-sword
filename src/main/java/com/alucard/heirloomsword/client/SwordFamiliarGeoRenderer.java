package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.FamiliarState;
import com.alucard.heirloomsword.HeirloomSwordItem;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SwordFamiliarGeoRenderer extends GeoEntityRenderer<SwordFamiliarEntity> {

    // Geo model: blade tip at y=0, pommel at y=48 units -> 3.0 blocks tall, center at 1.5
    private static final float MODEL_CENTER_Y = 1.5f;

    public SwordFamiliarGeoRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SwordFamiliarModel());

        // Blood: normal lighting, alpha = the owner blade's blood level.
        addRenderLayer(new FadingOverlayLayer<>(this, SwordTextures.BLOOD, false,
                (entity, partialTick) -> bloodAlpha(entity)));
        // Runes: full-bright emissive, alpha ramps with charge during CHARGING.
        addRenderLayer(new FadingOverlayLayer<>(this, SwordTextures.RUNES, true,
                SwordFamiliarGeoRenderer::runeAlpha));
    }

    private static float bloodAlpha(SwordFamiliarEntity entity) {
        Player owner = entity.getOwner();
        if (owner == null) return 0f;
        ItemStack stack = HeirloomSwordItem.findInInventory(owner);
        return stack.isEmpty() ? 0f : HeirloomSwordItem.getBlood(stack);
    }

    private static float runeAlpha(SwordFamiliarEntity entity, float partialTick) {
        if (entity.getState() != FamiliarState.CHARGING) return 0f;
        float t = entity.getChargeTimer() + partialTick;
        if (t < 20f) return 0f;                 // first second: no runes
        if (t < 60f) return (t - 20f) / 40f;    // 1s -> 3s: ramp half -> full
        // Held at full charge: gentle pulse (doubles as the charge-complete cue).
        return 0.85f + 0.15f * Mth.sin((entity.tickCount + partialTick) * 0.3f);
    }

    @Override
    public void preRender(PoseStack poseStack, SwordFamiliarEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer vertexConsumer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, animatable, model, bufferSource, vertexConsumer, isReRender,
                partialTick, packedLight, packedOverlay, color);

        // Overlay layers (blood/runes) re-render the model via reRender(), which re-invokes this
        // preRender with isReRender=true. The base pass already applied these positioning transforms
        // and the pose stack keeps them, so re-applying would double them and misalign the overlay.
        if (isReRender) return;

        if (animatable.tickCount < 10 && !animatable.isSkyDropSpawn()) {
            float scale = (animatable.tickCount + partialTick) / 10.0f;
            poseStack.scale(scale, scale, scale);
        }

        float hProgress = animatable.getHorizontalProgress(partialTick);
        // Smoothstep easing: gentle start and settle on the vertical<->horizontal blend
        hProgress = hProgress * hProgress * (3.0f - 2.0f * hProgress);

        boolean spinning = animatable.getState() == FamiliarState.SWEEPING_HOLD
                || (animatable.getState() == FamiliarState.SWEEPING_RELEASE && !animatable.isSweepReturning());

        if (spinning) {
            // Sawblade: continuous yaw spin, blade flattened into the horizontal plane.
            poseStack.mulPose(Axis.YP.rotationDegrees(animatable.getSpinAngle(partialTick)));
            poseStack.mulPose(Axis.XP.rotationDegrees(hProgress * 90.0f));
            poseStack.translate(0, -MODEL_CENTER_Y * hProgress, 0);
            return;
        }

        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));

        if (hProgress > 0.0f) {
            if (animatable.getState() != FamiliarState.BLOCKING && !animatable.isSlashing()) {
                float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
                poseStack.mulPose(Axis.XP.rotationDegrees(hProgress * (90.0f - pitch)));
            }

            poseStack.translate(0, -MODEL_CENTER_Y * hProgress, 0);
        }
    }
}

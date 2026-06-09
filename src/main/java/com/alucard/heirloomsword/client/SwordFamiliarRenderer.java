package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.FamiliarState;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SwordFamiliarRenderer extends EntityRenderer<SwordFamiliarEntity> {

    public SwordFamiliarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SwordFamiliarEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // Render a debug cube representing the sword hitbox
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        AABB box = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());

        // Color varies by state for debug visibility
        FamiliarState state = entity.getState();
        float r, g, b, a = 1.0f;
        switch (state) {
            case LAUNCHING -> { r = 1.0f; g = 0.5f; b = 0.0f; }        // Orange
            case STUCK -> { r = 1.0f; g = 1.0f; b = 0.0f; }            // Yellow
            case RETURNING -> { r = 0.0f; g = 1.0f; b = 0.5f; }        // Cyan-green
            case SWEEPING_HOLD -> { r = 1.0f; g = 0.0f; b = 0.5f; }    // Hot pink
            case SWEEPING_RELEASE -> { r = 0.5f; g = 0.0f; b = 1.0f; } // Violet
            case BLOCKING -> { r = 0.0f; g = 0.8f; b = 1.0f; }         // Cyan (guard)
            default -> { r = 0.6f; g = 0.2f; b = 0.9f; }               // Purple (hovering)
        }

        // Apply / tilt (45° around Z axis) when blocking — diagonal guard stance
        if (state == FamiliarState.BLOCKING) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45.0f));
        }

        LevelRenderer.renderLineBox(poseStack, consumer, box, r, g, b, a);

        // Draw a line toward awareness target if present
        Entity target = entity.getAwarenessTarget();
        if (target != null) {
            Vec3 entityPos = entity.position();
            Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 dir = targetPos.subtract(entityPos).normalize();

            float lineLen = (float) Math.min(entityPos.distanceTo(targetPos), 3.0);
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

            PoseStack.Pose pose = poseStack.last();
            lineConsumer.addVertex(pose.pose(), 0, entity.getBbHeight() * 0.5f, 0)
                    .setColor(1.0f, 0.2f, 0.2f, 1.0f)
                    .setNormal(pose, (float) dir.x, (float) dir.y, (float) dir.z);
            lineConsumer.addVertex(pose.pose(),
                            (float) (dir.x * lineLen),
                            (float) (dir.y * lineLen + entity.getBbHeight() * 0.5f),
                            (float) (dir.z * lineLen))
                    .setColor(1.0f, 0.2f, 0.2f, 1.0f)
                    .setNormal(pose, (float) dir.x, (float) dir.y, (float) dir.z);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SwordFamiliarEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}

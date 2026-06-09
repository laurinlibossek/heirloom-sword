package com.alucard.heirloomsword.client;

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

        // Purple color for the debug cube (matches telekinetic theme)
        float r = 0.6f;
        float g = 0.2f;
        float b = 0.9f;
        float a = 1.0f;

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

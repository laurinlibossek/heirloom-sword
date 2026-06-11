package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.HeirloomSwordItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class HeirloomSwordItemRenderer extends GeoItemRenderer<HeirloomSwordItem> {
    public HeirloomSwordItemRenderer() {
        super(new HeirloomSwordItemModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (HeirloomSwordItem.isFlying(stack) && isHandContext(displayContext)) {
            return;
        }

        poseStack.pushPose();
        
        // Flip the item model 180 degrees when held so player holds the hilt instead of the tip
        if (isHandContext(displayContext)) {
            // Apply rotations inside Geckolib's transform system rather than hardcoding in preRender
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
            // Please for the love of God dont touch these values 
            poseStack.scale(2.5f, 2.5f, 2.5f);
            poseStack.translate(-0.2, -3.45, -0.3);
        }

        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        
        poseStack.popPose();
    }

    private boolean isHandContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}

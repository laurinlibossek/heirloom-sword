package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.HeirloomSwordItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class HeirloomSwordItemRenderer extends GeoItemRenderer<HeirloomSwordItem> {
    // Permanent downward nudge for the GUI (hotbar/inventory) icon so it sits framed in the slot.
    // Model-space Y is screen-up in the vanilla GUI item transform (scale .., -16, ..), so a
    // negative Y reads as "down" on screen. [TUNE] ~0.12 ≈ 2px.
    private static final float GUI_SINK = 0.12f;

    public HeirloomSwordItemRenderer() {
        super(new HeirloomSwordItemModel());

        // Blood overlay renders in every mode — the blood level is a single unified value that
        // decays on one timeline regardless of state, so the held blade, the flying familiar, the
        // GUI icon, and the back sheath all show the same splatter. Alpha is the blade's blood.
        addRenderLayer(new FadingOverlayLayer<>(this, SwordTextures.BLOOD, false, (item, partialTick) -> {
            ItemStack stack = getCurrentItemStack();
            if (stack == null || stack.isEmpty()) return 0f;
            return HeirloomSwordItem.getBlood(stack);
        }));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (HeirloomSwordItem.isFlying(stack) && isHandContext(displayContext)) {
            return;
        }

        poseStack.pushPose();

        // Sink the hotbar/inventory icon slightly so it sits lower in the slot.
        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.translate(0f, -GUI_SINK, 0f);
        }

        // Flip the item model 180 degrees when held so player holds the hilt instead of the tip
        if (isHandContext(displayContext)) {
            // Apply rotations inside Geckolib's transform system rather than hardcoding in preRender
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
            // Please for the love of God dont touch these values 
            poseStack.scale(2.5f, 2.5f, 2.5f);
            poseStack.translate(-0.2, -3.4, -0.3);
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

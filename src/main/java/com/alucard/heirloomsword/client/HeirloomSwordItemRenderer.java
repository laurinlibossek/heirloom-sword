package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.FamiliarState;
import com.alucard.heirloomsword.HeirloomSwordItem;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class HeirloomSwordItemRenderer extends GeoItemRenderer<HeirloomSwordItem> {
    // How far the GUI icon sinks while the blade is STUCK. Model-space Y is screen-up in the
    // vanilla GUI item transform (scale .., -16, ..), so negative Y reads as "down" on screen.
    // [TUNE] ~0.12 ≈ 2px.
    private static final float STUCK_GUI_SINK = 0.12f;

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

        // Sink the hotbar/inventory icon slightly while the familiar is embedded in a block.
        if (displayContext == ItemDisplayContext.GUI && isOwnerFamiliarStuck()) {
            poseStack.translate(0f, -STUCK_GUI_SINK, 0f);
        }

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

    /** True when the local player's own familiar is currently embedded in a block (STUCK). */
    private static boolean isOwnerFamiliarStuck() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        for (SwordFamiliarEntity familiar : player.level().getEntitiesOfClass(
                SwordFamiliarEntity.class, player.getBoundingBox().inflate(64))) {
            if (familiar.getOwnerUUID().map(player.getUUID()::equals).orElse(false)) {
                return familiar.getState() == FamiliarState.STUCK;
            }
        }
        return false;
    }

    private boolean isHandContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}

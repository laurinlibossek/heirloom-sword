package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.FamiliarState;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import javax.annotation.Nullable;

/**
 * Renders the player's bare main arm with state-driven telekinesis poses while
 * flying mode is active (design doc "Hand Gesture System": intent-based gestures).
 * First-person only.
 */
public final class TelekinesisHandRenderer {

    private record HandPose(float x, float y, float z, float rotX, float rotY, float rotZ) {
        static HandPose lerp(HandPose a, HandPose b, float t) {
            return new HandPose(
                    Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z),
                    Mth.lerp(t, a.rotX, b.rotX), Mth.lerp(t, a.rotY, b.rotY), Mth.lerp(t, a.rotZ, b.rotZ));
        }
    }

    // Poses are DELTAS from the vanilla empty-hand arm pose (identity = exact vanilla).
    // Translation is view-space: +x right, +y up, -z toward camera.
    // Rotation pivots the arm around its anchor: rotX = pitch, rotY = yaw, rotZ = roll.
    // [TUNE] every value below
    private static final HandPose RELAXED = new HandPose(0f, 0f, 0f, 0f, 0f, 0f); // hovering, calm — vanilla
    private static final HandPose ALERT   = new HandPose(0f, 0.06f, 0f, -12f, 0f, 0f); // mob in range
    private static final HandPose CHARGE  = new HandPose(-0.05f, 0.12f, 0.05f, -25f, 5f, 0f); // tightened, raised
    private static final HandPose THRUST  = new HandPose(-0.10f, 0.15f, -0.18f, -40f, 8f, 0f); // launch/quick-fire flick
    private static final HandPose SWEEP   = new HandPose(-0.08f, 0.12f, -0.08f, -30f, 15f, 0f); // tracing the view
    private static final HandPose GUARD   = new HandPose(-0.14f, 0.20f, -0.08f, -50f, 10f, 0f); // raised, palm forward
    private static final HandPose RECEIVE = new HandPose(0f, 0.10f, -0.04f, -18f, 0f, 0f); // returning

    private static final float BLEND_SPEED = 0.12f; // [TUNE] per-frame pose blend

    private static HandPose current = RELAXED;

    public static void render(RenderHandEvent event, @Nullable SwordFamiliarEntity familiar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        current = HandPose.lerp(current, poseFor(familiar), BLEND_SPEED);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // View-space pose offset
        poseStack.translate(current.x(), current.y(), current.z());

        // Subtle tremble while charging
        if (familiar != null && familiar.getState() == FamiliarState.CHARGING) {
            float t = (mc.player.tickCount + event.getPartialTick()) * 1.4f; // [TUNE] slightly slower
            poseStack.translate(Math.sin(t * 3.1f) * 0.004f, Math.sin(t * 4.3f) * 0.004f, 0); // [TUNE] reduced amplitude
        }

        // Vanilla anchor, then pose rotations pivot the arm around it
        poseStack.translate(0.64000005F, -0.6F, -0.71999997F);
        poseStack.mulPose(Axis.XP.rotationDegrees(current.rotX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(current.rotY()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(current.rotZ()));

        // Remainder of the vanilla right-arm chain (ItemInHandRenderer.renderPlayerArm,
        // swingProgress = 0, equippedProgress = 0), verbatim
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        poseStack.translate(-1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-135.0F));
        poseStack.translate(5.6F, 0.0F, 0.0F);

        PlayerRenderer playerRenderer =
                (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        playerRenderer.renderRightHand(poseStack, event.getMultiBufferSource(),
                event.getPackedLight(), mc.player);
        poseStack.popPose();
    }

    private static HandPose poseFor(@Nullable SwordFamiliarEntity familiar) {
        if (familiar == null) return RELAXED;
        return switch (familiar.getState()) {
            case HOVERING -> familiar.getAwarenessTarget() != null ? ALERT : RELAXED;
            case CHARGING -> CHARGE;
            case LAUNCHING, QUICK_FIRE, STUCK -> THRUST;
            case SWEEPING_HOLD, SWEEPING_RELEASE -> SWEEP;
            case BLOCKING -> GUARD;
            case RETURNING -> RECEIVE;
            default -> RELAXED;
        };
    }
}

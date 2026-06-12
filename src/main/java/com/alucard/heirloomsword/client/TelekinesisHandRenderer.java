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

    // [TUNE] every value below — lowered + pulled back from face
    private static final HandPose RELAXED = new HandPose(0.55f, -0.75f, -0.40f,  5f,  -5f, 0f); // hovering, calm
    private static final HandPose ALERT   = new HandPose(0.52f, -0.70f, -0.40f, 12f,  -8f, 0f); // mob in range
    private static final HandPose CHARGE  = new HandPose(0.50f, -0.62f, -0.38f, 25f, -12f, 5f); // tightened, raised
    private static final HandPose THRUST  = new HandPose(0.40f, -0.65f, -0.55f, 45f, -18f, 0f); // launch/quick-fire flick
    private static final HandPose SWEEP   = new HandPose(0.38f, -0.60f, -0.45f, 38f, -22f, 0f); // tracing the view
    private static final HandPose GUARD   = new HandPose(0.35f, -0.50f, -0.38f, 55f, -25f, 0f); // raised, palm forward
    private static final HandPose RECEIVE = new HandPose(0.55f, -0.70f, -0.42f, 15f,  -8f, 0f); // returning

    private static final float BLEND_SPEED = 0.12f; // [TUNE] per-frame pose blend

    private static HandPose current = RELAXED;

    public static void render(RenderHandEvent event, @Nullable SwordFamiliarEntity familiar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        current = HandPose.lerp(current, poseFor(familiar), BLEND_SPEED);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(current.x(), current.y(), current.z());
        poseStack.mulPose(Axis.XP.rotationDegrees(current.rotX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(current.rotY()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(current.rotZ()));

        // Subtle tremble while charging
        if (familiar != null && familiar.getState() == FamiliarState.CHARGING) {
            float t = (mc.player.tickCount + event.getPartialTick()) * 1.7f;
            poseStack.translate(Math.sin(t * 3.1f) * 0.01f, Math.sin(t * 4.3f) * 0.01f, 0);
        }

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

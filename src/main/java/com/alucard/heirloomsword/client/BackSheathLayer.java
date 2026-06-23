package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.BackSheathClientState;
import com.alucard.heirloomsword.HeirloomSwordItem;
import com.alucard.heirloomsword.HeirloomSwordMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Renders a sheathed Heirloom Sword diagonally across the player's back (third person and for
 * other players). Off by default; each player opts in with {@code /heirloom show}. The actual
 * render decision (preference on, a NORMAL-mode sword owned, not held) is made server-side and
 * synced per-player into {@link BackSheathClientState}.
 *
 * <p>Implemented as a {@link RenderLayer} rather than a {@code RenderPlayerEvent.Post} handler:
 * inside {@link #render} the pose stack is already in the player model's space (body yaw applied,
 * the {@code -1,-1,1} flip + {@code 0.9375} scale + {@code -1.501} translate done by
 * {@code LivingEntityRenderer}), so {@code body.translateAndRotate} attaches the blade to the
 * torso and it tracks the body's animation for free. The layer never fires in first person
 * because the player body isn't rendered there.
 */
public final class BackSheathLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public BackSheathLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // Server-authoritative: the server computes the full decision (display preference on via
        // /heirloom show, a NORMAL-mode sword owned, and not held) and syncs a per-player flag.
        // Clients can't read other players' inventories, so this flag is the only reliable signal.
        if (!BackSheathClientState.isWearing(player.getUUID())) return;

        // Other clients don't have the actual stack, so we render a generic instance and stamp on
        // the blood level synced from the server — the one per-stack visual a sheathed blade shows.
        ItemStack sheathed = new ItemStack(HeirloomSwordMod.HEIRLOOM_SWORD.get());
        float blood = BackSheathClientState.getBlood(player.getUUID());
        if (blood > 0f) HeirloomSwordItem.setBlood(sheathed, blood);

        poseStack.pushPose();
        // Attach to the torso bone — origin moves to the body pivot and inherits its rotation.
        getParentModel().body.translateAndRotate(poseStack);

        // --- Diagonal back placement. Units are ~blocks; +Y is DOWN here (model space is flipped),
        //     so "up the spine" is negative Y. [TUNE EVERY VALUE — verify visually in F5.] ---
        poseStack.translate(1.0f, 1.3f, 0.18f);        // -X toward player's right, +Y lower, +Z behind back
        poseStack.mulPose(Axis.YP.rotationDegrees(180f)); // lay the flat of the blade against the back
        poseStack.mulPose(Axis.ZP.rotationDegrees(225f)); // diagonal: hilt over right shoulder, tip toward left hip
        poseStack.scale(3f, 3f, 3f);                // size the geo model to span the back

        // FIXED is not a hand context, so HeirloomSwordItemRenderer renders the clean Geo model
        // (no hilt-flip, no flying-mode suppression) and paints the blood overlay from the stack.
        Minecraft.getInstance().getItemRenderer().renderStatic(
                sheathed, ItemDisplayContext.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, player.level(), player.getId());
        poseStack.popPose();
    }
}

/**
 * Adds {@link BackSheathLayer} to both player skin renderers (wide + slim) once renderers are built.
 * Mod bus, client only.
 */
@EventBusSubscriber(modid = HeirloomSwordMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
class BackSheathLayerRegistrar {
    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BackSheathLayer(renderer));
            }
        }
    }
}

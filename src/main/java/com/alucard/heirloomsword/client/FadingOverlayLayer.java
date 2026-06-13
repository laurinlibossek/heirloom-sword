package com.alucard.heirloomsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Re-renders the model with a transparent overlay texture at a code-controlled alpha.
 * Used for the blood splatter (normal lighting) and the charge runes (full-bright emissive).
 * Alpha is supplied per-frame; an alpha <= 0 skips the pass entirely.
 */
public class FadingOverlayLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    @FunctionalInterface
    public interface AlphaFn<T> {
        /** @return overlay opacity 0..1 for this frame. */
        float alpha(T animatable, float partialTick);
    }

    private final ResourceLocation overlayTexture;
    private final boolean emissive;
    private final AlphaFn<T> alphaFn;

    public FadingOverlayLayer(GeoRenderer<T> renderer, ResourceLocation overlayTexture,
                              boolean emissive, AlphaFn<T> alphaFn) {
        super(renderer);
        this.overlayTexture = overlayTexture;
        this.emissive = emissive;
        this.alphaFn = alphaFn;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        float a = alphaFn.alpha(animatable, partialTick);
        if (a <= 0.001f) return;

        int alpha255 = Mth.clamp((int) (a * 255f), 0, 255);
        int colour = (alpha255 << 24) | 0x00FFFFFF; // white tint at the chosen alpha

        RenderType overlay = RenderType.entityTranslucent(overlayTexture);
        int light = emissive ? LightTexture.FULL_BRIGHT : packedLight;

        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, overlay,
                bufferSource.getBuffer(overlay), partialTick, light, packedOverlay, colour);
    }
}

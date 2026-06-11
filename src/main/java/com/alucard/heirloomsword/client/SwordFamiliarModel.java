package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.HeirloomSwordMod;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SwordFamiliarModel extends GeoModel<SwordFamiliarEntity> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "geo/alucard_sword.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "textures/entity/alucard_sword.png");
    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "animations/alucard_sword.animation.json");

    @Override
    public ResourceLocation getModelResource(SwordFamiliarEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SwordFamiliarEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SwordFamiliarEntity entity) {
        return ANIMATIONS;
    }
}

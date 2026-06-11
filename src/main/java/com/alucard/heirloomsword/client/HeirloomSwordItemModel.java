package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.HeirloomSwordItem;
import com.alucard.heirloomsword.HeirloomSwordMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HeirloomSwordItemModel extends GeoModel<HeirloomSwordItem> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "geo/alucard_sword.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "textures/entity/alucard_sword.png");

    @Override
    public ResourceLocation getModelResource(HeirloomSwordItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(HeirloomSwordItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(HeirloomSwordItem item) {
        return null;
    }
}

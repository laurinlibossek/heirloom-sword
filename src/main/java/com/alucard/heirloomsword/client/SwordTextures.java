package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.HeirloomSwordMod;
import net.minecraft.resources.ResourceLocation;

/** Overlay textures composited over the base blade (same UV layout as default.png). */
public final class SwordTextures {
    private SwordTextures() {}

    public static final ResourceLocation BLOOD = rl("alucard_sword_bloodied");
    public static final ResourceLocation RUNES = rl("alucard_sword_runes");

    private static ResourceLocation rl(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                HeirloomSwordMod.MODID, "textures/entity/" + name + ".png");
    }
}

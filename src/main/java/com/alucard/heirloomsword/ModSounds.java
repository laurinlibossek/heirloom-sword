package com.alucard.heirloomsword;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HeirloomSwordMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_MODE_EXIT = SOUNDS.register("sword_mode_exit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_mode_exit")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_AMBIENT = SOUNDS.register("sword_ambient",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_ambient")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_ARRIVES = SOUNDS.register("sword_arrives",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_arrives")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_BLOCK_ARROW = SOUNDS.register("sword_block_arrow",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_block_arrow")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_CHARGING = SOUNDS.register("sword_charging",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_charging")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_GUARDING_START = SOUNDS.register("sword_guarding_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_guarding_start")));
}

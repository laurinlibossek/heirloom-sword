package com.alucard.heirloomsword;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, HeirloomSwordMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SwordMode>> SWORD_MODE =
            DATA_COMPONENTS.register("sword_mode", () -> DataComponentType.<SwordMode>builder()
                    .persistent(SwordMode.CODEC)
                    .networkSynchronized(SwordMode.STREAM_CODEC)
                    .build());
}

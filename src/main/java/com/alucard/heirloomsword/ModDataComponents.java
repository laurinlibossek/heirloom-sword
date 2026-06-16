package com.alucard.heirloomsword;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, HeirloomSwordMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SwordMode>> SWORD_MODE =
            DATA_COMPONENTS.register("sword_mode", () -> DataComponentType.<SwordMode>builder()
                    .persistent(SwordMode.CODEC)
                    .networkSynchronized(SwordMode.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> FAMILIAR_UUID =
            DATA_COMPONENTS.register("familiar_uuid", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    // Cosmetic blood level 0.0–1.0. Persists on the stack and syncs to clients for rendering.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> BLOOD =
            DATA_COMPONENTS.register("blood", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    // True once the sword has ever been activated into flying mode. Drives the one-time awakening.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AWAKENED =
            DATA_COMPONENTS.register("awakened", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());
}

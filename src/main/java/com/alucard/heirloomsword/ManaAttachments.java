package com.alucard.heirloomsword;

import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ManaAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HeirloomSwordMod.MODID);

    // Current mana. Persists across logout; a freshly-respawned player gets the default (full).
    public static final Supplier<AttachmentType<Float>> MANA =
            ATTACHMENT_TYPES.register("mana", () ->
                    AttachmentType.<Float>builder(() -> ManaService.MAX_MANA)
                            .serialize(Codec.FLOAT)
                            .build());

    // Ticks remaining before regen resumes after a spend. Transient (not serialized).
    public static final Supplier<AttachmentType<Integer>> REGEN_DELAY =
            ATTACHMENT_TYPES.register("mana_regen_delay", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .build());

    // Depletion-punishment lockout: ticks during which mana is frozen at 0, regen is held,
    // and all sword inputs (except mode toggle) are rejected. Transient (not serialized).
    public static final Supplier<AttachmentType<Integer>> LOCKOUT =
            ATTACHMENT_TYPES.register("mana_lockout", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .build());

    // Ticks remaining before warp can be used again. Transient (not serialized).
    public static final Supplier<AttachmentType<Integer>> WARP_COOLDOWN =
            ATTACHMENT_TYPES.register("warp_cooldown", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .build());
}

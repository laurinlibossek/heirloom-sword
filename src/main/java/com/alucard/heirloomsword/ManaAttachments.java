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

    // Ticks remaining before the sword mode can be toggled again (either direction).
    // Throttles summon/recall spam — notably the sky-drop entrance. Transient (not serialized).
    public static final Supplier<AttachmentType<Integer>> MODE_SWITCH_COOLDOWN =
            ATTACHMENT_TYPES.register("mode_switch_cooldown", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .build());

    // Player preference: show the sheathed sword on the back (toggled by /heirloom show).
    // Off by default; persists across logout.
    public static final Supplier<AttachmentType<Boolean>> SHOW_BACK_SHEATH =
            ATTACHMENT_TYPES.register("show_back_sheath", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .serialize(Codec.BOOL)
                            .build());

    // Last back-sheath render state broadcast to clients for this player, used to edge-detect
    // changes so we only send a sync packet when it actually flips. Transient (not serialized).
    public static final Supplier<AttachmentType<Boolean>> BACK_SHEATH_SYNCED =
            ATTACHMENT_TYPES.register("back_sheath_synced", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .build());

    // Last back-sheath blood level (quantized 0..20) broadcast for this player, used to edge-detect
    // blood changes so the decaying splatter only re-syncs a handful of times. Transient.
    public static final Supplier<AttachmentType<Integer>> BACK_SHEATH_BLOOD_SYNCED =
            ATTACHMENT_TYPES.register("back_sheath_blood_synced", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .build());

    // Fast-path flag: true while this player's familiar is in BLOCKING. Kept in lockstep with the
    // familiar's state every server tick (SwordFamiliarEntity#serverTick) so the per-damage event
    // handlers can skip the full-level familiar scan unless the player is actually guarding.
    // Server-only and transient (the familiar re-asserts it each tick; default false is always safe).
    public static final Supplier<AttachmentType<Boolean>> IS_BLOCKING =
            ATTACHMENT_TYPES.register("is_blocking", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .build());
}

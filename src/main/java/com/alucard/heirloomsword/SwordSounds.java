package com.alucard.heirloomsword;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** Shared player-feedback cues. */
public final class SwordSounds {
    private SwordSounds() {}

    /**
     * Heavy "thunk" when the familiar embeds in a block (STUCK). Positional and server-broadcast
     * (player arg null, so the owner hears it too) — it plays in the world around the impact point,
     * not at the player. The hotbar gets a separate visual cue since this won't carry over distance.
     */
    public static void playStuckImpact(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z,
                SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * "You can't do that right now" cue — insufficient mana, on cooldown, no valid target, etc.
     */
    public static void playDenied(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                ModSounds.SWORD_MODE_EXIT.value(), SoundSource.PLAYERS, 0.35f, 1.0f);
    }

    // === Phase 10 placeholder cues (vanilla sounds; swap to custom SoundEvents in the audio pass) ===

    /** Flying mode exited to normal (F toggle). */
    public static void playModeExit(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModSounds.SWORD_GUARDING_START.value(), SoundSource.PLAYERS, 0.08f, 1.0f);
    }

    /** Familiar launch. Charged launch is louder and pitched down. Pitch way down, quieter. */
    public static void playLaunch(Level level, double x, double y, double z, boolean charged) {
        level.playSound(null, x, y, z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS,
                charged ? 0.7f : 0.5f, charged ? 0.4f : 0.6f);
    }

    /** Familiar strikes an entity (launch / return / quick-fire contact). */
    public static void playImpact(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    /** Familiar reaches the player at the end of RETURNING. Reuses old experience point pickup sound, very quiet. */
    public static void playReturnArrival(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.15f, 1.3f);
    }

    /** Familiar touchdown landing (arriving state). Quieter than before. */
    public static void playLandingTouchdown(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModSounds.SWORD_ARRIVES.value(), SoundSource.PLAYERS, 0.15f, 1.0f);
    }

    /** SWEEPING_HOLD contact with an entity. Quieter, pitched up. */
    public static void playSweepContact(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.4f, 1.3f);
    }

    /** Sword tossed during sweep release. */
    public static void playSweepRelease(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModSounds.SWORD_SWEEP_RELEASE.value(), SoundSource.PLAYERS, 0.08f, 0.8f);
    }

    /** Guard raised (entering BLOCKING). Very subtle acknowledgement — the sword moved one metre. */
    public static void playGuardRaised(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.08f, 1.3f);
    }

    /** Frontal shield block melee hits. Uses same custom deflect arrow sound. */
    public static void playShieldBlockMelee(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModSounds.SWORD_BLOCK_ARROW.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Projectile deflection off the blade plane. Uses custom block/deflect arrow sound. */
    public static void playShieldDeflectArrow(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModSounds.SWORD_BLOCK_ARROW.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Guard broken (mana exhausted while BLOCKING). Uses chain break. */
    public static void playGuardBreak(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Charge building loop (throttled one-shot). Quieter than before, played at native pitch. */
    public static void playChargeLoop(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModSounds.SWORD_CHARGING.value(), SoundSource.PLAYERS, 0.035f, 1.0f);
    }

    /** Hovering ambient (throttled one-shot). */
    public static void playHoverAmbient(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, ModSounds.SWORD_AMBIENT.value(), SoundSource.PLAYERS, 0.035f, 1.0f);
    }

    /** Familiar death-fall (owner death / despawn). */
    public static void playDeathFall(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    // === Phase 11 tether cues ===

    /** Tether yank begins — the chain snaps taut as the player is pulled. */
    public static void playTetherStart(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    /** Tether pull slams the player through an enemy — heavy metallic impact plus a blast. */
    public static void playTetherSlam(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9f, 0.8f);
        // GENERIC_EXPLODE is a Holder<SoundEvent> in 1.21.1 — the playSound(Holder, ...) overload applies.
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.1f);
    }
}

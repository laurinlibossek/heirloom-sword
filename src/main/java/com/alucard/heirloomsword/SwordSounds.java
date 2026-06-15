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
     * Subtle "you can't do that right now" cue. Reused for any blocked action (insufficient
     * mana, on cooldown, no valid target, …). Placeholder vanilla sound — swapped for a custom
     * sound in the audio pass.
     */
    public static void playDenied(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    // === Phase 10 placeholder cues (vanilla sounds; swap to custom SoundEvents in the audio pass) ===

    /** Flying mode entered (F). */
    public static void playModeEnter(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    /** Flying mode exited to normal (F toggle). Pitched down vs enter. */
    public static void playModeExit(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    /** Familiar launch. Charged launch is louder and pitched down. */
    public static void playLaunch(Level level, double x, double y, double z, boolean charged) {
        level.playSound(null, x, y, z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS,
                charged ? 1.2f : 0.9f, charged ? 0.8f : 1.0f);
    }

    /** Familiar strikes an entity (launch / return / quick-fire contact). */
    public static void playImpact(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    /** Familiar reaches the player at the end of RETURNING. */
    public static void playReturnArrival(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.3f);
    }

    /** SWEEPING_HOLD contact with an entity. */
    public static void playSweepContact(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** Guard raised (entering BLOCKING). Quieter/higher than the block-hit cue. */
    public static void playGuardRaised(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.1f);
    }

    /** Guard broken (mana exhausted while BLOCKING). */
    public static void playGuardBreak(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Charge building loop (throttled one-shot). progress 0..1 raises the pitch. */
    public static void playChargeLoop(Level level, double x, double y, double z, float progress) {
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.5f, 0.6f + progress * 0.8f);
    }

    /** Hovering ambient (throttled one-shot, very quiet) [TUNE: most likely to annoy — easy to disable]. */
    public static void playHoverAmbient(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.2f, 1.4f);
    }

    /** Familiar death-fall (owner death / despawn). */
    public static void playDeathFall(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8f, 0.9f);
    }
}

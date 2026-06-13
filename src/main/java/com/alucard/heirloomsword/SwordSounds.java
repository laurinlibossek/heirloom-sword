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
}

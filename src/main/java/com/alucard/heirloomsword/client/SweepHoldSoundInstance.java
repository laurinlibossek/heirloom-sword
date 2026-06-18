package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.ModSounds;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.alucard.heirloomsword.FamiliarState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public class SweepHoldSoundInstance extends AbstractTickableSoundInstance {
    private final SwordFamiliarEntity familiar;

    public SweepHoldSoundInstance(SwordFamiliarEntity familiar) {
        super(ModSounds.SWORD_SWEEP_HOLD.value(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.familiar = familiar;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.075F;
        this.pitch = 1.0F;
        
        this.x = familiar.getX();
        this.y = familiar.getY();
        this.z = familiar.getZ();
    }

    @Override
    public void tick() {
        if (!this.familiar.isRemoved() && this.familiar.getState() == FamiliarState.SWEEPING_HOLD) {
            this.x = this.familiar.getX();
            this.y = this.familiar.getY();
            this.z = this.familiar.getZ();
        } else {
            this.stop();
        }
    }

    public void stopPlaying() {
        this.stop();
    }
}

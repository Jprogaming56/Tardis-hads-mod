package com.personal.hadsswitch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Player-side half of the HADS music: plays it, then fades it out when told the TARDIS is gone. */
@OnlyIn(Dist.CLIENT)
public final class HadsClient {
    /** How long the fade-out takes once the TARDIS has vanished (ticks, 20 = 1 second). */
    private static final int FADE_TICKS = 30;

    private static FadingSound current;

    private HadsClient() {}

    public static void handle(HadsNetwork.Music m) {
        if (m.start()) {
            if (current != null) {
                current.cut();
            }
            current = new FadingSound(m);
            Minecraft.getInstance().getSoundManager().play(current);
        } else if (current != null) {
            current.beginFade();
        }
    }

    private static final class FadingSound extends AbstractSoundInstance implements TickableSoundInstance {
        private int fadeTick = -1;
        private boolean done;

        FadingSound(HadsNetwork.Music m) {
            super(HadsSwitchMod.HADS_DEMAT_SOUND.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.volume = 1.0f;
            this.pitch = 1.0f;
            this.looping = false;
            this.delay = 0;
            if (m.inside()) {
                // Inside the TARDIS: heard everywhere in the interior, not tied to the exterior's spot.
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
                this.x = 0;
                this.y = 0;
                this.z = 0;
            } else {
                this.x = m.x();
                this.y = m.y();
                this.z = m.z();
            }
        }

        void beginFade() {
            if (fadeTick < 0) {
                fadeTick = 0;
            }
        }

        void cut() {
            done = true;
        }

        @Override
        public boolean isStopped() {
            return done;
        }

        @Override
        public void tick() {
            if (fadeTick < 0) {
                return;
            }
            fadeTick++;
            this.volume = Math.max(0.0f, 1.0f - fadeTick / (float) FADE_TICKS);
            if (fadeTick >= FADE_TICKS) {
                done = true;
            }
        }
    }
}

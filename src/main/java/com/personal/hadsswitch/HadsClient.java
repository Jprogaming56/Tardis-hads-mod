package com.personal.hadsswitch;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Player-side half of the HADS music: plays it, then fades it out when told the TARDIS is gone.
 *
 * The song starts INSIDE the TARDIS. Anyone inside hears the normal song at full volume straight away.
 * Anyone outside hears a muffled, echoey copy of the SAME song (hads_demat_muffled.ogg, made from hads_demat.ogg
 * so it starts at exactly the same point), which crossfades into the clear song while it swells in volume, as if
 * the music is pushing its way out of the box.
 */
@OnlyIn(Dist.CLIENT)
public final class HadsClient {
    /** How long the fade-out takes once the TARDIS has vanished (ticks, 20 = 1 second). */
    private static final int FADE_TICKS = 30;

    /** How long the song takes to come from inside the box to the outside world (ticks, 100 = 5 s, the demat). */
    private static final int OUTWARD_TICKS = 100;
    /** How loud the clear version starts for listeners outside (0 to 1). */
    private static final float OUTSIDE_START_VOLUME = 0.15f;

    private static final List<FadingSound> CURRENT = new ArrayList<>();

    private HadsClient() {}

    public static void handle(HadsNetwork.Music m) {
        if (m.start()) {
            for (FadingSound s : CURRENT) {
                s.cut();
            }
            CURRENT.clear();
            // Always the normal song. Outside listeners also get the muffled copy, started on the same tick so
            // both are at the same point of the song.
            FadingSound clear = new FadingSound(m, HadsSwitchMod.HADS_DEMAT_SOUND.get(), false);
            CURRENT.add(clear);
            if (!m.inside()) {
                CURRENT.add(new FadingSound(m, HadsSwitchMod.HADS_DEMAT_MUFFLED_SOUND.get(), true));
            }
            for (FadingSound s : CURRENT) {
                Minecraft.getInstance().getSoundManager().play(s);
            }
        } else {
            for (FadingSound s : CURRENT) {
                s.beginFade();
            }
        }
    }

    private static final class FadingSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final boolean inside;
        private final boolean muffled;
        private int swellTick = 0;
        private int fadeTick = -1;
        private boolean done;

        FadingSound(HadsNetwork.Music m, SoundEvent event, boolean muffled) {
            super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.inside = m.inside();
            this.muffled = muffled;
            this.volume = startVolume();
            this.pitch = 1.0f;
            this.looping = false;
            this.delay = 0;
            if (inside) {
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

        private float startVolume() {
            if (inside) return 1.0f;
            return muffled ? 1.0f : OUTSIDE_START_VOLUME;
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
            float swell = 1.0f;
            if (!inside && swellTick < OUTWARD_TICKS) {
                swellTick++;
                float t = swellTick / (float) OUTWARD_TICKS;
                float eased = t * t * (3.0f - 2.0f * t); // smoothstep
                if (muffled) {
                    swell = 1.0f - eased; // the muffled copy gives way...
                } else {
                    swell = OUTSIDE_START_VOLUME + (1.0f - OUTSIDE_START_VOLUME) * eased; // ...to the clear one
                }
            } else if (!inside && muffled) {
                swell = 0.0f;
            }

            float fade = 1.0f;
            if (fadeTick >= 0) {
                fadeTick++;
                fade = Math.max(0.0f, 1.0f - fadeTick / (float) FADE_TICKS);
                if (fadeTick >= FADE_TICKS) {
                    done = true;
                }
            }
            this.volume = swell * fade;
        }
    }
}

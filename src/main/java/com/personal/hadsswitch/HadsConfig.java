package com.personal.hadsswitch;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * All the settings you can change without touching code.
 *
 * Two files appear in your game's config folder the first time you run the mod:
 *   hadsswitch-common.toml  - how HADS behaves (on a server, this is the server's file)
 *   hadsswitch-client.toml  - how the music sounds for you
 * Times in the files are in SECONDS and distances in BLOCKS.
 */
public final class HadsConfig {
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec CLIENT;

    // ---- common: triggers ----
    private static final ForgeConfigSpec.BooleanValue TRIGGER_HOSTILES;
    private static final ForgeConfigSpec.BooleanValue TRIGGER_CREEPERS;
    private static final ForgeConfigSpec.BooleanValue TRIGGER_TNT;
    private static final ForgeConfigSpec.BooleanValue TRIGGER_PROJECTILES;
    private static final ForgeConfigSpec.DoubleValue TRIGGER_ENEMY_RADIUS;
    private static final ForgeConfigSpec.DoubleValue TRIGGER_EXPLOSIVE_RADIUS;
    private static final ForgeConfigSpec.DoubleValue TRIGGER_PROJECTILE_RADIUS;
    // ---- common: doors ----
    private static final ForgeConfigSpec.BooleanValue CLOSE_DOORS_FIRST;
    private static final ForgeConfigSpec.DoubleValue DOOR_CLOSE_TIMEOUT;
    // ---- common: timing ----
    private static final ForgeConfigSpec.DoubleValue COOLDOWN_AFTER_TRIP;
    private static final ForgeConfigSpec.DoubleValue COOLDOWN_AFTER_FALSE_ALARM;
    private static final ForgeConfigSpec.DoubleValue MIN_TIME_IN_VORTEX;
    private static final ForgeConfigSpec.DoubleValue CALM_BEFORE_RETURN;
    private static final ForgeConfigSpec.DoubleValue SEARCH_INTERVAL;
    // ---- common: coming back ----
    private static final ForgeConfigSpec.BooleanValue LAND_NEAR_PLAYER;
    private static final ForgeConfigSpec.DoubleValue PLAYER_ENEMY_RADIUS;
    private static final ForgeConfigSpec.DoubleValue PLAYER_EXPLOSIVE_RADIUS;
    private static final ForgeConfigSpec.DoubleValue PLAYER_PROJECTILE_RADIUS;
    private static final ForgeConfigSpec.IntValue LANDING_MIN_DISTANCE;
    private static final ForgeConfigSpec.IntValue LANDING_MAX_DISTANCE;
    private static final ForgeConfigSpec.IntValue LANDING_HALF_WIDTH;
    private static final ForgeConfigSpec.IntValue LANDING_HEIGHT;
    private static final ForgeConfigSpec.IntValue LANDING_MAX_RISE;
    private static final ForgeConfigSpec.IntValue LANDING_MAX_DROP;
    // ---- common: effects ----
    private static final ForgeConfigSpec.BooleanValue PLAY_MUSIC;
    private static final ForgeConfigSpec.DoubleValue MUSIC_RANGE;
    private static final ForgeConfigSpec.BooleanValue SOUND_ALARM;
    private static final ForgeConfigSpec.BooleanValue CUSTOM_ANIMATIONS;

    // ---- client ----
    private static final ForgeConfigSpec.DoubleValue MUSIC_VOLUME;
    private static final ForgeConfigSpec.BooleanValue OUTWARD_INTRO;
    private static final ForgeConfigSpec.DoubleValue OUTWARD_SECONDS;
    private static final ForgeConfigSpec.DoubleValue OUTSIDE_START_VOLUME;
    private static final ForgeConfigSpec.DoubleValue MUSIC_FADE_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("What makes the TARDIS run away (measured from the parked exterior)").push("triggers");
        TRIGGER_HOSTILES = b.comment("Leave when a hostile mob (or an angry neutral one) gets close")
                .define("hostileMobs", true);
        TRIGGER_CREEPERS = b.comment("Leave when a creeper nearby is about to explode")
                .define("swellingCreepers", true);
        TRIGGER_TNT = b.comment("Leave when lit TNT is nearby").define("primedTnt", true);
        TRIGGER_PROJECTILES = b.comment("Leave when arrows etc. fired by something other than a player come close")
                .define("projectiles", true);
        TRIGGER_ENEMY_RADIUS = b.comment("Blocks: how close a hostile mob must be").defineInRange("hostileRadius", 6.0, 0.0, 128.0);
        TRIGGER_EXPLOSIVE_RADIUS = b.comment("Blocks: how close a creeper / TNT must be").defineInRange("explosiveRadius", 8.0, 0.0, 128.0);
        TRIGGER_PROJECTILE_RADIUS = b.comment("Blocks: how close a projectile must be").defineInRange("projectileRadius", 4.0, 0.0, 128.0);
        b.pop();

        b.comment("Doors").push("doors");
        CLOSE_DOORS_FIRST = b.comment(
                        "true  = if the doors are open, slam them shut and then leave (works with doors open)",
                        "false = HADS does nothing while the doors are open (like AiT's own rule)")
                .define("closeDoorsFirst", true);
        DOOR_CLOSE_TIMEOUT = b.comment("Seconds: longest we wait for the doors to finish closing before leaving anyway")
                .defineInRange("closeTimeoutSeconds", 2.0, 0.0, 30.0);
        b.pop();

        b.comment("Timing (all in seconds)").push("timing");
        COOLDOWN_AFTER_TRIP = b.comment("Pause before HADS can trigger again after a full trip")
                .defineInRange("cooldownAfterTripSeconds", 10.0, 0.0, 3600.0);
        COOLDOWN_AFTER_FALSE_ALARM = b.comment("Pause before HADS can trigger again if the TARDIS never actually left (AiT refused)")
                .defineInRange("cooldownAfterFailedSeconds", 30.0, 0.0, 3600.0);
        MIN_TIME_IN_VORTEX = b.comment("Minimum time spent in the vortex, counted from when it has fully vanished")
                .defineInRange("minTimeInVortexSeconds", 15.0, 0.0, 3600.0);
        CALM_BEFORE_RETURN = b.comment("How long it must be quiet around you before the TARDIS comes back")
                .defineInRange("calmBeforeReturnSeconds", 20.0, 0.0, 3600.0);
        SEARCH_INTERVAL = b.comment("How often to look for a safe landing spot while waiting")
                .defineInRange("landingSearchIntervalSeconds", 1.0, 0.25, 60.0);
        b.pop();

        b.comment("Coming back").push("returning");
        LAND_NEAR_PLAYER = b.comment(
                        "true  = come back next to the player who switched HADS on (if they are outside)",
                        "false = always return to the exact spot the TARDIS left from")
                .define("landNearPlayer", true);
        PLAYER_ENEMY_RADIUS = b.comment("Blocks around the player that must be free of hostile mobs").defineInRange("playerHostileRadius", 16.0, 0.0, 128.0);
        PLAYER_EXPLOSIVE_RADIUS = b.comment("Blocks around the player that must be free of creepers / TNT").defineInRange("playerExplosiveRadius", 12.0, 0.0, 128.0);
        PLAYER_PROJECTILE_RADIUS = b.comment("Blocks around the player that must be free of projectiles").defineInRange("playerProjectileRadius", 8.0, 0.0, 128.0);
        LANDING_MIN_DISTANCE = b.comment("Blocks: closest it will land to you").defineInRange("landingMinDistance", 3, 0, 64);
        LANDING_MAX_DISTANCE = b.comment("Blocks: furthest it will land from you").defineInRange("landingMaxDistance", 6, 1, 64);
        LANDING_HALF_WIDTH = b.comment("The landing spot needs a flat floor and clear air this far out to every side (1 = 3x3)")
                .defineInRange("landingHalfWidth", 1, 0, 8);
        LANDING_HEIGHT = b.comment("Clear air needed above the floor (the exterior is 2 blocks tall)")
                .defineInRange("landingHeight", 2, 1, 16);
        LANDING_MAX_RISE = b.comment("How far above you to look for a floor").defineInRange("landingMaxRise", 3, 0, 32);
        LANDING_MAX_DROP = b.comment("How far below you to look for a floor").defineInRange("landingMaxDrop", 4, 0, 32);
        b.pop();

        b.comment("Effects").push("effects");
        PLAY_MUSIC = b.comment("Play Wild Blue Yonder when HADS kicks in").define("playMusic", true);
        MUSIC_RANGE = b.comment("Blocks: players this close to the parked TARDIS hear the music (anyone inside always does)")
                .defineInRange("musicRange", 32.0, 0.0, 256.0);
        SOUND_ALARM = b.comment("Turn on the TARDIS alarm during a HADS trip").define("alarm", true);
        CUSTOM_ANIMATIONS = b.comment("Use the custom flickering departure / arrival animations (false = AiT's normal ones)")
                .define("customAnimations", true);
        b.pop();

        COMMON = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.comment("HADS music, just for you").push("music");
        MUSIC_VOLUME = c.comment("Volume multiplier for the HADS music (0 = silent)").defineInRange("volume", 1.0, 0.0, 1.0);
        OUTWARD_INTRO = c.comment(
                        "true  = when you are OUTSIDE the TARDIS the music starts muffled and quiet, as if coming from inside, and swells out",
                        "false = outside you just hear the normal song at full volume straight away")
                .define("muffledIntroOutside", true);
        OUTWARD_SECONDS = c.comment("Seconds the song takes to come out of the box").defineInRange("outwardSeconds", 5.0, 0.5, 60.0);
        OUTSIDE_START_VOLUME = c.comment("How loud the clear song starts when you are outside (0 to 1)")
                .defineInRange("outsideStartVolume", 0.15, 0.0, 1.0);
        MUSIC_FADE_SECONDS = c.comment("Seconds the music takes to fade out once the TARDIS has vanished")
                .defineInRange("fadeOutSeconds", 1.5, 0.0, 30.0);
        c.pop();
        CLIENT = c.build();
    }

    private HadsConfig() {}

    private static long ticks(double seconds) {
        return Math.round(seconds * 20.0);
    }

    // ---- common getters ----
    public static boolean triggerOnHostiles() { return TRIGGER_HOSTILES.get(); }
    public static boolean triggerOnCreepers() { return TRIGGER_CREEPERS.get(); }
    public static boolean triggerOnTnt() { return TRIGGER_TNT.get(); }
    public static boolean triggerOnProjectiles() { return TRIGGER_PROJECTILES.get(); }
    public static double triggerEnemyRadius() { return TRIGGER_ENEMY_RADIUS.get(); }
    public static double triggerExplosiveRadius() { return TRIGGER_EXPLOSIVE_RADIUS.get(); }
    public static double triggerProjectileRadius() { return TRIGGER_PROJECTILE_RADIUS.get(); }
    public static boolean closeDoorsFirst() { return CLOSE_DOORS_FIRST.get(); }
    public static long doorTimeoutTicks() { return ticks(DOOR_CLOSE_TIMEOUT.get()); }
    public static long cooldownTicks() { return ticks(COOLDOWN_AFTER_TRIP.get()); }
    public static long failedCooldownTicks() { return ticks(COOLDOWN_AFTER_FALSE_ALARM.get()); }
    public static long minVortexTicks() { return ticks(MIN_TIME_IN_VORTEX.get()); }
    public static long calmTicks() { return ticks(CALM_BEFORE_RETURN.get()); }
    public static long searchIntervalTicks() { return Math.max(1, ticks(SEARCH_INTERVAL.get())); }
    public static boolean landNearPlayer() { return LAND_NEAR_PLAYER.get(); }
    public static double playerEnemyRadius() { return PLAYER_ENEMY_RADIUS.get(); }
    public static double playerExplosiveRadius() { return PLAYER_EXPLOSIVE_RADIUS.get(); }
    public static double playerProjectileRadius() { return PLAYER_PROJECTILE_RADIUS.get(); }
    public static int landingMinDistance() { return Math.min(LANDING_MIN_DISTANCE.get(), landingMaxDistance()); }
    public static int landingMaxDistance() { return LANDING_MAX_DISTANCE.get(); }
    public static int landingHalfWidth() { return LANDING_HALF_WIDTH.get(); }
    public static int landingHeight() { return LANDING_HEIGHT.get(); }
    public static int landingMaxRise() { return LANDING_MAX_RISE.get(); }
    public static int landingMaxDrop() { return LANDING_MAX_DROP.get(); }
    public static boolean playMusic() { return PLAY_MUSIC.get(); }
    public static double musicRange() { return MUSIC_RANGE.get(); }
    public static boolean soundAlarm() { return SOUND_ALARM.get(); }
    public static boolean customAnimations() { return CUSTOM_ANIMATIONS.get(); }

    // ---- client getters ----
    public static float clientMusicVolume() { return MUSIC_VOLUME.get().floatValue(); }
    public static boolean clientOutwardIntro() { return OUTWARD_INTRO.get(); }
    public static int clientOutwardTicks() { return (int) Math.max(1, ticks(OUTWARD_SECONDS.get())); }
    public static float clientOutsideStartVolume() { return OUTSIDE_START_VOLUME.get().floatValue(); }
    public static int clientFadeTicks() { return (int) Math.max(1, ticks(MUSIC_FADE_SECONDS.get())); }
}

package com.personal.hadsswitch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Our own HADS: leave when danger shows up at the TARDIS, then wait in the vortex while watching the area
 * around YOU (the player who switched HADS on). Once nothing hostile has been near you for 20 seconds AND
 * a safe landing spot exists a few blocks from you (flat 3x3 floor, clear air above, loaded, dry), the
 * TARDIS materialises there. If there is no safe spot it stays in the vortex and keeps looking. If you are
 * inside a TARDIS (or offline) it watches its old spot instead and returns there.
 *
 * All times are in ticks (20 ticks = 1 second). Change the numbers below to taste.
 */
@Mod.EventBusSubscriber(modid = HadsSwitchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HadsManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How often we look around (ticks). */
    private static final int CHECK_INTERVAL = 10;

    // What makes the TARDIS leave (measured from the parked exterior, in blocks).
    private static final double TRIGGER_ENEMY_RADIUS = 6.0;
    private static final double TRIGGER_EXPLOSIVE_RADIUS = 8.0;
    private static final double TRIGGER_PROJECTILE_RADIUS = 4.0;

    // What keeps it away (measured from YOU, in blocks).
    private static final double PLAYER_ENEMY_RADIUS = 16.0;
    private static final double PLAYER_EXPLOSIVE_RADIUS = 12.0;
    private static final double PLAYER_PROJECTILE_RADIUS = 8.0;

    /** Minimum time in the vortex, counted from when the dematerialise finishes (15 s). */
    private static final long MIN_TIME_IN_VORTEX = 300;
    /** How long it must be quiet around you before the TARDIS comes back (20 s). */
    private static final long CALM_BEFORE_RETURN = 400;
    /** Pause before HADS can trigger again after a trip (10 s). */
    private static final long COOLDOWN_AFTER_TRIP = 200;

    /** Longest we wait for the doors to finish closing before dematerialising anyway (2 s). */
    private static final long DOOR_CLOSE_TIMEOUT = 40;
    /** Loudness of the HADS demat sound (above 1.0 it carries further than normal sounds). */
    private static final float HADS_SOUND_VOLUME = 2.0f;

    // Where the TARDIS is allowed to land near you.
    /** Sideways distance from you (blocks): not on top of you, not far away. */
    private static final int LANDING_MIN_DISTANCE = 3;
    private static final int LANDING_MAX_DISTANCE = 6;
    /** The spot needs a flat floor and clear air this far out to every side (1 = a 3x3 area). */
    private static final int LANDING_HALF_WIDTH = 1;
    /** Clear air needed above the floor (the exterior is 2 blocks tall). */
    private static final int LANDING_HEIGHT = 2;
    /** How far above / below you to look for a floor (blocks). */
    private static final int LANDING_MAX_RISE = 3;
    private static final int LANDING_MAX_DROP = 4;
    /** How often to search for a landing spot while waiting in the vortex (ticks, 20 = 1 s). */
    private static final long SEARCH_INTERVAL = 20;

    /** Sideways offsets to try, closest to you first. */
    private static final List<int[]> LANDING_OFFSETS = buildLandingOffsets();

    private static final class Trip {
        Object homePos;
        ServerLevel homeWorld;
        BlockPos homeBlock;
        Object originalDestination;
        UUID playerId;
        long startedAt;
        long flightSeenAt = -1;
        long calmSince = -1;
        long rematCalledAt = -1;
        long lastSearchAt = -1;
        boolean doorsClosing;
        boolean lastFollowing;
    }

    private record Scan(ServerLevel world, BlockPos pos, boolean followingPlayer) {}

    private static final Map<UUID, Trip> TRIPS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Set<UUID> FAILED = new HashSet<>();

    private HadsManager() {}

    /** Called by the block. Returns the new state, or null if the level is not a TARDIS interior. */
    public static Boolean toggle(ServerLevel interior, Player player) throws Exception {
        UUID id = AitBridge.tardisIdOf(interior);
        if (id == null) return null;
        MinecraftServer server = interior.getServer();
        HadsData data = HadsData.get(server);
        boolean next = !data.isEnabled(id);
        data.setEnabled(id, next);
        if (next) {
            data.setOwner(id, player.getUUID());
        }
        FAILED.remove(id);
        Object tardis = AitBridge.tardis(server, id);
        if (tardis != null) {
            AitBridge.setBuiltInHads(tardis, false);
        }
        return next;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) return;
        long now = server.overworld().getGameTime();
        if (now % CHECK_INTERVAL != 0) return;

        HadsData data = HadsData.get(server);
        Set<UUID> ids = new HashSet<>(data.enabledIds());
        ids.addAll(TRIPS.keySet());
        for (UUID id : ids) {
            if (FAILED.contains(id)) continue;
            try {
                process(server, data, id, now);
            } catch (Throwable t) {
                LOGGER.error("HADS Switch stopped handling TARDIS {} because of an error", id, t);
                FAILED.add(id);
                TRIPS.remove(id);
            }
        }
    }

    private static void process(MinecraftServer server, HadsData data, UUID id, long now) throws Exception {
        boolean enabled = data.isEnabled(id);
        Trip trip = TRIPS.get(id);
        Object tardis = AitBridge.tardis(server, id);
        if (tardis == null) {
            TRIPS.remove(id);
            return;
        }
        if (enabled) {
            AitBridge.setBuiltInHads(tardis, false);
        }
        Object travel = AitBridge.travel(tardis);
        String state = AitBridge.stateName(travel);

        if (trip == null) {
            if (!enabled || !"LANDED".equals(state)) return;
            if (COOLDOWN.getOrDefault(id, 0L) > now) return;
            Object posObj = AitBridge.position(travel);
            ServerLevel world = AitBridge.worldOf(posObj);
            BlockPos block = AitBridge.blockOf(posObj);
            if (world == null || block == null) return;
            if (!isDanger(world, block, TRIGGER_ENEMY_RADIUS, TRIGGER_EXPLOSIVE_RADIUS, TRIGGER_PROJECTILE_RADIUS)) {
                return;
            }

            Trip t = new Trip();
            t.homePos = posObj;
            t.homeWorld = world;
            t.homeBlock = block;
            t.originalDestination = AitBridge.destination(travel);
            t.startedAt = now;
            t.playerId = data.getOwner(id);
            if (t.playerId == null) {
                Player near = world.getNearestPlayer(block.getX(), block.getY(), block.getZ(), 48, false);
                if (near != null) t.playerId = near.getUUID();
            }
            // AiT refuses to dematerialise with the doors open, so slam them shut first.
            Object door = AitBridge.door(tardis);
            if (AitBridge.isDoorOpen(door)) {
                AitBridge.closeDoors(door);
                t.doorsClosing = true;
                TRIPS.put(id, t);
                return;
            }
            startHadsDemat(server, id, tardis, travel, t, now);
            return;
        }

        if (trip.doorsClosing && !"LANDED".equals(state)) {
            TRIPS.remove(id); // the TARDIS took off some other way while the doors were closing
            return;
        }
        if (trip.doorsClosing) {
            if (!enabled) {
                TRIPS.remove(id); // HADS was switched off while the doors were closing
                return;
            }
            Object door = AitBridge.door(tardis);
            if (AitBridge.isDoorOpen(door)) {
                AitBridge.closeDoors(door); // someone reopened them: shut them again
            }
            if (AitBridge.isDoorClosed(door) || now - trip.startedAt >= DOOR_CLOSE_TIMEOUT) {
                startHadsDemat(server, id, tardis, travel, trip, now);
            }
            return;
        }

        switch (state) {
            case "FLIGHT" -> {
                if (trip.flightSeenAt < 0) trip.flightSeenAt = now;

                Scan scan = scanTarget(server, trip);
                if (scan.followingPlayer() != trip.lastFollowing) {
                    trip.lastFollowing = scan.followingPlayer();
                    trip.calmSince = -1; // switched between watching you and watching the old spot
                }
                boolean danger = enabled && (scan.followingPlayer()
                        ? isDanger(scan.world(), scan.pos(),
                                PLAYER_ENEMY_RADIUS, PLAYER_EXPLOSIVE_RADIUS, PLAYER_PROJECTILE_RADIUS)
                        : isDanger(scan.world(), scan.pos(),
                                TRIGGER_ENEMY_RADIUS, TRIGGER_EXPLOSIVE_RADIUS, TRIGGER_PROJECTILE_RADIUS));
                if (danger) {
                    trip.calmSince = -1;
                } else if (trip.calmSince < 0) {
                    trip.calmSince = now;
                }

                boolean ready = !enabled
                        || (now - trip.flightSeenAt >= MIN_TIME_IN_VORTEX
                        && trip.calmSince >= 0
                        && now - trip.calmSince >= CALM_BEFORE_RETURN);
                boolean canTry = trip.rematCalledAt < 0 || now - trip.rematCalledAt >= 200;
                if (ready && canTry && now - trip.lastSearchAt >= SEARCH_INTERVAL) {
                    trip.lastSearchAt = now;
                    Object dest = trip.homePos;
                    boolean land = true;
                    if (scan.followingPlayer()) {
                        BlockPos spot = findLanding(scan.world(), scan.pos());
                        if (spot != null) {
                            dest = AitBridge.makePos(scan.world(), spot, AitBridge.rotationOf(trip.homePos));
                        } else if (enabled) {
                            land = false; // no safe spot near you yet: stay in the vortex and keep looking
                        } // HADS switched off: just go back to the old spot instead of getting stuck
                    }
                    if (land) {
                        AitBridge.setDestination(travel, dest);
                        AitBridge.rematerialize(travel);
                        trip.rematCalledAt = now;
                    }
                }
            }
            case "LANDED" -> {
                if (now - trip.startedAt < 60) return; // dematerialise hasn't started yet
                boolean wentAnywhere = trip.flightSeenAt >= 0;
                try {
                    AitBridge.setDestination(travel, trip.originalDestination);
                } catch (Exception ignored) {
                    // not important
                }
                AitBridge.alarm(tardis, false);
                TRIPS.remove(id);
                COOLDOWN.put(id, now + (wentAnywhere ? COOLDOWN_AFTER_TRIP : 600));
            }
            default -> {
                // DEMAT or MAT: let the animation play
            }
        }
    }

    /**
     * The HADS dematerialise: the doors are shut by now. Plays the HADS sound (only here, so normal demats and
     * the return trip stay silent) and starts the alarm. If AiT still refuses, give up quietly and retry later.
     */
    private static void startHadsDemat(MinecraftServer server, UUID id, Object tardis, Object travel,
                                       Trip t, long now) throws Exception {
        AitBridge.setDestination(travel, t.homePos); // safe default: come back to the same spot
        AitBridge.dematerialize(travel);
        if ("LANDED".equals(AitBridge.stateName(travel))) {
            try {
                AitBridge.setDestination(travel, t.originalDestination);
            } catch (Exception ignored) {
                // not important
            }
            TRIPS.remove(id);
            COOLDOWN.put(id, now + 600);
            return;
        }
        t.doorsClosing = false;
        t.startedAt = now;
        AitBridge.alarm(tardis, true);
        playHadsSound(server, id, t);
        TRIPS.put(id, t);
    }

    /** Plays the HADS sound at the parked exterior and to anyone standing inside that TARDIS. */
    private static void playHadsSound(MinecraftServer server, UUID id, Trip t) {
        SoundEvent sound = HadsSwitchMod.HADS_DEMAT_SOUND.get();
        t.homeWorld.playSound(null, t.homeBlock, sound, SoundSource.BLOCKS, HADS_SOUND_VOLUME, 1.0f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                if (id.equals(AitBridge.tardisIdOf(p.serverLevel()))) {
                    p.playNotifySound(sound, SoundSource.BLOCKS, HADS_SOUND_VOLUME, 1.0f);
                }
            } catch (Exception ignored) {
                // not inside a TARDIS
            }
        }
    }

    /** Where to look for danger: around the player if they are out in the world, otherwise the old spot. */
    private static Scan scanTarget(MinecraftServer server, Trip trip) {
        if (trip.playerId != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(trip.playerId);
            if (p != null && p.isAlive() && !AitBridge.isInterior(p.serverLevel())) {
                return new Scan(p.serverLevel(), p.blockPosition(), true);
            }
        }
        return new Scan(trip.homeWorld, trip.homeBlock, false);
    }

    private static List<int[]> buildLandingOffsets() {
        List<int[]> list = new ArrayList<>();
        for (int dx = -LANDING_MAX_DISTANCE; dx <= LANDING_MAX_DISTANCE; dx++) {
            for (int dz = -LANDING_MAX_DISTANCE; dz <= LANDING_MAX_DISTANCE; dz++) {
                int far = Math.max(Math.abs(dx), Math.abs(dz));
                if (far < LANDING_MIN_DISTANCE || far > LANDING_MAX_DISTANCE) continue;
                list.add(new int[] {dx, dz});
            }
        }
        list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return list;
    }

    /**
     * Looks for a safe spot a few blocks from the player, closest first (and closest to your height first).
     * Returns null if there isn't one, in which case the TARDIS keeps waiting in the vortex.
     */
    private static BlockPos findLanding(ServerLevel world, BlockPos center) {
        int maxK = Math.max(LANDING_MAX_RISE, LANDING_MAX_DROP);
        for (int[] o : LANDING_OFFSETS) {
            int x = center.getX() + o[0];
            int z = center.getZ() + o[1];
            for (int k = 0; k <= maxK; k++) {
                if (k <= LANDING_MAX_RISE) {
                    BlockPos p = new BlockPos(x, center.getY() + k, z);
                    if (isSafeLanding(world, p)) return p;
                }
                if (k > 0 && k <= LANDING_MAX_DROP) {
                    BlockPos p = new BlockPos(x, center.getY() - k, z);
                    if (isSafeLanding(world, p)) return p;
                }
            }
        }
        return null;
    }

    /**
     * p is the block the TARDIS would stand in. The whole area around it must be loaded, inside the world
     * border, have a flat solid dry floor, and be free of blocks, liquids and fire up to the exterior's height.
     */
    private static boolean isSafeLanding(ServerLevel world, BlockPos p) {
        BlockPos lo = p.offset(-LANDING_HALF_WIDTH, -1, -LANDING_HALF_WIDTH);
        BlockPos hi = p.offset(LANDING_HALF_WIDTH, LANDING_HEIGHT - 1, LANDING_HALF_WIDTH);
        if (world.isOutsideBuildHeight(lo) || world.isOutsideBuildHeight(hi)) return false;
        if (!world.getWorldBorder().isWithinBounds(lo) || !world.getWorldBorder().isWithinBounds(hi)) return false;
        if (!world.hasChunksAt(lo, hi)) return false;

        int floorY = p.getY() - 1;
        for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
            BlockState s = world.getBlockState(q);
            if (!s.getFluidState().isEmpty()) return false;
            if (q.getY() == floorY) {
                if (!s.isFaceSturdy(world, q, Direction.UP) || s.is(Blocks.MAGMA_BLOCK)) return false;
            } else if (!s.getCollisionShape(world, q).isEmpty() || s.is(BlockTags.FIRE)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDanger(ServerLevel world, BlockPos pos,
                                    double enemyRadius, double explosiveRadius, double projectileRadius) {
        AABB base = new AABB(pos);

        if (!world.getEntitiesOfClass(LivingEntity.class, base.inflate(enemyRadius),
                e -> e.isAlive() && e instanceof Enemy && !(e instanceof NeutralMob n && !n.isAngry())).isEmpty()) {
            return true;
        }
        if (!world.getEntitiesOfClass(Creeper.class, base.inflate(explosiveRadius),
                c -> c.isAlive() && c.getSwellDir() > 0).isEmpty()) {
            return true;
        }
        if (!world.getEntitiesOfClass(PrimedTnt.class, base.inflate(explosiveRadius), t -> true).isEmpty()) {
            return true;
        }
        return !world.getEntitiesOfClass(Projectile.class, base.inflate(projectileRadius),
                p -> !(p.getOwner() instanceof Player)).isEmpty();
    }
}

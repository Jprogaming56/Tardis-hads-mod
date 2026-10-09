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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;
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
 * The custom HADS animations (wby_demat for leaving, wby_mat for arriving) are swapped in only at the moment
 * HADS itself asks the TARDIS to dematerialise / rematerialise, and swapped back out the moment that animation
 * has finished (or immediately if AiT refuses), so normal flying never sees them.
 *
 * All times are in ticks (20 ticks = 1 second). Change the numbers below to taste.
 */
@Mod.EventBusSubscriber(modid = HadsSwitchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HadsManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How often we look around (ticks). */
    private static final int CHECK_INTERVAL = 10;

    // All other numbers (radii, times, landing rules...) live in HadsConfig / config/hadsswitch-common.toml.

    /** The custom arrival animation, played only when coming back from a HADS trip. */
    private static final ResourceLocation HADS_MAT_ANIMATION = new ResourceLocation(HadsSwitchMod.MOD_ID, "wby_mat");
    /** The custom departure animation, played only when HADS itself makes the TARDIS dematerialise. */
    private static final ResourceLocation HADS_DEMAT_ANIMATION = new ResourceLocation(HadsSwitchMod.MOD_ID, "wby_demat");

    /** Sideways offsets to try, closest to you first (rebuilt if the config distances change). */
    private static List<int[]> landingOffsets;
    private static int offsetsMin = -1;
    private static int offsetsMax = -1;

    private static List<int[]> landingOffsets() {
        int min = HadsConfig.landingMinDistance();
        int max = HadsConfig.landingMaxDistance();
        if (landingOffsets == null || min != offsetsMin || max != offsetsMax) {
            offsetsMin = min;
            offsetsMax = max;
            landingOffsets = buildLandingOffsets(min, max);
        }
        return landingOffsets;
    }

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
        final Set<UUID> listeners = new HashSet<>(); // players currently hearing the HADS music
        boolean doorsClosing;
        boolean reopenDoors;
        int reopenTries;
        boolean lastFollowing;
        boolean matAnimationSwapped;
        boolean matLogged;
        int matForced;
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
        ids.addAll(data.pendingRestoreIds());
        for (UUID id : ids) {
            if (FAILED.contains(id)) continue;
            try {
                process(server, data, id, now);
            } catch (Throwable t) {
                LOGGER.error("HADS Switch stopped handling TARDIS {} because of an error", id, t);
                FAILED.add(id);
                TRIPS.remove(id);
                try {
                    restoreAllAnimations(server, data, id);
                } catch (Throwable ignored) {
                    // best effort
                }
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

        if (trip == null && data.hasPendingRestore(id)) {
            restoreAllAnimations(server, data, id); // left over from an interrupted trip
        }

        if (trip == null) {
            if (!enabled || !"LANDED".equals(state)) return;
            if (COOLDOWN.getOrDefault(id, 0L) > now) return;
            Object posObj = AitBridge.position(travel);
            ServerLevel world = AitBridge.worldOf(posObj);
            BlockPos block = AitBridge.blockOf(posObj);
            if (world == null || block == null) return;
            if (!isDanger(world, block, HadsConfig.triggerEnemyRadius(), HadsConfig.triggerExplosiveRadius(), HadsConfig.triggerProjectileRadius())) {
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
                if (!HadsConfig.closeDoorsFirst() && !HadsConfig.keepDoorsOpen()) return; // doors open and we may not shut them: stay put
                t.reopenDoors = HadsConfig.keepDoorsOpen();
                AitBridge.closeDoors(door);
                t.doorsClosing = true;
                TRIPS.put(id, t);
                return;
            }
            startHadsDemat(server, data, id, tardis, travel, t, now);
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
            if (AitBridge.isDoorClosed(door) || now - trip.startedAt >= HadsConfig.doorTimeoutTicks()) {
                startHadsDemat(server, data, id, tardis, travel, trip, now);
            }
            return;
        }

        switch (state) {
            case "FLIGHT" -> {
                if (trip.flightSeenAt < 0) {
                    trip.flightSeenAt = now;
                    fadeMusic(server, trip); // the TARDIS has fully vanished: fade the music out
                    restoreAnimation(server, data, id, "DEMAT", HADS_DEMAT_ANIMATION); // HADS demat is over
                }
                if (trip.matAnimationSwapped) {
                    // We asked AiT to land on the previous check, yet it is still in flight, so it refused.
                    // Put the normal arrival animation back right now so nothing else can ever use ours.
                    restoreAnimation(server, data, id, "MAT", HADS_MAT_ANIMATION);
                    trip.matAnimationSwapped = false;
                }

                Scan scan = scanTarget(server, trip);
                if (scan.followingPlayer() != trip.lastFollowing) {
                    trip.lastFollowing = scan.followingPlayer();
                    trip.calmSince = -1; // switched between watching you and watching the old spot
                }
                boolean danger = enabled && (scan.followingPlayer()
                        ? isDanger(scan.world(), scan.pos(),
                                HadsConfig.playerEnemyRadius(), HadsConfig.playerExplosiveRadius(), HadsConfig.playerProjectileRadius())
                        : isDanger(scan.world(), scan.pos(),
                                HadsConfig.triggerEnemyRadius(), HadsConfig.triggerExplosiveRadius(), HadsConfig.triggerProjectileRadius()));
                if (danger) {
                    trip.calmSince = -1;
                } else if (trip.calmSince < 0) {
                    trip.calmSince = now;
                }

                boolean ready = !enabled
                        || (now - trip.flightSeenAt >= HadsConfig.minVortexTicks()
                        && trip.calmSince >= 0
                        && now - trip.calmSince >= HadsConfig.calmTicks());
                boolean canTry = trip.rematCalledAt < 0 || now - trip.rematCalledAt >= 200;
                if (ready && canTry && now - trip.lastSearchAt >= HadsConfig.searchIntervalTicks()) {
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
                        // Only now, at the exact moment HADS asks to land, does our animation go in.
                        trip.matAnimationSwapped = swapAnimation(data, id, travel, "MAT", HADS_MAT_ANIMATION);
                        boolean accepted = AitAnimation.rematerialize(travel);
                        trip.rematCalledAt = now;
                        LOGGER.info("HADS Switch: asked AiT to rematerialise: accepted={}, state now {}, MAT animation set to {}",
                                accepted, AitBridge.stateName(travel), AitAnimation.animationIdFor(travel, "MAT"));
                        if (!accepted && trip.matAnimationSwapped) {
                            // AiT said no: nothing is landing, so take our animation straight back out
                            restoreAnimation(server, data, id, "MAT", HADS_MAT_ANIMATION);
                            trip.matAnimationSwapped = false;
                        }
                    }
                }
            }
            case "LANDED" -> {
                if (now - trip.startedAt < 60) return; // dematerialise hasn't started yet
                fadeMusic(server, trip);
                boolean wentAnywhere = trip.flightSeenAt >= 0;
                try {
                    AitBridge.setDestination(travel, trip.originalDestination);
                } catch (Exception ignored) {
                    // not important
                }
                if (HadsConfig.soundAlarm()) AitBridge.alarm(tardis, false);
                TRIPS.remove(id);
                restoreAllAnimations(server, data, id);
                COOLDOWN.put(id, now + (wentAnywhere ? HadsConfig.cooldownTicks() : HadsConfig.failedCooldownTicks()));
            }
            case "MAT" -> {
                // This only runs for a HADS trip. Make sure the animation really playing is ours; if AiT picked
                // another one, replace the running animation with ours (AiT's own override, synced to players).
                String running = AitAnimation.runningAnimationId(travel);
                String ours = HADS_MAT_ANIMATION.toString();
                if (!trip.matLogged) {
                    trip.matLogged = true;
                    LOGGER.info("HADS Switch: arrival started, running animation = {}, MAT animation set to {}",
                            running, AitAnimation.animationIdFor(travel, "MAT"));
                }
                if (HadsConfig.customAnimations() && running != null && !"?".equals(running) && !ours.equals(running)
                        && trip.matForced < 3 && AitAnimation.isRegistered(HADS_MAT_ANIMATION)) {
                    trip.matForced++;
                    boolean ok = AitAnimation.forceAnimation(travel, HADS_MAT_ANIMATION);
                    LOGGER.warn("HADS Switch: AiT was playing {} for the arrival, forced {} (success={})",
                            running, ours, ok);
                }
            }
            default -> {
                // DEMAT: let the animation play. If AiT shut the doors again, reopen them (a few tries only).
                if (trip.reopenDoors && trip.reopenTries < 3 && AitBridge.isDoorClosed(AitBridge.door(tardis))) {
                    trip.reopenTries++;
                    reopenDoorsIfWanted(tardis, trip);
                }
            }
        }
    }

    /**
     * The HADS dematerialise: the doors are shut by now. Plays the HADS sound (only here, so normal demats and
     * the return trip stay silent) and starts the alarm. If AiT still refuses, give up quietly and retry later.
     */
    private static void startHadsDemat(MinecraftServer server, HadsData data, UUID id, Object tardis, Object travel,
                                       Trip t, long now) throws Exception {
        AitBridge.setDestination(travel, t.homePos); // safe default: come back to the same spot
        // Our departure animation goes in only for this HADS dematerialise.
        swapAnimation(data, id, travel, "DEMAT", HADS_DEMAT_ANIMATION);
        try {
            // AiT's own call: it also sets up our arrival animation for the landing at the end of this trip
            AitAnimation.dematerialize(travel, HadsConfig.customAnimations() ? HADS_MAT_ANIMATION : null);
        } catch (NoSuchMethodException e) {
            AitBridge.dematerialize(travel);
        }
        if ("LANDED".equals(AitBridge.stateName(travel))) {
            restoreAnimation(server, data, id, "DEMAT", HADS_DEMAT_ANIMATION); // AiT refused: undo straight away
            reopenDoorsIfWanted(tardis, t); // put the doors back how they were
            try {
                AitBridge.setDestination(travel, t.originalDestination);
            } catch (Exception ignored) {
                // not important
            }
            TRIPS.remove(id);
            COOLDOWN.put(id, now + HadsConfig.failedCooldownTicks());
            return;
        }
        reopenDoorsIfWanted(tardis, t); // AiT has accepted, so the doors can go back to open
        t.doorsClosing = false;
        t.startedAt = now;
        if (HadsConfig.soundAlarm()) AitBridge.alarm(tardis, true);
        startMusic(server, id, t);
        TRIPS.put(id, t);
    }

    /** If the doors were open before HADS shut them for AiT, open them again (keepDoorsOpen option). */
    private static void reopenDoorsIfWanted(Object tardis, Trip t) {
        if (!t.reopenDoors) return;
        try {
            AitBridge.openDoors(AitBridge.door(tardis));
        } catch (NoSuchMethodException e) {
            t.reopenDoors = false;
            LOGGER.warn("HADS Switch: keepDoorsOpen is on, but this AiT has no openDoors call, so the doors stay shut");
        } catch (Exception e) {
            t.reopenDoors = false;
            LOGGER.warn("HADS Switch: could not reopen the doors", e);
        }
    }

    /** Starts the HADS music for players near the parked exterior and for anyone standing inside that TARDIS. */
    private static void startMusic(MinecraftServer server, UUID id, Trip t) {
        if (!HadsConfig.playMusic()) return;
        Vec3 spot = Vec3.atCenterOf(t.homeBlock);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean inside = false;
            try {
                inside = id.equals(AitBridge.tardisIdOf(p.serverLevel()));
            } catch (Exception ignored) {
                // not inside a TARDIS
            }
            boolean near = p.serverLevel() == t.homeWorld && p.distanceToSqr(spot) <= HadsConfig.musicRange() * HadsConfig.musicRange();
            if (inside || near) {
                HadsNetwork.send(p, HadsNetwork.Music.begin(inside, spot.x, spot.y, spot.z));
                t.listeners.add(p.getUUID());
            }
        }
    }

    /** Fades the music out for everyone who was given it (their game does the fade). */
    private static void fadeMusic(MinecraftServer server, Trip t) {
        for (UUID uuid : t.listeners) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                HadsNetwork.send(p, HadsNetwork.Music.fade());
            }
        }
        t.listeners.clear();
    }

    /**
     * Makes the TARDIS use our animation for the given state ("MAT" or "DEMAT"), remembering the one it had so it
     * can be put back. Returns false (and changes nothing) if AiT has not loaded our animation or won't take it.
     */
    private static boolean swapAnimation(HadsData data, UUID id, Object travel, String state,
                                         ResourceLocation custom) {
        if (!HadsConfig.customAnimations()) return false;
        try {
            if (!AitAnimation.isRegistered(custom)) {
                LOGGER.error("HADS Switch: AiT has not loaded the animation {}, so the normal {} animation will play. "
                        + "AiT needs data/{}/fx/animation/keyframes/<anything>.animation.json (the file name MUST end in "
                        + "'animation.json') containing an animation named '{}', plus data/{}/fx/animation/type/{}.json.",
                        custom, state, custom.getNamespace(), custom.getPath(), custom.getNamespace(), custom.getPath());
                return false;
            }
            Object original = AitAnimation.animationIdFor(travel, state);
            if (original == null) {
                LOGGER.warn("HADS Switch: TARDIS reports no {} animation, leaving it alone", state);
                return false;
            }
            if (custom.toString().equals(String.valueOf(original))) {
                return true; // already ours (left over); the saved original is still pending
            }
            data.setPendingRestore(id, state, String.valueOf(original));
            AitAnimation.setAnimationFor(travel, state, custom);
            Object now = AitAnimation.animationIdFor(travel, state);
            if (!custom.toString().equals(String.valueOf(now))) {
                // AiT ignored the change: put things back exactly as they were
                AitAnimation.setAnimationFor(travel, state, new ResourceLocation(String.valueOf(original)));
                data.clearPendingRestore(id, state);
                LOGGER.error("HADS Switch: tried to set {} animation {} but the TARDIS still reports {}",
                        state, custom, now);
                return false;
            }
            LOGGER.info("HADS Switch: {} animation set to {} (was {})", state, custom, original);
            return true;
        } catch (Exception e) {
            data.clearPendingRestore(id, state);
            LOGGER.error("HADS Switch could not set its {} animation, using the normal one", state, e);
            return false;
        }
    }

    /** Puts the TARDIS's normal animation for the given state back, but only if it is still ours. */
    private static void restoreAnimation(MinecraftServer server, HadsData data, UUID id, String state,
                                         ResourceLocation custom) throws Exception {
        String original = data.getPendingRestore(id, state);
        if (original == null) return;
        Object tardis = AitBridge.tardis(server, id);
        if (tardis != null) {
            Object travel = AitBridge.travel(tardis);
            Object current = AitAnimation.animationIdFor(travel, state);
            // only undo our own change, never one the player made in the meantime
            if (custom.toString().equals(String.valueOf(current))) {
                AitAnimation.setAnimationFor(travel, state, new ResourceLocation(original));
            }
        }
        data.clearPendingRestore(id, state);
    }

    private static void restoreAllAnimations(MinecraftServer server, HadsData data, UUID id) throws Exception {
        try {
            restoreAnimation(server, data, id, "DEMAT", HADS_DEMAT_ANIMATION);
        } finally {
            restoreAnimation(server, data, id, "MAT", HADS_MAT_ANIMATION);
        }
    }

    /** Where to look for danger: around the player if they are out in the world, otherwise the old spot. */
    private static Scan scanTarget(MinecraftServer server, Trip trip) {
        if (HadsConfig.landNearPlayer() && trip.playerId != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(trip.playerId);
            if (p != null && p.isAlive() && !AitBridge.isInterior(p.serverLevel())) {
                return new Scan(p.serverLevel(), p.blockPosition(), true);
            }
        }
        return new Scan(trip.homeWorld, trip.homeBlock, false);
    }

    private static List<int[]> buildLandingOffsets(int minDist, int maxDist) {
        List<int[]> list = new ArrayList<>();
        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dz = -maxDist; dz <= maxDist; dz++) {
                int far = Math.max(Math.abs(dx), Math.abs(dz));
                if (far < minDist || far > maxDist) continue;
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
        int maxK = Math.max(HadsConfig.landingMaxRise(), HadsConfig.landingMaxDrop());
        for (int[] o : landingOffsets()) {
            int x = center.getX() + o[0];
            int z = center.getZ() + o[1];
            for (int k = 0; k <= maxK; k++) {
                if (k <= HadsConfig.landingMaxRise()) {
                    BlockPos p = new BlockPos(x, center.getY() + k, z);
                    if (isSafeLanding(world, p)) return p;
                }
                if (k > 0 && k <= HadsConfig.landingMaxDrop()) {
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
        BlockPos lo = p.offset(-HadsConfig.landingHalfWidth(), -1, -HadsConfig.landingHalfWidth());
        BlockPos hi = p.offset(HadsConfig.landingHalfWidth(), HadsConfig.landingHeight() - 1, HadsConfig.landingHalfWidth());
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

        if (HadsConfig.triggerOnHostiles() && enemyRadius > 0
                && !world.getEntitiesOfClass(LivingEntity.class, base.inflate(enemyRadius),
                e -> e.isAlive() && e instanceof Enemy && !(e instanceof NeutralMob n && !n.isAngry())).isEmpty()) {
            return true;
        }
        if (HadsConfig.triggerOnCreepers() && explosiveRadius > 0
                && !world.getEntitiesOfClass(Creeper.class, base.inflate(explosiveRadius),
                c -> c.isAlive() && c.getSwellDir() > 0).isEmpty()) {
            return true;
        }
        if (HadsConfig.triggerOnTnt() && explosiveRadius > 0
                && !world.getEntitiesOfClass(PrimedTnt.class, base.inflate(explosiveRadius), t -> true).isEmpty()) {
            return true;
        }
        return HadsConfig.triggerOnProjectiles() && projectileRadius > 0
                && !world.getEntitiesOfClass(Projectile.class, base.inflate(projectileRadius),
                p -> !(p.getOwner() instanceof Player)).isEmpty();
    }
}

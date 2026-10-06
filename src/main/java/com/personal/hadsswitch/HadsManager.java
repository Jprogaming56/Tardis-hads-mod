package com.personal.hadsswitch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Our own HADS: leave when danger shows up at the TARDIS, then wait in the vortex while watching the area
 * around YOU (the player who switched HADS on). Once nothing hostile has been near you for a while, the
 * TARDIS materialises next to you. If you are inside a TARDIS (or offline) it watches its old spot instead
 * and returns there.
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
            AitBridge.setDestination(travel, posObj); // safe default: come back to the same spot
            AitBridge.dematerialize(travel);
            AitBridge.alarm(tardis, true);
            TRIPS.put(id, t);
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
                if (ready && canTry) {
                    Object dest = trip.homePos;
                    if (scan.followingPlayer()) {
                        BlockPos spot = findLanding(scan.world(), scan.pos());
                        dest = AitBridge.makePos(scan.world(), spot, AitBridge.rotationOf(trip.homePos));
                    }
                    AitBridge.setDestination(travel, dest);
                    AitBridge.rematerialize(travel);
                    trip.rematCalledAt = now;
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

    /** Finds a flat, empty 1x2 spot a few blocks from the player; falls back to the player's own spot. */
    private static BlockPos findLanding(ServerLevel world, BlockPos center) {
        int[] distances = {4, 6, 3, 8};
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int dist : distances) {
            for (int[] d : dirs) {
                int x = center.getX() + d[0] * dist;
                int z = center.getZ() + d[1] * dist;
                for (int y = center.getY() + 3; y >= center.getY() - 4; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!world.hasChunkAt(p)) break;
                    if (isStandable(world, p)) return p;
                }
            }
        }
        return center;
    }

    private static boolean isStandable(ServerLevel world, BlockPos p) {
        BlockState feet = world.getBlockState(p);
        BlockState head = world.getBlockState(p.above());
        BlockState floor = world.getBlockState(p.below());
        return feet.getCollisionShape(world, p).isEmpty() && feet.getFluidState().isEmpty()
                && head.getCollisionShape(world, p.above()).isEmpty() && head.getFluidState().isEmpty()
                && floor.isFaceSturdy(world, p.below(), Direction.UP) && floor.getFluidState().isEmpty();
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

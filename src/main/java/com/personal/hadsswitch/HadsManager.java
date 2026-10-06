package com.personal.hadsswitch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Our own HADS: leave when danger shows up, wait in the vortex, come back when it has been calm for a while.
 * All times are in ticks (20 ticks = 1 second). Change the numbers below to taste.
 */
@Mod.EventBusSubscriber(modid = HadsSwitchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HadsManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How often we look around (ticks). */
    private static final int CHECK_INTERVAL = 10;
    /** Hostile mob this close to the exterior triggers HADS (blocks). */
    private static final double ENEMY_RADIUS = 6.0;
    /** Lit creeper or primed TNT this close triggers HADS (blocks). */
    private static final double EXPLOSIVE_RADIUS = 8.0;
    /** Arrows, fireballs etc. from non-players this close trigger HADS (blocks). */
    private static final double PROJECTILE_RADIUS = 4.0;
    /** Minimum time spent in the vortex before coming back, counted from when the dematerialise finishes (15 s). */
    private static final long MIN_TIME_IN_VORTEX = 300;
    /** How long the area must be free of danger before coming back (20 s). */
    private static final long CALM_BEFORE_RETURN = 400;
    /** Pause before HADS can trigger again after a trip (10 s). */
    private static final long COOLDOWN_AFTER_TRIP = 200;

    private static final class Trip {
        Object homePos;
        ServerLevel homeWorld;
        BlockPos homeBlock;
        Object originalDestination;
        long startedAt;
        long flightSeenAt = -1;
        long calmSince = -1;
        long rematCalledAt = -1;
    }

    private static final Map<UUID, Trip> TRIPS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Set<UUID> FAILED = new HashSet<>();

    private HadsManager() {}

    /** Called by the block. Returns the new state, or null if the level is not a TARDIS interior. */
    public static Boolean toggle(ServerLevel interior) throws Exception {
        UUID id = AitBridge.tardisIdOf(interior);
        if (id == null) return null;
        MinecraftServer server = interior.getServer();
        HadsData data = HadsData.get(server);
        boolean next = !data.isEnabled(id);
        data.setEnabled(id, next);
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
            if (!isDanger(world, block)) return;

            Trip t = new Trip();
            t.homePos = posObj;
            t.homeWorld = world;
            t.homeBlock = block;
            t.originalDestination = AitBridge.destination(travel);
            t.startedAt = now;
            AitBridge.setDestination(travel, posObj); // so it comes back to the same spot
            AitBridge.dematerialize(travel);
            AitBridge.alarm(tardis, true);
            TRIPS.put(id, t);
            return;
        }

        switch (state) {
            case "FLIGHT" -> {
                if (trip.flightSeenAt < 0) trip.flightSeenAt = now;
                boolean danger = enabled && isDanger(trip.homeWorld, trip.homeBlock);
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

    private static boolean isDanger(ServerLevel world, BlockPos pos) {
        AABB base = new AABB(pos);

        if (!world.getEntitiesOfClass(LivingEntity.class, base.inflate(ENEMY_RADIUS),
                e -> e.isAlive() && e instanceof Enemy && !(e instanceof NeutralMob n && !n.isAngry())).isEmpty()) {
            return true;
        }
        if (!world.getEntitiesOfClass(Creeper.class, base.inflate(EXPLOSIVE_RADIUS),
                c -> c.isAlive() && c.getSwellDir() > 0).isEmpty()) {
            return true;
        }
        if (!world.getEntitiesOfClass(PrimedTnt.class, base.inflate(EXPLOSIVE_RADIUS), t -> true).isEmpty()) {
            return true;
        }
        return !world.getEntitiesOfClass(Projectile.class, base.inflate(PROJECTILE_RADIUS),
                p -> !(p.getOwner() instanceof Player)).isEmpty();
    }
}

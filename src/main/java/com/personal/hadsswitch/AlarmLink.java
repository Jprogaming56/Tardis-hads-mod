package com.personal.hadsswitch;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Once a HADS Switch block is placed inside a TARDIS it is "linked" to that TARDIS, and the console's alarm
 * button (right or left click) toggles our HADS instead of the alarm. Breaking the last HADS Switch in the
 * TARDIS gives the button back its normal job.
 *
 * Works by catching clicks on AiT's console control entity before AiT sees them, so nothing inside AiT is changed.
 */
@Mod.EventBusSubscriber(modid = HadsSwitchMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlarmLink {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AlarmLink() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (handle(player, event.getTarget())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (handle(player, event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /** Returns true if this click was the alarm button of a linked TARDIS and we handled it (so AiT must not). */
    private static boolean handle(ServerPlayer player, Entity target) {
        try {
            if (!target.getClass().getName().startsWith("dev.amble.ait")) return false;
            if (!(target.level() instanceof ServerLevel level)) return false;
            if (!AitBridge.isAlarmControlEntity(target)) return false;

            UUID id = AitBridge.tardisIdOf(level);
            if (id == null) return false;
            MinecraftServer server = level.getServer();
            HadsData data = HadsData.get(server);
            if (!hasLiveLink(level, data, id)) return false;
            if (aitNeedsThisClick(player)) return false;

            if (HadsConfig.alarmButtonSilencesRingingAlarm()) {
                Object tardis = AitBridge.tardis(server, id);
                if (tardis != null && AitBridge.alarmEnabled(tardis)) return false; // let AiT switch the alarm off
            }

            Boolean nowEnabled = HadsManager.toggle(level, player);
            if (nowEnabled == null) return false;
            syncBlocks(level, id, nowEnabled);
            level.playSound(null, target.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f,
                    nowEnabled ? 1.2f : 0.8f);
            player.displayClientMessage(Component.literal(nowEnabled ? "HADS: ENGAGED" : "HADS: DISENGAGED"), true);
            return true;
        } catch (Throwable t) {
            LOGGER.error("HADS Switch could not take over the alarm button (the normal alarm button will run instead)", t);
            return false;
        }
    }

    /** Clicks AiT uses for its own tools and editing; those must never be swallowed. */
    private static boolean aitNeedsThisClick(ServerPlayer player) {
        if ("minecraft:command_block".equals(itemKey(player.getOffhandItem()))) return true; // control editor
        String main = itemKey(player.getMainHandItem());
        return main.equals("ait:repair_tool") || main.equals("ait:tardis_item") || main.equals("ait:redstone_control_block")
                || main.equals("minecraft:slime_ball") || main.equals("minecraft:shears");
    }

    private static String itemKey(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    /** True while at least one HADS Switch block still exists for this TARDIS. Forgets blocks that have vanished. */
    private static boolean hasLiveLink(ServerLevel level, HadsData data, UUID id) {
        Set<Long> positions = new HashSet<>(data.linkedPositions(id));
        boolean any = false;
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos)) {
                any = true; // can't tell, assume it is still there
            } else if (level.getBlockState(pos).is(HadsSwitchMod.HADS_SWITCH.get())) {
                any = true;
            } else {
                data.unlink(id, packed);
            }
        }
        return any;
    }

    /** Makes every linked HADS Switch block in this TARDIS show the given state. */
    public static void syncBlocks(ServerLevel level, UUID id, boolean on) {
        HadsData data = HadsData.get(level.getServer());
        for (long packed : new HashSet<>(data.linkedPositions(id))) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(HadsSwitchMod.HADS_SWITCH.get()) && state.getValue(HadsSwitchBlock.ACTIVE) != on) {
                level.setBlock(pos, state.setValue(HadsSwitchBlock.ACTIVE, on), 3);
            }
        }
    }

    /** Same, working out which TARDIS this interior belongs to. */
    public static void syncBlocks(ServerLevel level, boolean on) {
        try {
            UUID id = AitBridge.tardisIdOf(level);
            if (id != null) syncBlocks(level, id, on);
        } catch (Exception e) {
            LOGGER.error("Could not update linked HADS Switch blocks", e);
        }
    }
}

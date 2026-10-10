package com.personal.hadsswitch;

import java.util.UUID;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

public class HadsSwitchBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final Logger LOGGER = LogUtils.getLogger();

    public HadsSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        try {
            Boolean nowEnabled = HadsManager.toggle((ServerLevel) level, player);
            if (nowEnabled == null) {
                player.displayClientMessage(Component.literal("HADS Switch only works inside a TARDIS."), true);
                return InteractionResult.CONSUME;
            }
            level.setBlock(pos, state.setValue(ACTIVE, nowEnabled), 3);
            AlarmLink.syncBlocks((ServerLevel) level, nowEnabled);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f,
                    nowEnabled ? 1.2f : 0.8f);
            player.displayClientMessage(Component.literal(nowEnabled ? "HADS: ENGAGED" : "HADS: DISENGAGED"), true);
        } catch (Exception e) {
            LOGGER.error("Could not toggle HADS", e);
            player.displayClientMessage(Component.literal("HADS link failed - check latest.log"), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Placing the block inside a TARDIS links it to that TARDIS's console alarm button. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || oldState.is(state.getBlock()) || !(level instanceof ServerLevel server)) return;
        try {
            UUID id = AitBridge.tardisIdOf(server);
            if (id != null) {
                HadsData.get(server.getServer()).link(id, pos.asLong());
            }
        } catch (Exception e) {
            LOGGER.error("Could not link HADS Switch to the alarm button", e);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level instanceof ServerLevel server)) return;
        try {
            UUID id = AitBridge.tardisIdOf(server);
            if (id != null) {
                // show the real state and tell the player the alarm button is now theirs
                boolean on = HadsData.get(server.getServer()).isEnabled(id);
                if (state.getValue(ACTIVE) != on) {
                    level.setBlock(pos, state.setValue(ACTIVE, on), 3);
                }
                if (placer instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.literal("HADS Switch linked: the console alarm button now toggles HADS."), true);
                }
            } else if (placer instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("HADS Switch only links to the alarm button inside a TARDIS."), true);
            }
        } catch (Exception e) {
            LOGGER.error("Could not finish linking HADS Switch", e);
        }
    }

    /** Breaking the block hands the alarm button back to AiT. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            try {
                UUID id = AitBridge.tardisIdOf(server);
                if (id != null) {
                    HadsData.get(server.getServer()).unlink(id, pos.asLong());
                }
            } catch (Exception e) {
                LOGGER.error("Could not unlink HADS Switch", e);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

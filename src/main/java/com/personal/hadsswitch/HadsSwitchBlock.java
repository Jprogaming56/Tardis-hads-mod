package com.personal.hadsswitch;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f,
                    nowEnabled ? 1.2f : 0.8f);
            player.displayClientMessage(Component.literal(nowEnabled ? "HADS: ENGAGED" : "HADS: DISENGAGED"), true);
        } catch (Exception e) {
            LOGGER.error("Could not toggle HADS", e);
            player.displayClientMessage(Component.literal("HADS link failed - check latest.log"), true);
        }
        return InteractionResult.CONSUME;
    }
}

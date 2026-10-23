package com.melon.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.RenderShape;

public class CopycatBlock extends DirectionalBlock implements EntityBlock {

    private static final VoxelShape UP_AABB = Block.box(0, 0, 0, 16, 1, 16);
    private static final VoxelShape DOWN_AABB = Block.box(0, 15, 0, 16, 16, 16);
    private static final VoxelShape NORTH_AABB = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape SOUTH_AABB = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape WEST_AABB = Block.box(15, 0, 0, 16, 16, 16);
    private static final VoxelShape EAST_AABB = Block.box(0, 0, 0, 1, 16, 16);

    public CopycatBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction dir = pState.getValue(FACING);
        return switch (dir) {
            case UP -> UP_AABB;
            case DOWN -> DOWN_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case EAST -> EAST_AABB;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        // 让方块本身透明，全权交给 BlockEntityRenderer (BER) 去画
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CopycatBlockEntity(pPos, pState);
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, net.minecraft.world.entity.player.Player pPlayer, net.minecraft.world.InteractionHand pHand, net.minecraft.world.phys.BlockHitResult pHit) {
        net.minecraft.world.item.ItemStack itemInHand = pPlayer.getItemInHand(pHand);
        if (itemInHand.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block != this && !pLevel.isClientSide) {
                if (pLevel.getBlockEntity(pPos) instanceof CopycatBlockEntity copycat) {
                    copycat.setDisguiseState(block.defaultBlockState());
                    copycat.setPlayerSetDisguise(true);
                    if (!pPlayer.isCreative()) {
                        itemInHand.shrink(1);
                    }
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            }
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, net.minecraft.world.entity.LivingEntity pPlacer, net.minecraft.world.item.ItemStack pStack) {
        if (!pLevel.isClientSide) {
            Direction attachDir = pState.getValue(FACING).getOpposite();
            BlockPos neighborPos = pPos.relative(attachDir);
            if (!pLevel.hasChunkAt(neighborPos)) {
                super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
                return;
            }
            BlockState neighborState = pLevel.getBlockState(neighborPos);
            
            if (!neighborState.isAir() && neighborState.getBlock() != this) {
                if (pLevel.getBlockEntity(pPos) instanceof CopycatBlockEntity copycat) {
                    copycat.setDisguiseState(neighborState);
                }
            }
        }
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return pLevel.isClientSide || pBlockEntityType != MelonAnomalyLibrary.COPYCAT_BLOCK_ENTITY.get() ? null : (level, pos, state, blockEntity) -> {
            if (blockEntity instanceof CopycatBlockEntity copycat) {
                copycat.tick();
            }
        };
    }

    @Override
    public void entityInside(BlockState pState, Level pLevel, BlockPos pPos, net.minecraft.world.entity.Entity pEntity) {
        if (pEntity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
            if (!livingEntity.hasEffect(MelonAnomalyLibrary.ELECTRIC_SHOCK.get())) {
                if (pLevel.isClientSide && livingEntity instanceof net.minecraft.world.entity.player.Player player) {
                    pLevel.playLocalSound(player.getX(), player.getY(), player.getZ(), MelonAnomalyLibrary.ELECTRIC_SHOCK_SOUND.get(), net.minecraft.sounds.SoundSource.MASTER, 1.0f, 1.0f, false);
                }
            }
            if (!pLevel.isClientSide) {
                livingEntity.addEffect(new net.minecraft.world.effect.MobEffectInstance(MelonAnomalyLibrary.ELECTRIC_SHOCK.get(), 60, 0, false, false, true));
            }
        }
        super.entityInside(pState, pLevel, pPos, pEntity);
    }
}

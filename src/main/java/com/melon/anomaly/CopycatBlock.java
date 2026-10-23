package com.melon.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide || !(held.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() == this || !(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat)) {
            return InteractionResult.PASS;
        }
        // 手动伪装使用物品对应方块的默认状态，保持原有消耗规则。
        copycat.setDisguiseState(blockItem.getBlock().defaultBlockState());
        copycat.setPlayerSetDisguise(true);
        if (!player.isCreative()) held.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos neighbor = pos.relative(state.getValue(FACING).getOpposite());
        if (!level.isClientSide && level.hasChunkAt(neighbor)
                && level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat) {
            BlockState disguise = level.getBlockState(neighbor);
            if (!disguise.isAir() && disguise.getBlock() != this) copycat.setDisguiseState(disguise);
        }
        super.setPlacedBy(level, pos, state, placer, stack);
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
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity living) {
            // 首次接触的本地音效和服务端持续触电分别处理，避免两端重复施加效果。
            if (level.isClientSide && !living.hasEffect(MelonAnomalyLibrary.ELECTRIC_SHOCK.get())
                    && living instanceof Player player) {
                level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                        MelonAnomalyLibrary.ELECTRIC_SHOCK_SOUND.get(), SoundSource.MASTER, 1.0F, 1.0F, false);
            }
            if (!level.isClientSide) {
                living.addEffect(new MobEffectInstance(MelonAnomalyLibrary.ELECTRIC_SHOCK.get(), 60, 0, false, false, true));
            }
        }
        super.entityInside(state, level, pos, entity);
    }
}

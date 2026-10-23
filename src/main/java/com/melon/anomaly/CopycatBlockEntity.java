package com.melon.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CopycatBlockEntity extends BlockEntity {
    private static final int FEED_INTERVAL = 10;
    private static final int FEED_COOLDOWN = 400;
    private static final int WANDER_INTERVAL = 1200;
    private static final double FEED_RADIUS = 1.5D;
    private static final List<Direction> DIRECTIONS = List.of(Direction.values());

    private BlockState disguiseState = Blocks.MOSS_CARPET.defaultBlockState();
    private boolean playerSetDisguise;
    private int timer;
    private int feedCooldown;
    private final Random random = new Random();

    public CopycatBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(MelonAnomalyLibrary.COPYCAT_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    public BlockState getDisguiseState() {
        return disguiseState;
    }

    public void setDisguiseState(BlockState disguiseState) {
        if (this.disguiseState.equals(disguiseState)) return;
        this.disguiseState = disguiseState;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void setPlayerSetDisguise(boolean playerSetDisguise) {
        if (this.playerSetDisguise == playerSetDisguise) return;
        this.playerSetDisguise = playerSetDisguise;
        this.setChanged();
    }

    public boolean isPlayerSetDisguise() {
        return playerSetDisguise;
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        pTag.put("DisguiseState", NbtUtils.writeBlockState(this.disguiseState));
        pTag.putBoolean("PlayerSetDisguise", this.playerSetDisguise);
        pTag.putInt("Timer", this.timer);
        pTag.putInt("FeedCooldown", this.feedCooldown);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        if (pTag.contains("DisguiseState")) {
            this.disguiseState = NbtUtils.readBlockState(this.level != null
                    ? this.level.holderLookup(Registries.BLOCK) : BuiltInRegistries.BLOCK.asLookup(),
                    pTag.getCompound("DisguiseState"));
        }
        // 更新包只包含外观；缺失的字段不能覆盖服务端已有状态。
        if (pTag.contains("PlayerSetDisguise")) this.playerSetDisguise = pTag.getBoolean("PlayerSetDisguise");
        if (pTag.contains("Timer")) this.timer = pTag.getInt("Timer");
        if (pTag.contains("FeedCooldown")) this.feedCooldown = pTag.getInt("FeedCooldown");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("DisguiseState", NbtUtils.writeBlockState(this.disguiseState));
        return tag;
    }

    private boolean isLoaded(BlockPos pos) {
        return !this.level.isOutsideBuildHeight(pos) && this.level.hasChunkAt(pos);
    }

    // 保留原有区别：捕食移动要求实体支撑面，自然游荡只排除空气和同类方块。
    private boolean canAttach(BlockPos pos, BlockState state, boolean feeding) {
        return !state.isAir() && (feeding ? state.isSolidRender(this.level, pos)
                : state.getBlock() != MelonAnomalyLibrary.COPYCAT_BLOCK.get());
    }

    private Direction findAttachment(BlockPos target, boolean feeding, List<Direction> directions) {
        for (Direction direction : directions) {
            BlockPos neighbor = target.relative(direction);
            if (isLoaded(neighbor) && canAttach(neighbor, this.level.getBlockState(neighbor), feeding)) {
                return direction;
            }
        }
        return null;
    }

    private boolean relocate(BlockPos target, Direction supportDirection) {
        if (supportDirection == null || target.equals(this.worldPosition) || !isLoaded(target)
                || !this.level.isEmptyBlock(target)) return false;
        BlockPos support = target.relative(supportDirection);
        if (!isLoaded(support)) return false;
        BlockState targetDisguise = this.level.getBlockState(support);
        BlockState previousTarget = this.level.getBlockState(target);
        BlockState newState = this.getBlockState().setValue(DirectionalBlock.FACING, supportDirection.getOpposite());

        // 先确认目标方块与实体创建成功，再删除原位置；失败时保留原实体。
        if (!this.level.setBlock(target, newState, Block.UPDATE_ALL)) return false;
        if (!(this.level.getBlockEntity(target) instanceof CopycatBlockEntity newEntity)) {
            if (this.level.getBlockState(target).is(newState.getBlock())) {
                this.level.setBlock(target, previousTarget, Block.UPDATE_ALL);
            }
            return false;
        }
        newEntity.disguiseState = this.playerSetDisguise ? this.disguiseState : targetDisguise;
        newEntity.playerSetDisguise = this.playerSetDisguise;
        newEntity.timer = this.timer;
        newEntity.feedCooldown = this.feedCooldown;
        if (!this.level.setBlock(this.worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
            this.level.setBlock(target, previousTarget, Block.UPDATE_ALL);
            return false;
        }
        newEntity.setChanged();
        this.level.sendBlockUpdated(target, newState, newState, Block.UPDATE_CLIENTS);
        return true;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide || this.isRemoved()) return;
        if (this.feedCooldown > 0) this.feedCooldown--;
        // 计时变化仅标记存盘，不通过 setChanged 每刻触发比较器通知。
        this.level.getChunkAt(this.worldPosition).setUnsaved(true);
        // 按位置错开扫描；成功捕食后本刻不再增加游荡计时。
        if (this.feedCooldown <= 0 && Math.floorMod(this.level.getGameTime(), FEED_INTERVAL)
                == Math.floorMod(this.worldPosition.asLong(), FEED_INTERVAL) && tryFeed()) return;
        if (++this.timer >= WANDER_INTERVAL) wander();
    }

    private boolean tryFeed() {
        List<ItemEntity> items = this.level.getEntitiesOfClass(ItemEntity.class,
                new AABB(this.worldPosition).inflate(FEED_RADIUS));
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            if (!stack.getItem().isEdible()) continue;
            var food = stack.getItem().getFoodProperties();
            if (food == null || !food.isMeat()) continue;
            stack.shrink(1);
            if (stack.isEmpty()) item.discard();
            this.feedCooldown = FEED_COOLDOWN;
            this.timer = 0;
            BlockPos target = item.blockPosition();
            if (!target.equals(this.worldPosition) && isLoaded(target) && this.level.isEmptyBlock(target)) {
                relocate(target, findAttachment(target, true, DIRECTIONS));
            }
            return true;
        }
        return false;
    }

    private void wander() {
        this.timer = 0;
        BlockPos current = this.worldPosition;
        List<BlockPos> possibleSpots = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = current.offset(x, y, z);
                    if (!candidate.equals(current) && isLoaded(candidate) && this.level.isEmptyBlock(candidate)
                            && findAttachment(candidate, false, DIRECTIONS) != null) {
                        possibleSpots.add(candidate);
                    }
                }
            }
        }
        if (possibleSpots.isEmpty()) return;
        BlockPos target = possibleSpots.get(this.random.nextInt(possibleSpots.size()));
        List<Direction> shuffled = new ArrayList<>(DIRECTIONS);
        Collections.shuffle(shuffled, this.random);
        relocate(target, findAttachment(target, false, shuffled));
    }
}

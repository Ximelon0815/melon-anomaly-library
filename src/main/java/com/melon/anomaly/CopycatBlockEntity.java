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

        // Keep the source intact until the target and its block entity are available.
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
        // Only persistence changes each tick; avoid comparator notifications from setChanged().
        this.level.getChunkAt(this.worldPosition).setUnsaved(true);
        BlockPos current = this.worldPosition;

        // Preserve the ten-tick interval while distributing searches across world positions.
        if (this.feedCooldown <= 0 && Math.floorMod(this.level.getGameTime(), 10)
                == Math.floorMod(current.asLong(), 10)) {
            List<ItemEntity> items = this.level.getEntitiesOfClass(ItemEntity.class, new AABB(current).inflate(1.5D));
            for (ItemEntity itemEntity : items) {
                ItemStack stack = itemEntity.getItem();
                if (stack.getItem().isEdible() && stack.getItem().getFoodProperties() != null
                        && stack.getItem().getFoodProperties().isMeat()) {
                    stack.shrink(1);
                    if (stack.isEmpty()) itemEntity.discard();
                    this.feedCooldown = 400;
                    this.timer = 0;
                    BlockPos target = itemEntity.blockPosition();
                    if (!target.equals(current) && isLoaded(target) && this.level.isEmptyBlock(target)) {
                        relocate(target, findAttachment(target, true, List.of(Direction.values())));
                    }
                    return;
                }
            }
        }

        if (++this.timer < 1200) return;
        this.timer = 0;
        List<BlockPos> possibleSpots = new ArrayList<>();
        List<Direction> directions = List.of(Direction.values());
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = current.offset(x, y, z);
                    if (!candidate.equals(current) && isLoaded(candidate) && this.level.isEmptyBlock(candidate)
                            && findAttachment(candidate, false, directions) != null) {
                        possibleSpots.add(candidate);
                    }
                }
            }
        }
        if (possibleSpots.isEmpty()) return;
        BlockPos target = possibleSpots.get(this.random.nextInt(possibleSpots.size()));
        List<Direction> shuffled = new ArrayList<>(directions);
        Collections.shuffle(shuffled, this.random);
        relocate(target, findAttachment(target, false, shuffled));
    }
}

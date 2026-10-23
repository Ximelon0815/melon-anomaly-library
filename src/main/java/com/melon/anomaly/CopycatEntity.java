package com.melon.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;

public class CopycatEntity extends PathfinderMob {

    private static final EntityDataAccessor<Optional<BlockState>> DISGUISE_STATE = SynchedEntityData.defineId(CopycatEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);

    public CopycatEntity(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        // 默认苔藓地毯
        this.entityData.define(DISGUISE_STATE, Optional.of(Blocks.MOSS_CARPET.defaultBlockState()));
    }

    public BlockState getDisguiseState() {
        return this.entityData.get(DISGUISE_STATE).orElse(Blocks.MOSS_CARPET.defaultBlockState());
    }

    public void setDisguiseState(BlockState state) {
        this.entityData.set(DISGUISE_STATE, Optional.of(state));
    }

    @Override
    public void tick() {
        super.tick();
        
        // 检测脚底下方块进行自动伪装
        if (!this.level().isClientSide && this.tickCount % 20 == 0) { // 每秒检测一次节省性能
            BlockPos posBelow = this.blockPosition().below();
            BlockState stateBelow = this.level().getBlockState(posBelow);
            
            // 忽略空气、屏障等不可见方块
            if (!stateBelow.isAir() && stateBelow.getBlock() != Blocks.BARRIER) {
                if (!stateBelow.equals(getDisguiseState())) {
                    setDisguiseState(stateBelow);
                }
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D);
    }
}

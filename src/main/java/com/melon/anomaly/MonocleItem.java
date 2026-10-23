package com.melon.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

public class MonocleItem extends ArmorItem {

    private static final Set<String> ALLOWED_BLOCKS = Set.of(
            "scplockdown:white_wall",
            "scplockdown:ceiling",
            "scplockdown:wall_e",
            "scplockdown:floor_a",
            "scplockdown:grate"
    );

    public MonocleItem() {
        super(ArmorMaterials.LEATHER, Type.HELMET, new Item.Properties().stacksTo(1));
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private net.minecraft.client.model.HumanoidModel<?> model;

            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(LivingEntity entityLiving, ItemStack itemStack, EquipmentSlot armorSlot, net.minecraft.client.model.HumanoidModel<?> _default) {
                if (model == null) {
                    model = new MonocleArmorModel<>(MonocleArmorModel.createArmorLayer().bakeRoot());
                }
                // 同步原版护甲模型的姿态，保持眼镜随玩家动作变化。
                model.young = _default.young;
                model.crouching = _default.crouching;
                model.riding = _default.riding;
                model.rightArmPose = _default.rightArmPose;
                model.leftArmPose = _default.leftArmPose;
                return model;
            }
        });
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "melon_anomaly:textures/models/armor/glasses_mono_layer_1.png";
    }

    public void teleportToValidLocation(Player player, Level level) {
        if (level.isClientSide) return;
        BlockPos currentPos = player.blockPosition();
        int radius = 30; // 沿用原有传送范围

        // 最多尝试 50 次；查询前排除未加载区域，避免同步加载区块。
        for (int i = 0; i < 50; i++) {
            int dx = level.random.nextInt(radius * 2) - radius;
            int dy = level.random.nextInt(radius * 2) - radius;
            int dz = level.random.nextInt(radius * 2) - radius;

            BlockPos targetPos = currentPos.offset(dx, dy, dz);
            BlockPos floorPos = targetPos.below();
            double x = targetPos.getX() + 0.5;
            double y = targetPos.getY();
            double z = targetPos.getZ() + 0.5;
            AABB targetBounds = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());

            if (floorPos.getY() < level.getMinBuildHeight()
                    || targetBounds.maxY > level.getMaxBuildHeight()
                    || targetPos.above().getY() >= level.getMaxBuildHeight()
                    || targetBounds.minX < level.getWorldBorder().getMinX()
                    || targetBounds.maxX > level.getWorldBorder().getMaxX()
                    || targetBounds.minZ < level.getWorldBorder().getMinZ()
                    || targetBounds.maxZ > level.getWorldBorder().getMaxZ()
                    || !hasLoadedCollisionArea(level, targetBounds)) {
                continue;
            }

            BlockState floorState = level.getBlockState(floorPos);
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(floorState.getBlock());

            // 除了白名单地板与两格空气，还检查玩家完整碰撞箱。
            if (blockId != null && ALLOWED_BLOCKS.contains(blockId.toString())
                    && level.isEmptyBlock(targetPos) && level.isEmptyBlock(targetPos.above())
                    && level.noCollision(player, targetBounds)) {
                player.teleportTo(x, y, z);
                return;
            }
        }
    }

    private static boolean hasLoadedCollisionArea(Level level, AABB bounds) {
        // 邻接方块的碰撞形状可能伸入目标区域，区块检查需向外扩一格。
        int minChunkX = Mth.floor(bounds.minX - 1.0) >> 4;
        int maxChunkX = Mth.floor(bounds.maxX + 1.0) >> 4;
        int minChunkZ = Mth.floor(bounds.minZ - 1.0) >> 4;
        int maxChunkZ = Mth.floor(bounds.maxZ + 1.0) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }
}

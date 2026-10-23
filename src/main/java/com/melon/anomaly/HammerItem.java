package com.melon.anomaly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.phys.Vec3;

public class HammerItem extends PickaxeItem {
    // 保持旧 NBT 键名，已有存档中的固定状态可继续读取。
    private static final String NAILED = "melon_anomaly:nailed";
    private static final String NAIL_X = "melon_anomaly:nail_x";
    private static final String NAIL_Y = "melon_anomaly:nail_y";
    private static final String NAIL_Z = "melon_anomaly:nail_z";

    public HammerItem() {
        super(Tiers.IRON, 4, -2.8F, new Item.Properties());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        ItemStack offhand = player.getOffhandItem();
        // 钉住与拔钉共用同一入口：两者都必须主手持锤、副手持钉。
        if (hand != InteractionHand.MAIN_HAND || !offhand.is(MelonAnomalyLibrary.NAIL.get())) {
            return super.interactLivingEntity(stack, player, target, hand);
        }
        if (!player.level().isClientSide) {
            if (isNailed(target)) {
                CompoundTag data = target.getPersistentData();
                data.remove(NAILED);
                data.remove(NAIL_X);
                data.remove(NAIL_Y);
                data.remove(NAIL_Z);
                if (!player.getAbilities().instabuild) refundNail(player, offhand);
            } else {
                if (!player.getAbilities().instabuild) offhand.shrink(1);
                target.hurt(player.damageSources().generic(), 2.0F);
                CompoundTag data = target.getPersistentData();
                data.putBoolean(NAILED, true);
                data.putDouble(NAIL_X, target.getX());
                data.putDouble(NAIL_Y, target.getY());
                data.putDouble(NAIL_Z, target.getZ());
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private static void refundNail(Player player, ItemStack offhand) {
        // 优先叠加回副手；满组后依次尝试背包和掉落，避免钉子丢失。
        if (offhand.getCount() < offhand.getMaxStackSize()) {
            offhand.grow(1);
            return;
        }
        ItemStack nail = new ItemStack(MelonAnomalyLibrary.NAIL.get());
        if (!player.addItem(nail)) player.drop(nail, false);
    }

    static boolean isNailed(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(NAILED);
    }

    static void keepNailedInPlace(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(NAILED)) return;
        double x = data.getDouble(NAIL_X), y = data.getDouble(NAIL_Y), z = data.getDouble(NAIL_Z);
        if (entity.getX() == x && entity.getY() == y && entity.getZ() == z) return;
        entity.setDeltaMovement(Vec3.ZERO);
        // 使用传送入口，让服务端玩家的位置修正同步到客户端。
        entity.teleportTo(x, y, z);
        entity.hurtMarked = true;
    }
}

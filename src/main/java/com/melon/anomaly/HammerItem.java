package com.melon.anomaly;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.Item;

public class HammerItem extends PickaxeItem {
    // 铁质(Tiers.IRON), 物品本身伤害修饰符设定为4点
    public HammerItem() {
        super(Tiers.IRON, 4, -2.8F, new Item.Properties());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack pStack, Player pPlayer, LivingEntity pInteractionTarget, InteractionHand pUsedHand) {
        if (pUsedHand == InteractionHand.MAIN_HAND) {
            ItemStack offhand = pPlayer.getOffhandItem();
            boolean isNailed = pInteractionTarget.getPersistentData().getBoolean("melon_anomaly:nailed");

            if (isNailed && offhand.getItem() == MelonAnomalyLibrary.NAIL.get()) {
                // 取回钉子
                if (!pPlayer.level().isClientSide) {
                    pInteractionTarget.getPersistentData().remove("melon_anomaly:nailed");
                    pInteractionTarget.getPersistentData().remove("melon_anomaly:nail_x");
                    pInteractionTarget.getPersistentData().remove("melon_anomaly:nail_y");
                    pInteractionTarget.getPersistentData().remove("melon_anomaly:nail_z");
                    if (!pPlayer.getAbilities().instabuild) {
                        if (offhand.getCount() < offhand.getMaxStackSize()) {
                            offhand.grow(1);
                        } else {
                            ItemStack returnedNail = new ItemStack(MelonAnomalyLibrary.NAIL.get());
                            if (!pPlayer.addItem(returnedNail)) {
                                pPlayer.drop(returnedNail, false);
                            }
                        }
                    }
                }
                return InteractionResult.sidedSuccess(pPlayer.level().isClientSide);
            } else if (!isNailed && offhand.getItem() == MelonAnomalyLibrary.NAIL.get()) {
                // 消耗钉子，钉住生物
                if (!pPlayer.level().isClientSide) {
                    if (!pPlayer.getAbilities().instabuild) {
                        offhand.shrink(1);
                    }
                    pInteractionTarget.hurt(pPlayer.damageSources().generic(), 2.0F);
                    pInteractionTarget.getPersistentData().putBoolean("melon_anomaly:nailed", true);
                    pInteractionTarget.getPersistentData().putDouble("melon_anomaly:nail_x", pInteractionTarget.getX());
                    pInteractionTarget.getPersistentData().putDouble("melon_anomaly:nail_y", pInteractionTarget.getY());
                    pInteractionTarget.getPersistentData().putDouble("melon_anomaly:nail_z", pInteractionTarget.getZ());
                }
                return InteractionResult.sidedSuccess(pPlayer.level().isClientSide);
            }
        }
        return super.interactLivingEntity(pStack, pPlayer, pInteractionTarget, pUsedHand);
    }
}

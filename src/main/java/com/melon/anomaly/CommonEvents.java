package com.melon.anomaly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 事件入口仅分发逻辑，具体异常物的处理分别集中在对应方法中。 */
@Mod.EventBusSubscriber(modid = MelonAnomalyLibrary.MODID)
public class CommonEvents {
    private static final String WAS_COLLIDING = "TarotWasColliding";

    public static boolean hasChariot(Player player) {
        Item chariot = MelonAnomalyLibrary.TAROT_THE_CHARIOT.get();
        return player.getMainHandItem().is(chariot) || player.getOffhandItem().is(chariot)
                || CuriosCompat.hasItem(player, chariot);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        tickChariot(player);
        // 保留原有 200 tick 周期；只在触发时查询眼镜，避免每刻重复检查饰品。
        if (player.tickCount % 200 == 0
                && (player.getItemBySlot(EquipmentSlot.HEAD).is(MelonAnomalyLibrary.MONOCLE.get())
                || CuriosCompat.hasItem(player, MelonAnomalyLibrary.MONOCLE.get()))
                && MelonAnomalyLibrary.MONOCLE.get() instanceof MonocleItem monocle) {
            monocle.teleportToValidLocation(player, player.level());
        }
    }

    private static void tickChariot(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!hasChariot(player)) {
            data.remove(WAS_COLLIDING);
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2, 4, false, false, false));
        Vec3 look = player.getLookAngle();
        AABB forwardBox = player.getBoundingBox().move(look.x * 0.5, 0, look.z * 0.5);
        boolean colliding = !player.level().noCollision(player, forwardBox);
        // 只在进入碰撞状态的瞬间扣血，持续顶墙不会每刻重复伤害。
        if (colliding && !data.getBoolean(WAS_COLLIDING)) {
            player.hurt(new AnomalyDamageSource(player.damageSources().generic(), "death.attack.chariot_crash"), 4.0F);
        }
        data.putBoolean(WAS_COLLIDING, colliding);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide) HammerItem.keepNailedInPlace(event.getEntity());
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (HammerItem.isNailed(event.getEntity())) event.setCanceled(true);
    }
}

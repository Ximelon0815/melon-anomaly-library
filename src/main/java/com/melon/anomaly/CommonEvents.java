package com.melon.anomaly;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.damagesource.DamageSource;

@Mod.EventBusSubscriber(modid = MelonAnomalyLibrary.MODID)
public class CommonEvents {

    public static boolean hasChariot(Player player) {
        Item chariot = MelonAnomalyLibrary.TAROT_THE_CHARIOT.get();
        return player.getMainHandItem().is(chariot) || player.getOffhandItem().is(chariot)
                || CuriosCompat.hasItem(player, chariot);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            Player player = event.player;
            
            // Tarot: The Chariot logic
            boolean hasChariot = hasChariot(player);
                                 
            if (hasChariot) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 2, 4, false, false, false));
                
                boolean wasColliding = player.getPersistentData().getBoolean("TarotWasColliding");
                Vec3 look = player.getLookAngle();
                AABB forwardBox = player.getBoundingBox().move(look.x * 0.5, 0, look.z * 0.5);
                boolean isColliding = !player.level().noCollision(player, forwardBox);
                
                if (isColliding && !wasColliding) { 
                    DamageSource baseSource = player.damageSources().generic();
                    DamageSource source = new DamageSource(baseSource.typeHolder(), baseSource.getEntity(), baseSource.getDirectEntity()) {
                        @Override
                        public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity entity) {
                            String msgId = entity.level().random.nextBoolean() ? "death.attack.chariot_crash_1" : "death.attack.chariot_crash_2";
                            return net.minecraft.network.chat.Component.translatable(msgId, entity.getDisplayName());
                        }
                    };
                    player.hurt(source, 4.0F);
                }
                player.getPersistentData().putBoolean("TarotWasColliding", isColliding);
            } else {
                player.getPersistentData().remove("TarotWasColliding");
            }
            
            // Monocle logic
            boolean hasMonocle = player.getItemBySlot(EquipmentSlot.HEAD).getItem() == MelonAnomalyLibrary.MONOCLE.get() ||
                                 CuriosCompat.hasItem(player, MelonAnomalyLibrary.MONOCLE.get());
                                 
            if (hasMonocle) {
                if (player.tickCount % 200 == 0) {
                    Item monocleItem = MelonAnomalyLibrary.MONOCLE.get();
                    if (monocleItem instanceof MonocleItem monocle) {
                        monocle.teleportToValidLocation(player, player.level());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        
        if (entity.getPersistentData().getBoolean("melon_anomaly:nailed")) {
            double x = entity.getPersistentData().getDouble("melon_anomaly:nail_x");
            double y = entity.getPersistentData().getDouble("melon_anomaly:nail_y");
            double z = entity.getPersistentData().getDouble("melon_anomaly:nail_z");
            
            if (entity.getX() != x || entity.getY() != y || entity.getZ() != z) {
                entity.setDeltaMovement(Vec3.ZERO);
                entity.teleportTo(x, y, z);
                entity.hurtMarked = true;
            }
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity().getPersistentData().getBoolean("melon_anomaly:nailed")) {
            event.setCanceled(true);
        }
    }
}

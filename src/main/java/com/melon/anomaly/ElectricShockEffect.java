package com.melon.anomaly;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;

public class ElectricShockEffect extends MobEffect {
    
    public ElectricShockEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFFF00); // Yellowish color
        this.addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "e5b8a5b2-3c4a-4b6c-8a1a-0a0f9d9f5a8a", 0.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        // Blindness and Slowness 5 (amplifier 4)
        if (!pLivingEntity.level().isClientSide) {
            pLivingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
            pLivingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false, false));
            
            // Damage 2 points every 2 seconds (40 ticks)
            if (pLivingEntity.tickCount % 40 == 0) {
                pLivingEntity.level().playSound(null, pLivingEntity.blockPosition(), MelonAnomalyLibrary.ELECTRIC_SHOCK_SOUND.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                DamageSource magicSource = pLivingEntity.damageSources().magic();
                DamageSource source = new DamageSource(magicSource.typeHolder(), magicSource.getEntity(), magicSource.getDirectEntity()) {
                    @Override
                    public net.minecraft.network.chat.Component getLocalizedDeathMessage(LivingEntity entity) {
                        String msgId = entity.level().random.nextBoolean() ? "death.attack.electric_shock_1" : "death.attack.electric_shock_2";
                        return net.minecraft.network.chat.Component.translatable(msgId, entity.getDisplayName());
                    }
                };
                pLivingEntity.hurt(source, 2.0F);
            }
        } else {
            // Client side camera shake
            if (pLivingEntity instanceof Player player && player.isLocalPlayer()) {
                float yawShake = (player.getRandom().nextFloat() - 0.5f) * 10f;
                float pitchShake = (player.getRandom().nextFloat() - 0.5f) * 10f;
                player.setYRot(player.getYRot() + yawShake);
                player.setXRot(Mth.clamp(player.getXRot() + pitchShake, -90.0F, 90.0F));
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int pDuration, int pAmplifier) {
        // Apply every tick
        return true;
    }
}

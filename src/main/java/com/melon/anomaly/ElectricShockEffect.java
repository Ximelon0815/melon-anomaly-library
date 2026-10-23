package com.melon.anomaly;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

public class ElectricShockEffect extends MobEffect {

    public ElectricShockEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFFF00); // 触电效果图标的黄色标识
        this.addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "e5b8a5b2-3c4a-4b6c-8a1a-0a0f9d9f5a8a", 0.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        // 保持每刻续期，确保触电结束后的失明、缓慢持续时间不变。
        if (!pLivingEntity.level().isClientSide) {
            pLivingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
            pLivingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false, false));

            // 每 40 tick 造成 2 点伤害，沿用原来的实体计时。
            if (pLivingEntity.tickCount % 40 == 0) {
                pLivingEntity.level().playSound(null, pLivingEntity.blockPosition(), MelonAnomalyLibrary.ELECTRIC_SHOCK_SOUND.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                pLivingEntity.hurt(new AnomalyDamageSource(pLivingEntity.damageSources().magic(),
                        "death.attack.electric_shock"), 2.0F);
            }
        } else {
            // 只扰动本地玩家视角，俯仰角限制在正常范围内。
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
        // 服务端效果与客户端震动均按原来的每刻频率执行。
        return true;
    }
}

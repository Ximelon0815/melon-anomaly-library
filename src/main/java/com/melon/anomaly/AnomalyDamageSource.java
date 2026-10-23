package com.melon.anomaly;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** 复用原伤害类型与归属，只替换异常物的随机死亡文案。 */
final class AnomalyDamageSource extends DamageSource {
    private final String messagePrefix;

    AnomalyDamageSource(DamageSource source, String messagePrefix) {
        super(source.typeHolder(), source.getEntity(), source.getDirectEntity());
        this.messagePrefix = messagePrefix;
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity entity) {
        String key = messagePrefix + (entity.level().random.nextBoolean() ? "_1" : "_2");
        return Component.translatable(key, entity.getDisplayName());
    }
}

package com.melon.anomaly;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MelonAnomalyLibrary.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (player == null || !CommonEvents.hasChariot(player)) return;
        // 客户端与服务端共用装备判定，手持和饰品栏的战车行为一致。
        event.getInput().up = true;
        event.getInput().down = false;
        event.getInput().forwardImpulse = 1.0f;
        player.setSprinting(true);
    }
}

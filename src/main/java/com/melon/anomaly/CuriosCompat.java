package com.melon.anomaly;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;

final class CuriosCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean failed;

    private CuriosCompat() {
    }

    static boolean hasItem(Player player, Item item) {
        Access access = Holder.ACCESS;
        if (access == null || failed) return false;
        try {
            if (access.inventoryMethod != null) {
                LazyOptional<?> inventory = (LazyOptional<?>) access.inventoryMethod.invoke(null, player);
                Object handler = inventory.orElse(null);
                return handler != null && ((Optional<?>) access.findMethod.invoke(handler, item)).isPresent();
            }
            return ((Optional<?>) access.findMethod.invoke(access.helper, player, item)).isPresent();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
            return false;
        }
    }

    private static synchronized void disable(Throwable exception) {
        if (!failed) {
            failed = true;
            LOGGER.warn("Unable to read Curios equipment; disabling Curios equipment checks", exception);
        }
    }

    private static class Holder {
        private static final Access ACCESS = createAccess();

        private static Access createAccess() {
            if (!ModList.get().isLoaded("curios")) return null;
            try {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                try {
                    Method inventory = api.getMethod("getCuriosInventory", LivingEntity.class);
                    Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                    return new Access(inventory, handler.getMethod("findFirstCurio", Item.class), null);
                } catch (NoSuchMethodException | ClassNotFoundException missingModernApi) {
                    Object helper = api.getMethod("getCuriosHelper").invoke(null);
                    Class<?> helperApi = Class.forName("top.theillusivec4.curios.api.type.util.ICuriosHelper");
                    return new Access(null, helperApi.getMethod("findFirstCurio", LivingEntity.class, Item.class), helper);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                disable(exception);
                return null;
            }
        }
    }

    private record Access(Method inventoryMethod, Method findMethod, Object helper) {
    }
}

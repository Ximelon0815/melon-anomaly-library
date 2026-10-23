package com.melon.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

@GameTestHolder(MelonAnomalyLibrary.MODID)
@PrefixGameTestTemplate(false)
public class AnomalyRegressionTests {
    private static final BlockPos CENTER = new BlockPos(1, 1, 1);

    // 清空受伤无敌时间，确保“持续顶墙不重复扣血”由碰撞状态控制。
    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void chariotDamagesOnlyOnCollisionEntry(GameTestHelper helper) {
        Player player = survivalPlayer(helper);
        player.tickCount = 1;
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MelonAnomalyLibrary.TAROT_THE_CHARIOT.get()));
        BlockPos wall = CENTER.south();
        helper.setBlock(wall, Blocks.STONE);
        CommonEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Chariot must retain speed effect");
        helper.assertTrue(player.getHealth() == 16.0F, "First wall collision should deal four damage");
        player.invulnerableTime = 0;
        CommonEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(player.getHealth() == 16.0F, "Sustained collision must not deal repeated damage");
        helper.setBlock(wall, Blocks.AIR);
        CommonEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.setBlock(wall, Blocks.STONE);
        CommonEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(player.getHealth() == 12.0F, "Re-entering collision should deal four damage");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CommonEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(!player.getPersistentData().contains("TarotWasColliding"), "Unequipping must clear collision state");
        helper.succeed();
    }

    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void anomalyDamagePreservesTypesAndMessageKeys(GameTestHelper helper) {
        Player player = survivalPlayer(helper);
        var bases = new net.minecraft.world.damagesource.DamageSource[]{player.damageSources().generic(), player.damageSources().magic()};
        String[] prefixes = {"death.attack.chariot_crash", "death.attack.electric_shock"};
        for (int i = 0; i < bases.length; i++) {
            var source = new AnomalyDamageSource(bases[i], prefixes[i]);
            helper.assertTrue(source.typeHolder().equals(bases[i].typeHolder()), "Damage type must be preserved");
            String key = ((TranslatableContents) source.getLocalizedDeathMessage(player).getContents()).getKey();
            helper.assertTrue(key.equals(prefixes[i] + "_1") || key.equals(prefixes[i] + "_2"), "Existing death message keys must be preserved");
        }
        helper.succeed();
    }

    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void hammerRequiresOffhandNailAndRefunds(GameTestHelper helper) {
        Player player = survivalPlayer(helper);
        var target = helper.spawnWithNoFreeWill(EntityType.PIG, CENTER);
        ItemStack hammer = new ItemStack(MelonAnomalyLibrary.HAMMER.get());
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MelonAnomalyLibrary.NAIL.get(), 2));
        hammer.getItem().interactLivingEntity(hammer, player, target, InteractionHand.MAIN_HAND);
        helper.assertTrue(target.getPersistentData().getBoolean("melon_anomaly:nailed"), "Hammer should pin with an offhand nail");
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "Pinning should consume one nail");

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        hammer.getItem().interactLivingEntity(hammer, player, target, InteractionHand.MAIN_HAND);
        helper.assertTrue(target.getPersistentData().getBoolean("melon_anomaly:nailed"), "Empty offhand must not unpin");
        helper.assertTrue(target.getPersistentData().contains("melon_anomaly:nail_x"), "Failed unpin must retain coordinates");

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MelonAnomalyLibrary.NAIL.get()));
        hammer.getItem().interactLivingEntity(hammer, player, target, InteractionHand.MAIN_HAND);
        assertUnpinned(helper, target.getPersistentData());
        helper.assertTrue(player.getOffhandItem().getCount() == 2, "Unpinning should return the nail directly to the offhand");
        helper.assertTrue(player.getInventory().countItem(MelonAnomalyLibrary.NAIL.get()) == 2, "Unpinning should refund exactly one nail");
        helper.succeed();
    }

    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void hammerUsesInventoryWhenOffhandFull(GameTestHelper helper) {
        Player player = survivalPlayer(helper);
        ItemStack hammer = new ItemStack(MelonAnomalyLibrary.HAMMER.get());
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MelonAnomalyLibrary.NAIL.get(), 64));
        var target = helper.spawnWithNoFreeWill(EntityType.PIG, CENTER);
        target.getPersistentData().putBoolean("melon_anomaly:nailed", true);
        hammer.getItem().interactLivingEntity(hammer, player, target, InteractionHand.MAIN_HAND);
        assertUnpinned(helper, target.getPersistentData());
        helper.assertTrue(player.getOffhandItem().getCount() == 64, "Refund must not overflow the offhand stack");
        helper.assertTrue(player.getInventory().countItem(MelonAnomalyLibrary.NAIL.get()) == 65,
                "A full offhand should send the refunded nail to the inventory");
        helper.succeed();
    }

    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void hammerDropsRefundWhenInventoryFull(GameTestHelper helper) {
        Player player = survivalPlayer(helper);
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
        }
        ItemStack hammer = new ItemStack(MelonAnomalyLibrary.HAMMER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(MelonAnomalyLibrary.NAIL.get(), 64));
        var target = helper.spawnWithNoFreeWill(EntityType.PIG, CENTER);
        CompoundTag data = target.getPersistentData();
        data.putBoolean("melon_anomaly:nailed", true);
        data.putDouble("melon_anomaly:nail_x", target.getX());
        data.putDouble("melon_anomaly:nail_y", target.getY());
        data.putDouble("melon_anomaly:nail_z", target.getZ());
        hammer.getItem().interactLivingEntity(hammer, player, target, InteractionHand.MAIN_HAND);
        assertUnpinned(helper, data);
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(1.5))
                .stream().filter(item -> item.getItem().is(MelonAnomalyLibrary.NAIL.get()))
                .mapToInt(item -> item.getItem().getCount()).sum();
        helper.assertTrue(dropped == 1, "Full inventory should drop exactly one refunded nail");
        helper.assertTrue(player.getOffhandItem().getCount() == 64, "Unpinning must not consume the held nail");
        helper.succeed();
    }

    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void mossLoadsLegacyNbtAndSynchronizesOnlyDisguise(GameTestHelper helper) {
        CopycatBlockEntity moss = new CopycatBlockEntity(helper.absolutePos(CENTER), MelonAnomalyLibrary.COPYCAT_BLOCK.get().defaultBlockState());
        CompoundTag legacy = mossData(731, 137);
        moss.load(legacy);
        CompoundTag saved = moss.saveWithoutMetadata();
        helper.assertTrue(saved.getInt("Timer") == 731 && saved.getInt("FeedCooldown") == 137, "Legacy timers must round-trip");
        helper.assertTrue(moss.isPlayerSetDisguise() && moss.getDisguiseState().is(Blocks.GOLD_BLOCK), "Legacy manual disguise must load");
        helper.assertTrue(saved.getCompound("DisguiseState").equals(legacy.getCompound("DisguiseState")), "Saved disguise must round-trip");
        helper.assertTrue(moss.getUpdateTag().getAllKeys().equals(Set.of("DisguiseState")), "Network tag must omit server timers and flags");
        CopycatBlockEntity clientCopy = new CopycatBlockEntity(helper.absolutePos(CENTER), moss.getBlockState());
        clientCopy.load(moss.getUpdateTag());
        helper.assertTrue(clientCopy.getDisguiseState().is(Blocks.GOLD_BLOCK), "Visual update tag must restore disguise");
        helper.succeed();
    }

    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void mossWanderingPreservesFeedingCooldown(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }
        helper.setBlock(CENTER, MelonAnomalyLibrary.COPYCAT_BLOCK.get());
        CopycatBlockEntity source = (CopycatBlockEntity) helper.getBlockEntity(CENTER);
        source.load(mossData(1199, 137));
        source.tick();
        helper.assertTrue(helper.getBlockState(CENTER).isAir(), "Wandering should remove the old position");
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(CENTER.offset(-1, -1, -1), CENTER.offset(1, 1, 1))) {
            if (helper.getBlockEntity(pos) instanceof CopycatBlockEntity moved) {
                found++;
                CompoundTag saved = moved.saveWithoutMetadata();
                helper.assertTrue(saved.getInt("FeedCooldown") == 136, "Wandering must retain the active feeding cooldown");
                helper.assertTrue(saved.getInt("Timer") == 0, "Wandering should reset only the wandering timer");
                helper.assertTrue(moved.isPlayerSetDisguise() && moved.getDisguiseState().is(Blocks.GOLD_BLOCK), "Movement must preserve manual disguise");
            }
        }
        helper.assertTrue(found == 1, "Movement must leave exactly one moss block entity");
        helper.succeed();
    }

    @GameTest(template = "regression_empty", timeoutTicks = 20)
    public static void mossBlockedWanderingRetainsSourceAndCooldown(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 2, 2, 2)) helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(CENTER, MelonAnomalyLibrary.COPYCAT_BLOCK.get());
        CopycatBlockEntity source = (CopycatBlockEntity) helper.getBlockEntity(CENTER);
        source.load(mossData(1199, 137));
        source.tick();
        helper.assertTrue(helper.getBlockEntity(CENTER) == source, "Blocked movement must retain the source entity");
        CompoundTag saved = source.saveWithoutMetadata();
        helper.assertTrue(saved.getInt("FeedCooldown") == 136 && saved.getInt("Timer") == 0, "Blocked movement must preserve cooldown and restart wandering interval");
        helper.assertTrue(source.isPlayerSetDisguise() && source.getDisguiseState().is(Blocks.GOLD_BLOCK), "Blocked movement must retain disguise");
        helper.succeed();
    }

    private static Player survivalPlayer(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        BlockPos pos = helper.absolutePos(CENTER);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static void assertUnpinned(GameTestHelper helper, CompoundTag data) {
        helper.assertTrue(!data.contains("melon_anomaly:nailed") && !data.contains("melon_anomaly:nail_x")
                && !data.contains("melon_anomaly:nail_y") && !data.contains("melon_anomaly:nail_z"), "Unpinning must clear the flag and all saved coordinates");
    }

    private static CompoundTag mossData(int timer, int cooldown) {
        CompoundTag data = new CompoundTag();
        data.put("DisguiseState", NbtUtils.writeBlockState(Blocks.GOLD_BLOCK.defaultBlockState()));
        data.putBoolean("PlayerSetDisguise", true);
        data.putInt("Timer", timer);
        data.putInt("FeedCooldown", cooldown);
        return data;
    }
}

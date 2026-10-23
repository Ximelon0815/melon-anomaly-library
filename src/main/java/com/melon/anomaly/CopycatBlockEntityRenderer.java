package com.melon.anomaly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MelonAnomalyLibrary.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CopycatBlockEntityRenderer implements BlockEntityRenderer<CopycatBlockEntity> {

    private static final int MAX_CACHED_STATES = 256;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] RENDER_ORDER = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    // 按 Direction 顺序列出四个顶点；0/1 为 X 下/上界，2/3 为 Y 下/上界，4/5 为 Z 下/上界。
    // 保持原有顶点绕序，避免重构后出现背面剔除或纹理翻转。
    private static final int[][] FACE_VERTICES = {
            {0, 2, 4, 1, 2, 4, 1, 2, 5, 0, 2, 5}, // 下
            {0, 3, 5, 1, 3, 5, 1, 3, 4, 0, 3, 4}, // 上
            {1, 2, 4, 0, 2, 4, 0, 3, 4, 1, 3, 4}, // 北
            {0, 2, 5, 1, 2, 5, 1, 3, 5, 0, 3, 5}, // 南
            {0, 2, 4, 0, 2, 5, 0, 3, 5, 0, 3, 4}, // 西
            {1, 2, 5, 1, 2, 4, 1, 3, 4, 1, 3, 5}  // 东
    };
    private static final float THICKNESS = 1.0F / 16.0F;
    private static final Map<BlockState, CachedSprites> SPRITE_CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockState, CachedSprites> eldest) {
            return size() > MAX_CACHED_STATES;
        }
    };
    private final RandomSource spriteRandom = RandomSource.create();
    // BER 在渲染线程依次调用，复用工作数组，避免为每个方块、每一帧创建临时容器。
    private final float[] bounds = new float[6];
    private final int[] colors = new int[6];

    @SubscribeEvent
    public static void onModelsBaked(ModelEvent.BakingCompleted event) {
        SPRITE_CACHE.clear();
    }

    private record CachedSprites(BakedModel model, SpriteInfo[] faces) {
    }

    public CopycatBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    private record SpriteInfo(TextureAtlasSprite sprite, int tintIndex) {
    }

    private SpriteInfo getSpriteForDirection(BakedModel model, BlockState state, Direction dir) {
        RandomSource random = spriteRandom;
        random.setSeed(42L);
        List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model.getQuads(state, dir, random, net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        if (!quads.isEmpty()) {
            return new SpriteInfo(quads.get(0).getSprite(), quads.get(0).getTintIndex());
        }
        quads = model.getQuads(state, null, random, net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        if (!quads.isEmpty()) {
            return new SpriteInfo(quads.get(0).getSprite(), quads.get(0).getTintIndex());
        }
        return new SpriteInfo(model.getParticleIcon(), -1);
    }

    private SpriteInfo[] getSprites(BakedModel model, BlockState state) {
        // 仅缓存确定的静态模型；动态模型仍实时读取，避免缓存过期的外观。
        boolean cacheable = model.getClass() == SimpleBakedModel.class;
        if (cacheable) {
            CachedSprites cached = SPRITE_CACHE.get(state);
            if (cached != null && cached.model() == model) {
                return cached.faces();
            }
        }
        SpriteInfo[] faces = new SpriteInfo[DIRECTIONS.length];
        for (Direction direction : DIRECTIONS) {
            faces[direction.ordinal()] = getSpriteForDirection(model, state, direction);
        }
        if (cacheable) {
            SPRITE_CACHE.put(state, new CachedSprites(model, faces));
        }
        return faces;
    }

    private int getFaceColor(SpriteInfo info, BlockState state, CopycatBlockEntity blockEntity) {
        return info.tintIndex == -1 ? 0xFFFFFF : Minecraft.getInstance().getBlockColors()
                .getColor(state, blockEntity.getLevel(), blockEntity.getBlockPos(), info.tintIndex);
    }

    @Override
    public void render(CopycatBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getDisguiseState();
        if (state == null || state.isAir()) return;
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        SpriteInfo[] faces = getSprites(model, state);
        Direction attachment = blockEntity.getBlockState().getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
        updateBounds(attachment);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutout());
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        for (int i = 0; i < RENDER_ORDER.length; i++) {
            Direction face = RENDER_ORDER[i];
            SpriteInfo info = faces[face.ordinal()];
            int color = 0;
            boolean reused = false;
            // 同一个染色索引只查询一次，但不跨方块缓存受生物群系影响的颜色。
            for (int j = 0; j < i; j++) {
                if (faces[RENDER_ORDER[j].ordinal()].tintIndex == info.tintIndex) {
                    color = colors[j];
                    reused = true;
                    break;
                }
            }
            if (!reused) color = getFaceColor(info, state, blockEntity);
            colors[i] = color;
            renderFace(poseStack.last(), buffer, face, attachment, info.sprite, color, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private void updateBounds(Direction attachment) {
        for (int axis = 0; axis < 3; axis++) {
            bounds[axis * 2] = -0.5F;
            bounds[axis * 2 + 1] = 0.5F;
        }
        switch (attachment) {
            case UP -> bounds[3] = -0.5F + THICKNESS;
            case DOWN -> bounds[2] = 0.5F - THICKNESS;
            case NORTH -> bounds[4] = 0.5F - THICKNESS;
            case SOUTH -> bounds[5] = -0.5F + THICKNESS;
            case WEST -> bounds[0] = 0.5F - THICKNESS;
            case EAST -> bounds[1] = -0.5F + THICKNESS;
        }
    }

    private void renderFace(PoseStack.Pose pose, VertexConsumer buffer, Direction face, Direction attachment,
                            TextureAtlasSprite sprite, int color, int light, int overlay) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        // 薄边只截取原贴图的 1/16，保留原先各个附着方向的纹理裁切方式。
        switch (cropFor(attachment, face)) {
            case LEFT -> u1 = u0 + (u1 - u0) * THICKNESS;
            case RIGHT -> u0 = u1 - (u1 - u0) * THICKNESS;
            case TOP -> v1 = v0 + (v1 - v0) * THICKNESS;
            case BOTTOM -> v0 = v1 - (v1 - v0) * THICKNESS;
            case FULL -> { }
        }
        float shade = switch (face) {
            case UP -> 1.0F;
            case DOWN -> 0.5F;
            case WEST, EAST -> 0.6F;
            case NORTH, SOUTH -> 0.8F;
        };
        float r = ((color >> 16) & 255) / 255F * shade;
        float g = ((color >> 8) & 255) / 255F * shade;
        float b = (color & 255) / 255F * shade;
        int[] vertices = FACE_VERTICES[face.ordinal()];
        for (int i = 0; i < 4; i++) {
            buffer.vertex(pose.pose(), bounds[vertices[i * 3]], bounds[vertices[i * 3 + 1]], bounds[vertices[i * 3 + 2]])
                    .color(r, g, b, 1.0F).uv(i == 0 || i == 3 ? u0 : u1, i < 2 ? v1 : v0)
                    .overlayCoords(overlay).uv2(light)
                    .normal(pose.normal(), face.getStepX(), face.getStepY(), face.getStepZ()).endVertex();
        }
    }

    private enum Crop { FULL, LEFT, RIGHT, TOP, BOTTOM }

    private static Crop cropFor(Direction attachment, Direction face) {
        return switch (attachment) {
            case UP -> face.getAxis().isHorizontal() ? Crop.BOTTOM : Crop.FULL;
            case DOWN -> face.getAxis().isHorizontal() ? Crop.TOP : Crop.FULL;
            case NORTH -> switch (face) {
                case UP -> Crop.BOTTOM;
                case DOWN -> Crop.TOP;
                case WEST -> Crop.LEFT;
                case EAST -> Crop.RIGHT;
                default -> Crop.FULL;
            };
            case SOUTH -> switch (face) {
                case UP -> Crop.TOP;
                case DOWN -> Crop.BOTTOM;
                case WEST -> Crop.RIGHT;
                case EAST -> Crop.LEFT;
                default -> Crop.FULL;
            };
            case WEST -> switch (face) {
                case UP, DOWN, NORTH -> Crop.LEFT;
                case SOUTH -> Crop.RIGHT;
                default -> Crop.FULL;
            };
            case EAST -> switch (face) {
                case UP, DOWN, NORTH -> Crop.RIGHT;
                case SOUTH -> Crop.LEFT;
                default -> Crop.FULL;
            };
        };
    }
}

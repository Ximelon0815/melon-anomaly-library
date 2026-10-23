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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MelonAnomalyLibrary.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CopycatBlockEntityRenderer implements BlockEntityRenderer<CopycatBlockEntity> {

    private static final int MAX_CACHED_STATES = 256;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<BlockState, CachedSprites> SPRITE_CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockState, CachedSprites> eldest) {
            return size() > MAX_CACHED_STATES;
        }
    };
    private final RandomSource spriteRandom = RandomSource.create();

    @SubscribeEvent
    public static void onModelsBaked(ModelEvent.BakingCompleted event) {
        SPRITE_CACHE.clear();
    }

    private record CachedSprites(BakedModel model, SpriteInfo[] faces) {
    }

    public CopycatBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static class SpriteInfo {
        public final TextureAtlasSprite sprite;
        public final int tintIndex;
        public SpriteInfo(TextureAtlasSprite sprite, int tintIndex) {
            this.sprite = sprite;
            this.tintIndex = tintIndex;
        }
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
        // Custom models may depend on state outside their BlockState, so only cache vanilla static models.
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
    public void render(CopycatBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState disguiseState = blockEntity.getDisguiseState();

        if (disguiseState != null && !disguiseState.isAir()) {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(disguiseState);
            Direction dir = blockEntity.getBlockState().getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
            
            SpriteInfo[] faces = getSprites(model, disguiseState);
            SpriteInfo upInfo = faces[Direction.UP.ordinal()];
            SpriteInfo downInfo = faces[Direction.DOWN.ordinal()];
            SpriteInfo northInfo = faces[Direction.NORTH.ordinal()];
            SpriteInfo southInfo = faces[Direction.SOUTH.ordinal()];
            SpriteInfo westInfo = faces[Direction.WEST.ordinal()];
            SpriteInfo eastInfo = faces[Direction.EAST.ordinal()];

            TextureAtlasSprite upSprite = upInfo.sprite;
            TextureAtlasSprite downSprite = downInfo.sprite;
            TextureAtlasSprite northSprite = northInfo.sprite;
            TextureAtlasSprite southSprite = southInfo.sprite;
            TextureAtlasSprite westSprite = westInfo.sprite;
            TextureAtlasSprite eastSprite = eastInfo.sprite;

            VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutout());
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);

            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();

            float minX = -0.5f, maxX = 0.5f;
            float minY = -0.5f, maxY = 0.5f;
            float minZ = -0.5f, maxZ = 0.5f;
            float th = 1f/16f;

            if (dir == Direction.UP) { maxY = -0.5f + th; }
            else if (dir == Direction.DOWN) { minY = 0.5f - th; }
            else if (dir == Direction.NORTH) { minZ = 0.5f - th; }
            else if (dir == Direction.SOUTH) { maxZ = -0.5f + th; }
            else if (dir == Direction.WEST) { minX = 0.5f - th; }
            else if (dir == Direction.EAST) { maxX = -0.5f + th; }

            float upU0 = upSprite.getU0(), upU1 = upSprite.getU1(), upV0 = upSprite.getV0(), upV1 = upSprite.getV1();
            float downU0 = downSprite.getU0(), downU1 = downSprite.getU1(), downV0 = downSprite.getV0(), downV1 = downSprite.getV1();
            float northU0 = northSprite.getU0(), northU1 = northSprite.getU1(), northV0 = northSprite.getV0(), northV1 = northSprite.getV1();
            float southU0 = southSprite.getU0(), southU1 = southSprite.getU1(), southV0 = southSprite.getV0(), southV1 = southSprite.getV1();
            float westU0 = westSprite.getU0(), westU1 = westSprite.getU1(), westV0 = westSprite.getV0(), westV1 = westSprite.getV1();
            float eastU0 = eastSprite.getU0(), eastU1 = eastSprite.getU1(), eastV0 = eastSprite.getV0(), eastV1 = eastSprite.getV1();

            if (dir == Direction.UP) {
                northV0 = northV1 - (northV1-northV0)*th;
                southV0 = southV1 - (southV1-southV0)*th;
                westV0 = westV1 - (westV1-westV0)*th;
                eastV0 = eastV1 - (eastV1-eastV0)*th;
            } else if (dir == Direction.DOWN) {
                northV1 = northV0 + (northV1-northV0)*th;
                southV1 = southV0 + (southV1-southV0)*th;
                westV1 = westV0 + (westV1-westV0)*th;
                eastV1 = eastV0 + (eastV1-eastV0)*th;
            } else if (dir == Direction.NORTH) {
                upV0 = upV1 - (upV1-upV0)*th;
                downV1 = downV0 + (downV1-downV0)*th;
                westU1 = westU0 + (westU1-westU0)*th;
                eastU0 = eastU1 - (eastU1-eastU0)*th;
            } else if (dir == Direction.SOUTH) {
                upV1 = upV0 + (upV1-upV0)*th;
                downV0 = downV1 - (downV1-downV0)*th;
                westU0 = westU1 - (westU1-westU0)*th;
                eastU1 = eastU0 + (eastU1-eastU0)*th;
            } else if (dir == Direction.WEST) {
                upU1 = upU0 + (upU1-upU0)*th;
                downU1 = downU0 + (downU1-downU0)*th;
                northU1 = northU0 + (northU1-northU0)*th;
                southU0 = southU1 - (southU1-southU0)*th;
            } else if (dir == Direction.EAST) {
                upU0 = upU1 - (upU1-upU0)*th;
                downU0 = downU1 - (downU1-downU0)*th;
                northU0 = northU1 - (northU1-northU0)*th;
                southU1 = southU0 + (southU1-southU0)*th;
            }

            int upColor = getFaceColor(upInfo, disguiseState, blockEntity);
            float rUp = ((upColor >> 16) & 0xFF) / 255f;
            float gUp = ((upColor >> 8) & 0xFF) / 255f;
            float bUp = (upColor & 0xFF) / 255f;

            int downColor = downInfo.tintIndex == upInfo.tintIndex ? upColor : getFaceColor(downInfo, disguiseState, blockEntity);
            float rDown = ((downColor >> 16) & 0xFF) / 255f;
            float gDown = ((downColor >> 8) & 0xFF) / 255f;
            float bDown = (downColor & 0xFF) / 255f;

            int northColor = northInfo.tintIndex == upInfo.tintIndex ? upColor
                    : northInfo.tintIndex == downInfo.tintIndex ? downColor : getFaceColor(northInfo, disguiseState, blockEntity);
            float rNorth = ((northColor >> 16) & 0xFF) / 255f;
            float gNorth = ((northColor >> 8) & 0xFF) / 255f;
            float bNorth = (northColor & 0xFF) / 255f;

            int southColor = southInfo.tintIndex == upInfo.tintIndex ? upColor
                    : southInfo.tintIndex == northInfo.tintIndex ? northColor : getFaceColor(southInfo, disguiseState, blockEntity);
            float rSouth = ((southColor >> 16) & 0xFF) / 255f;
            float gSouth = ((southColor >> 8) & 0xFF) / 255f;
            float bSouth = (southColor & 0xFF) / 255f;

            int westColor = westInfo.tintIndex == upInfo.tintIndex ? upColor
                    : westInfo.tintIndex == southInfo.tintIndex ? southColor : getFaceColor(westInfo, disguiseState, blockEntity);
            float rWest = ((westColor >> 16) & 0xFF) / 255f;
            float gWest = ((westColor >> 8) & 0xFF) / 255f;
            float bWest = (westColor & 0xFF) / 255f;

            int eastColor = eastInfo.tintIndex == upInfo.tintIndex ? upColor
                    : eastInfo.tintIndex == westInfo.tintIndex ? westColor : getFaceColor(eastInfo, disguiseState, blockEntity);
            float rEast = ((eastColor >> 16) & 0xFF) / 255f;
            float gEast = ((eastColor >> 8) & 0xFF) / 255f;
            float bEast = (eastColor & 0xFF) / 255f;

            // UP (0,1,0)
            addQuad(pose, normal, buffer, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, upU0, upU1, upV0, upV1, packedLight, packedOverlay, 0, 1, 0, rUp, gUp, bUp, 1.0f);
            // DOWN (0,-1,0)
            addQuad(pose, normal, buffer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, downU0, downU1, downV0, downV1, packedLight, packedOverlay, 0, -1, 0, rDown, gDown, bDown, 0.5f);
            // NORTH (0,0,-1)
            addQuad(pose, normal, buffer, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, northU0, northU1, northV0, northV1, packedLight, packedOverlay, 0, 0, -1, rNorth, gNorth, bNorth, 0.8f);
            // SOUTH (0,0,1)
            addQuad(pose, normal, buffer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, southU0, southU1, southV0, southV1, packedLight, packedOverlay, 0, 0, 1, rSouth, gSouth, bSouth, 0.8f);
            // WEST (-1,0,0)
            addQuad(pose, normal, buffer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, westU0, westU1, westV0, westV1, packedLight, packedOverlay, -1, 0, 0, rWest, gWest, bWest, 0.6f);
            // EAST (1,0,0)
            addQuad(pose, normal, buffer, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, eastU0, eastU1, eastV0, eastV1, packedLight, packedOverlay, 1, 0, 0, rEast, gEast, bEast, 0.6f);

            poseStack.popPose();
        }
    }

    private void addQuad(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u0, float u1, float v0, float v1, int packedLight, int packedOverlay, float nx, float ny, float nz, float r, float g, float b, float shade) {
        float finalR = r * shade;
        float finalG = g * shade;
        float finalB = b * shade;
        buffer.vertex(pose, x1, y1, z1).color(finalR, finalG, finalB, 1.0f).uv(u0, v1).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        buffer.vertex(pose, x2, y2, z2).color(finalR, finalG, finalB, 1.0f).uv(u1, v1).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        buffer.vertex(pose, x3, y3, z3).color(finalR, finalG, finalB, 1.0f).uv(u1, v0).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
        buffer.vertex(pose, x4, y4, z4).color(finalR, finalG, finalB, 1.0f).uv(u0, v0).overlayCoords(packedOverlay).uv2(packedLight).normal(normal, nx, ny, nz).endVertex();
    }
}

package com.melon.anomaly;

import net.minecraft.core.Direction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/** 不启动游戏窗口，对照重构前的六面顶点和贴图裁切检查全部 36 种组合。 */
public final class CopycatGeometryRegression {
    public static void main(String[] args) throws Exception {
        var renderer = new CopycatBlockEntityRenderer(null);
        Class<?> type = CopycatBlockEntityRenderer.class;
        Method update = type.getDeclaredMethod("updateBounds", Direction.class);
        Method crop = type.getDeclaredMethod("cropFor", Direction.class, Direction.class);
        Field boundsField = type.getDeclaredField("bounds");
        Field verticesField = type.getDeclaredField("FACE_VERTICES");
        update.setAccessible(true);
        crop.setAccessible(true);
        boundsField.setAccessible(true);
        verticesField.setAccessible(true);
        int[][] indices = (int[][]) verticesField.get(null);
        // 顺序为下、上、北、南、西、东；数据来自重构前各附着方向的 UV 分支。
        String[][] crops = {
                {"FULL", "FULL", "TOP", "TOP", "TOP", "TOP"},
                {"FULL", "FULL", "BOTTOM", "BOTTOM", "BOTTOM", "BOTTOM"},
                {"TOP", "BOTTOM", "FULL", "FULL", "LEFT", "RIGHT"},
                {"BOTTOM", "TOP", "FULL", "FULL", "RIGHT", "LEFT"},
                {"LEFT", "LEFT", "LEFT", "RIGHT", "FULL", "FULL"},
                {"RIGHT", "RIGHT", "RIGHT", "LEFT", "FULL", "FULL"}
        };
        for (Direction attachment : Direction.values()) {
            update.invoke(renderer, attachment);
            float[] bounds = (float[]) boundsField.get(renderer);
            for (Direction face : Direction.values()) {
                float[] actual = new float[12];
                for (int i = 0; i < actual.length; i++) actual[i] = bounds[indices[face.ordinal()][i]];
                if (!Arrays.equals(actual, originalVertices(attachment, face))) {
                    throw new AssertionError("顶点或绕序变化：" + attachment + "/" + face);
                }
                if (!crop.invoke(null, attachment, face).toString().equals(crops[attachment.ordinal()][face.ordinal()])) {
                    throw new AssertionError("贴图裁切变化：" + attachment + "/" + face);
                }
            }
        }
        System.out.println("苔藓渲染几何回归：36 种附着面/渲染面组合全部通过");
    }

    // 保留旧实现的坐标表达式作为对照，不复用待测代码的顶点索引。
    private static float[] originalVertices(Direction attachment, Direction face) {
        float minX = -0.5F, maxX = 0.5F, minY = -0.5F, maxY = 0.5F, minZ = -0.5F, maxZ = 0.5F;
        switch (attachment) {
            case UP -> maxY = -0.5F + 1F / 16F;
            case DOWN -> minY = 0.5F - 1F / 16F;
            case NORTH -> minZ = 0.5F - 1F / 16F;
            case SOUTH -> maxZ = -0.5F + 1F / 16F;
            case WEST -> minX = 0.5F - 1F / 16F;
            case EAST -> maxX = -0.5F + 1F / 16F;
        }
        return switch (face) {
            case UP -> new float[]{minX,maxY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ, minX,maxY,minZ};
            case DOWN -> new float[]{minX,minY,minZ, maxX,minY,minZ, maxX,minY,maxZ, minX,minY,maxZ};
            case NORTH -> new float[]{maxX,minY,minZ, minX,minY,minZ, minX,maxY,minZ, maxX,maxY,minZ};
            case SOUTH -> new float[]{minX,minY,maxZ, maxX,minY,maxZ, maxX,maxY,maxZ, minX,maxY,maxZ};
            case WEST -> new float[]{minX,minY,minZ, minX,minY,maxZ, minX,maxY,maxZ, minX,maxY,minZ};
            case EAST -> new float[]{maxX,minY,maxZ, maxX,minY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ};
        };
    }
}

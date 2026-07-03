package com.chuanshuoi9.client.render;

import com.chuanshuoi9.tile.TileTrainDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class TileTrainDisplayRenderer extends TileEntitySpecialRenderer<TileTrainDisplay> {
    @Override
    public void render(TileTrainDisplay te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) {
            return;
        }
        if (!te.isController()) {
            return;
        }
        EnumFacing face = te.getDisplayFace();
        if (!face.getAxis().isHorizontal()) {
            return;
        }

        BlockPos min = te.getMinPos();
        BlockPos max = te.getMaxPos();
        if (min == null || max == null) {
            return;
        }

        int widthBlocks = Math.max(1, te.getWidthBlocks());
        int heightBlocks = Math.max(1, te.getHeightBlocks());
        int widthPx = widthBlocks * 16;
        int heightPx = heightBlocks * 16;

        List<String> lines = te.getRenderLines();
        if (lines.isEmpty()) {
            lines = java.util.Collections.singletonList("未配置");
        }

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int fontH = fr.FONT_HEIGHT;
        int maxLines = Math.max(1, (heightPx / (fontH + 1)) * 2);
        int drawCount = Math.min(lines.size(), maxLines);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        applyFaceTransform(te.getPos(), min, max, face);
        GlStateManager.translate(0.0, heightBlocks, 0.0);
        GlStateManager.scale(1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f);
        GlStateManager.scale(0.5f, 0.5f, 1.0f);

        float prevLightX = OpenGlHelper.lastBrightnessX;
        float prevLightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        for (int i = 0; i < drawCount; i++) {
            String s = lines.get(i);
            if (s == null) {
                continue;
            }
            int scale = 2;
            int widthLimit = Math.max(1, (widthPx - 4) * scale);
            String trimmed = fr.trimStringToWidth(s, widthLimit);
            int x0 = 2 * scale;
            int y0 = 2 * scale + i * (fontH + 1) * scale;
            fr.drawString(trimmed, x0, y0, 0xFFFFFFFF, true);
        }

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevLightX, prevLightY);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void applyFaceTransform(BlockPos controllerPos, BlockPos min, BlockPos max, EnumFacing face) {
        double outset = 0.02;
        double ox;
        double oy = min.getY();
        double oz;
        float yaw;

        if (face == EnumFacing.NORTH) {
            yaw = 180.0f;
            ox = max.getX() + 1;
            oz = min.getZ() - outset;
        } else if (face == EnumFacing.SOUTH) {
            yaw = 0.0f;
            ox = min.getX();
            oz = max.getZ() + 1 + outset;
        } else if (face == EnumFacing.EAST) {
            yaw = 90.0f;
            ox = max.getX() + 1 + outset;
            oz = min.getZ();
        } else if (face == EnumFacing.WEST) {
            yaw = -90.0f;
            ox = min.getX() - outset;
            oz = max.getZ() + 1;
        } else {
            return;
        }

        GlStateManager.translate(ox - controllerPos.getX(), oy - controllerPos.getY(), oz - controllerPos.getZ());
        GlStateManager.rotate(yaw, 0.0f, 1.0f, 0.0f);
    }
}

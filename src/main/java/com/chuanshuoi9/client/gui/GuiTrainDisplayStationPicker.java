package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.client.map.RailMapClientCache;
import com.chuanshuoi9.network.StationSyncMessage.StationData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class GuiTrainDisplayStationPicker extends GuiScreen {
    private static final double MIN_RANGE = 50.0;
    private static final double MAX_RANGE = 10000.0;
    private static final double MAX_PAN = 500000.0;

    private final GuiTrainDisplayConfig parent;
    private BlockPos selected;

    private double viewRange = 1000.0;
    private double centerX = 0.0;
    private double centerZ = 0.0;

    private boolean draggingMap = false;
    private int lastDragMouseX;
    private int lastDragMouseY;
    private String status = "";

    public GuiTrainDisplayStationPicker(GuiTrainDisplayConfig parent, BlockPos selected) {
        this.parent = parent;
        this.selected = selected;
    }

    @Override
    public void initGui() {
        if (mc != null && mc.player != null) {
            if (selected != null) {
                centerX = selected.getX() + 0.5;
                centerZ = selected.getZ() + 0.5;
            } else {
                centerX = mc.player.posX;
                centerZ = mc.player.posZ;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (dWheel > 0) {
                viewRange *= 0.8;
            } else {
                viewRange *= 1.25;
            }
            viewRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE, viewRange));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "选择车站坐标", width / 2, 10, 0xFFFFFF);
        drawCenteredString(fontRenderer, "左键选择站台标记点(黄色)，右键拖动平移，滚轮缩放，ESC 返回", width / 2, 24, 0xAAAAAA);
        drawCenteredString(fontRenderer, String.format("范围: %.0f x %.0f", viewRange, viewRange), width / 2, 34, 0xFFFFFF);

        int top = 52;
        int mapSize = Math.max(1, Math.min(width - 60, height - top - 30));
        int left = (width - mapSize) / 2;
        int right = left + mapSize;
        int bottom = top + mapSize;
        drawRect(left, top, right, bottom, 0xCC101010);
        drawRect(left - 1, top - 1, right + 1, top, 0xFF666666);
        drawRect(left - 1, bottom, right + 1, bottom + 1, 0xFF666666);
        drawRect(left - 1, top, left, bottom, 0xFF666666);
        drawRect(right, top, right + 1, bottom, 0xFF666666);

        Set<BlockPos> rails = RailMapClientCache.currentDimensionRails();
        List<StationData> stations = RailMapClientCache.getStations();
        if (rails.isEmpty() && stations.isEmpty()) {
            drawCenteredString(fontRenderer, "没有站台标记数据。请先用站台标记器放置站台。", width / 2, height / 2, 0xFF7777);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        World world = Minecraft.getMinecraft().world;
        if (world == null || Minecraft.getMinecraft().player == null) {
            drawCenteredString(fontRenderer, "世界未加载", width / 2, height / 2, 0xFF7777);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        BlockPos playerPos = Minecraft.getMinecraft().player.getPosition();
        double minX = centerX - viewRange / 2;
        double maxX = centerX + viewRange / 2;
        double minZ = centerZ - viewRange / 2;
        double maxZ = centerZ + viewRange / 2;

        double drawW = Math.max(1, right - left - 8);
        double drawH = Math.max(1, bottom - top - 8);
        double scaleX = drawW / viewRange;
        double scaleZ = drawH / viewRange;

        double rectSizeX = Math.max(1.0, scaleX);
        double rectSizeZ = Math.max(1.0, scaleZ);

        Set<BlockPos> signals = RailMapClientCache.currentDimensionSignalRails();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();

        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int visibleRails = 0;
        for (BlockPos pos : rails) {
            int xw = pos.getX();
            int zw = pos.getZ();
            if (xw < minX || xw > maxX || zw < minZ || zw > maxZ) continue;
            if (signals.contains(pos)) continue;

            double x = left + 4 + (xw - minX) * scaleX;
            double y = top + 4 + (zw - minZ) * scaleZ;

            bufferbuilder.pos(x, y + rectSizeZ, 0.0D).color(0.0F, 0.82F, 0.5F, 1.0F).endVertex();
            bufferbuilder.pos(x + rectSizeX, y + rectSizeZ, 0.0D).color(0.0F, 0.82F, 0.5F, 1.0F).endVertex();
            bufferbuilder.pos(x + rectSizeX, y, 0.0D).color(0.0F, 0.82F, 0.5F, 1.0F).endVertex();
            bufferbuilder.pos(x, y, 0.0D).color(0.0F, 0.82F, 0.5F, 1.0F).endVertex();
            visibleRails++;
        }
        tessellator.draw();

        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (BlockPos pos : signals) {
            int xw = pos.getX();
            int zw = pos.getZ();
            if (xw < minX || xw > maxX || zw < minZ || zw > maxZ) continue;

            double x = left + 4 + (xw - minX) * scaleX;
            double y = top + 4 + (zw - minZ) * scaleZ;

            bufferbuilder.pos(x, y + rectSizeZ, 0.0D).color(1.0F, 0.25F, 0.25F, 1.0F).endVertex();
            bufferbuilder.pos(x + rectSizeX, y + rectSizeZ, 0.0D).color(1.0F, 0.25F, 0.25F, 1.0F).endVertex();
            bufferbuilder.pos(x + rectSizeX, y, 0.0D).color(1.0F, 0.25F, 0.25F, 1.0F).endVertex();
            bufferbuilder.pos(x, y, 0.0D).color(1.0F, 0.25F, 0.25F, 1.0F).endVertex();
            visibleRails++;
        }
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        int px = (int) (left + 4 + (playerPos.getX() - minX) * scaleX);
        int py = (int) (top + 4 + (playerPos.getZ() - minZ) * scaleZ);
        if (px >= left && px <= right && py >= top && py <= bottom) {
            drawRect(px - 2, py - 2, px + 3, py + 3, 0xFFFF4040);
        }

        int cx = (int) (left + 4 + (centerX - minX) * scaleX);
        int cy = (int) (top + 4 + (centerZ - minZ) * scaleZ);
        if (cx >= left && cx <= right && cy >= top && cy <= bottom) {
            drawRect(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        }

        if (selected != null) {
            int sx = (int) (left + 4 + (selected.getX() - minX) * scaleX);
            int sy = (int) (top + 4 + (selected.getZ() - minZ) * scaleZ);
            if (sx >= left && sx <= right && sy >= top && sy <= bottom) {
                drawRect(sx - 3, sy - 3, sx + 4, sy + 4, 0xFFFF4040);
            }
        }

        StationData hoveredStation = null;
        for (StationData station : stations) {
            if (station.x < minX || station.x > maxX || station.z < minZ || station.z > maxZ) continue;
            int sx = (int) (left + 4 + (station.x - minX) * scaleX);
            int sy = (int) (top + 4 + (station.z - minZ) * scaleZ);
            drawRect(sx - 2, sy - 2, sx + 3, sy + 3, 0xFFFFFF00);

            if (mouseX >= sx - 3 && mouseX <= sx + 3 && mouseY >= sy - 3 && mouseY <= sy + 3) {
                hoveredStation = station;
            }
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, width / 2, bottom + 6, 0xFFFF66);
        } else {
            drawString(fontRenderer, "显示点数: " + visibleRails + " / 轨道总计: " + rails.size(), left + 6, bottom + 6, 0xFFFFFF);
        }

        if (hoveredStation != null) {
            drawHoveringText(hoveredStation.name + " (" + hoveredStation.x + ", " + hoveredStation.y + ", " + hoveredStation.z + ")", mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 1 && isInsideMap(mouseX, mouseY)) {
            draggingMap = true;
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (mouseButton == 0) {
            StationData picked = pickStationByMouse(mouseX, mouseY);
            if (picked != null) {
                selected = new BlockPos(picked.x, picked.y, picked.z);
                parent.applyPickedStation(picked);
                mc.displayGuiScreen(parent);
                return;
            }
            status = "只能在站台标记点上选择";
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingMap && clickedMouseButton == 1) {
            int dx = mouseX - lastDragMouseX;
            int dy = mouseY - lastDragMouseY;
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;

            int top = 52;
            int mapSize = Math.max(1, Math.min(width - 60, height - top - 30));
            int left = (width - mapSize) / 2;
            int right = left + mapSize;
            int bottom = top + mapSize;
            double drawW = Math.max(1, right - left - 8);
            double drawH = Math.max(1, bottom - top - 8);
            double scaleX = drawW / viewRange;
            double scaleZ = drawH / viewRange;

            centerX -= dx / scaleX;
            centerZ -= dy / scaleZ;

            centerX = Math.max(-MAX_PAN, Math.min(MAX_PAN, centerX));
            centerZ = Math.max(-MAX_PAN, Math.min(MAX_PAN, centerZ));
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingMap = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean isInsideMap(int mouseX, int mouseY) {
        int top = 52;
        int mapSize = Math.max(1, Math.min(width - 60, height - top - 30));
        int left = (width - mapSize) / 2;
        int right = left + mapSize;
        int bottom = top + mapSize;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private StationData pickStationByMouse(int mouseX, int mouseY) {
        int top = 52;
        int mapSize = Math.max(1, Math.min(width - 60, height - top - 30));
        int left = (width - mapSize) / 2;
        int right = left + mapSize;
        int bottom = top + mapSize;

        double minX = centerX - viewRange / 2;
        double maxX = centerX + viewRange / 2;
        double minZ = centerZ - viewRange / 2;
        double maxZ = centerZ + viewRange / 2;
        double drawW = Math.max(1, right - left - 8);
        double drawH = Math.max(1, bottom - top - 8);
        double scaleX = drawW / viewRange;
        double scaleZ = drawH / viewRange;

        List<StationData> stations = RailMapClientCache.getStations();
        for (StationData station : stations) {
            if (station.x < minX || station.x > maxX || station.z < minZ || station.z > maxZ) continue;
            int sx = (int) (left + 4 + (station.x - minX) * scaleX);
            int sy = (int) (top + 4 + (station.z - minZ) * scaleZ);
            if (mouseX >= sx - 3 && mouseX <= sx + 3 && mouseY >= sy - 3 && mouseY <= sy + 3) {
                return station;
            }
        }
        return null;
    }
}


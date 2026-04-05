package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.TrainManagerClientCache;
import com.chuanshuoi9.client.map.RailMapClientCache;
import com.chuanshuoi9.network.TrainPositionsRequestMessage;
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

public class GuiRailwayMap extends GuiScreen {
    private static final double MIN_RANGE = 50.0;
    private static final double MAX_RANGE = 10000.0;
    private static final double MAX_PAN = 500000.0;

    private double viewRange = 1000.0;
    private double centerX = 0.0;
    private double centerZ = 0.0;

    private boolean draggingMap = false;
    private int lastDragMouseX;
    private int lastDragMouseY;
    private boolean initializedCenter = false;
    private int ticks;

    @Override
    public void initGui() {
        ticks = 0;
        if (!initializedCenter && mc != null && mc.player != null) {
            centerX = mc.player.posX;
            centerZ = mc.player.posZ;
            initializedCenter = true;
        }
    }

    @Override
    public void updateScreen() {
        ticks++;
        if (ticks % 24 == 0 && mc != null && mc.world != null) {
            IrAutoMod.NETWORK.sendToServer(new TrainPositionsRequestMessage(mc.world.provider.getDimension()));
        }
        super.updateScreen();
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
        drawCenteredString(fontRenderer, "Railway Map", width / 2, 10, 0xFFFFFF);
        drawCenteredString(fontRenderer, "多人服务器：100tick自动同步（放置/破坏铁路会更快）；单人：后台定时扫描（不开地图也刷新）；滚轮缩放/拖拽平移", width / 2, 24, 0xAAAAAA);
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
            drawCenteredString(fontRenderer, "没有地图数据。单人会后台扫描；多人请等待自动同步或手动右键地图。", width / 2, height / 2, 0xFF7777);
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
            drawRect(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
            drawRect(px - 1, py - 1, px + 2, py + 2, 0xFFFF4040);
        }

        long[] trains = TrainManagerClientCache.getTrainPositions(world.provider.getDimension());
        if (trains != null) {
            for (long v : trains) {
                int xw = (int) (v >> 32);
                int zw = (int) v;
                if (xw < minX || xw > maxX || zw < minZ || zw > maxZ) {
                    continue;
                }
                int sx = (int) (left + 4 + (xw - minX) * scaleX);
                int sy = (int) (top + 4 + (zw - minZ) * scaleZ);
                drawRect(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFAA00FF);
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

        drawString(fontRenderer, "显示点数: " + visibleRails + " / 轨道总计: " + rails.size(), left + 6, bottom + 6, 0xFFFFFF);
        
        if (hoveredStation != null) {
            drawHoveringText(hoveredStation.name + " (" + hoveredStation.x + ", " + hoveredStation.y + ", " + hoveredStation.z + ")", mouseX, mouseY);
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if ((mouseButton == 0 || mouseButton == 1) && isInsideMap(mouseX, mouseY)) {
            draggingMap = true;
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingMap && (clickedMouseButton == 0 || clickedMouseButton == 1)) {
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

    private boolean isInsideMap(int mouseX, int mouseY) {
        int top = 52;
        int mapSize = Math.max(1, Math.min(width - 60, height - top - 30));
        int left = (width - mapSize) / 2;
        int right = left + mapSize;
        int bottom = top + mapSize;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

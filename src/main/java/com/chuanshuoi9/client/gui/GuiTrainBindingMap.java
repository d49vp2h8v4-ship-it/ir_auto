package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.network.OpenTrainTimetableGuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiTrainBindingMap extends GuiScreen {
    private static final int SCAN_RANGE = 100;
    private final ItemStack paperStack;
    private List<Entity> nearbyLocomotives = new ArrayList<>();
    private String status = "";

    public GuiTrainBindingMap(ItemStack paperStack) {
        this.paperStack = paperStack;
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshNearbyLocomotives();
    }

    private void refreshNearbyLocomotives() {
        if (mc.world == null || mc.player == null) return;
        
        List<Entity> detected = new ArrayList<>();
        // Create a copy of the list to avoid ConcurrentModificationException
        List<Entity> allEntities = new ArrayList<>(mc.world.loadedEntityList);
        
        for (Entity entity : allEntities) {
            if (entity == null || entity == mc.player) continue;
            
            if (IrTrainReflection.isLocomotive(entity)) {
                double dist = entity.getDistance(mc.player);
                if (dist <= SCAN_RANGE) {
                    detected.add(entity);
                }
            }
        }
        this.nearbyLocomotives = detected;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.world != null && mc.world.getTotalWorldTime() % 20 == 0) {
            refreshNearbyLocomotives();
        }
        drawDefaultBackground();
        
        int centerX_Screen = width / 2;
        int centerY_Screen = height / 2;
        
        drawCenteredString(fontRenderer, "机车绑定地图 (范围: " + SCAN_RANGE + "格)", centerX_Screen, 10, 0xFFFFFF);
        drawCenteredString(fontRenderer, "红点: 机车 | 白点: 你", centerX_Screen, 22, 0xAAAAAA);
        if (mc.world != null) {
            drawString(fontRenderer, "已加载实体: " + mc.world.loadedEntityList.size() + " | 机车: " + nearbyLocomotives.size(), 6, 6, 0xAAAAAA);
        }

        int mapSize = Math.min(width, height) - 80;
        int left = centerX_Screen - mapSize / 2;
        int top = centerY_Screen - mapSize / 2 + 10;
        int right = left + mapSize;
        int bottom = top + mapSize;
        
        // Draw Map Background
        drawRect(left, top, right, bottom, 0xEE050505);
        drawRect(left - 1, top - 1, right + 1, top, 0xFF555555);
        drawRect(left - 1, bottom, right + 1, bottom + 1, 0xFF555555);
        drawRect(left - 1, top, left, bottom, 0xFF555555);
        drawRect(right, top, right + 1, bottom, 0xFF555555);

        if (mc.player == null) return;
        
        double playerX = mc.player.posX;
        double playerZ = mc.player.posZ;
        
        double scale = (mapSize / 2.0) / SCAN_RANGE;

        // Draw Crosshair at Player Position (Center of map)
        int mapCenterX = left + mapSize / 2;
        int mapCenterY = top + mapSize / 2;
        
        // Draw Range Circle (Optional, but helps visualize)
        drawCircle(mapCenterX, mapCenterY, (int)(SCAN_RANGE * scale), 0x22FFFFFF);

        // Draw Player
        drawRect(mapCenterX - 2, mapCenterY - 2, mapCenterX + 2, mapCenterY + 2, 0xFFFFFFFF);

        Entity hoveredEntity = null;
        for (Entity entity : nearbyLocomotives) {
            int dx = (int)((entity.posX - playerX) * scale);
            int dz = (int)((entity.posZ - playerZ) * scale);
            int x = mapCenterX + dx;
            int y = mapCenterY + dz;

            // Only draw if within map bounds (should be true if distance <= SCAN_RANGE)
            if (x >= left && x <= right && y >= top && y <= bottom) {
                boolean isHovered = mouseX >= x - 4 && mouseX <= x + 4 && mouseY >= y - 4 && mouseY <= y + 4;
                if (isHovered) {
                    hoveredEntity = entity;
                    drawRect(x - 5, y - 5, x + 5, y + 5, 0xFFFFFF00); // Yellow highlight
                }
                
                int color = 0xFFFF4444; // Uniform locomotive color
                drawRect(x - 3, y - 3, x + 3, y + 3, color);
            }
        }

        if (hoveredEntity != null) {
            String name = hoveredEntity.getName();
            int nameWidth = fontRenderer.getStringWidth(name);
            drawRect(mouseX + 5, mouseY - 15, mouseX + 15 + nameWidth, mouseY, 0xAA000000);
            drawString(fontRenderer, name, mouseX + 10, mouseY - 12, 0xFFFFFF);
            
            String dist = String.format("%.1f m", hoveredEntity.getDistance(mc.player));
            drawString(fontRenderer, dist, mouseX + 10, mouseY + 5, 0xAAAAAA);
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, centerX_Screen, bottom + 15, 0xFFFF66);
        } else if (nearbyLocomotives.isEmpty()) {
            drawCenteredString(fontRenderer, "未检测到附近的机车 (100格范围内)", centerX_Screen, bottom + 15, 0xFF7777);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCircle(int x, int y, int radius, int color) {
        int d = 0;
        int r = radius;
        int x1 = 0;
        int y1 = r;
        int p = 3 - 2 * r;
        while (x1 <= y1) {
            drawRect(x + x1, y + y1, x + x1 + 1, y + y1 + 1, color);
            drawRect(x - x1, y + y1, x - x1 + 1, y + y1 + 1, color);
            drawRect(x + x1, y - y1, x + x1 + 1, y - y1 + 1, color);
            drawRect(x - x1, y - y1, x - x1 + 1, y - y1 + 1, color);
            drawRect(x + y1, y + x1, x + y1 + 1, y + x1 + 1, color);
            drawRect(x - y1, y + x1, x - y1 + 1, y + x1 + 1, color);
            drawRect(x + y1, y - x1, x + y1 + 1, y - x1 + 1, color);
            drawRect(x - y1, y - x1, x - y1 + 1, y - x1 + 1, color);
            if (p < 0) {
                p = p + 4 * x1 + 6;
            } else {
                p = p + 4 * (x1 - y1) + 10;
                y1--;
            }
            x1++;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            Entity selected = pickLocomotiveByMouse(mouseX, mouseY);
            if (selected != null) {
                handleTrainSelection(selected);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private Entity pickLocomotiveByMouse(int mouseX, int mouseY) {
        if (mc.player == null) return null;
        
        int centerX_Screen = width / 2;
        int centerY_Screen = height / 2;
        int mapSize = Math.min(width, height) - 80;
        int left = centerX_Screen - mapSize / 2;
        int top = centerY_Screen - mapSize / 2 + 10;
        
        double playerX = mc.player.posX;
        double playerZ = mc.player.posZ;
        double scale = (mapSize / 2.0) / SCAN_RANGE;
        int mapCenterX = left + mapSize / 2;
        int mapCenterY = top + mapSize / 2;

        for (Entity entity : nearbyLocomotives) {
            int dx = (int)((entity.posX - playerX) * scale);
            int dz = (int)((entity.posZ - playerZ) * scale);
            int x = mapCenterX + dx;
            int y = mapCenterY + dz;
            
            if (Math.abs(mouseX - x) <= 6 && Math.abs(mouseY - y) <= 6) {
                return entity;
            }
        }
        return null;
    }

    private void handleTrainSelection(Entity train) {
        // Send packet to server to bind and request open GUI
        // In Agent mode, I should probably implement the network part too if missing.
        // For now, let's use the existing NBT binding logic if possible, but the user wants file storage.
        
        // On client side, we can't easily modify item NBT of the server-side item.
        // We need a network packet.
        
        // I will assume there's a need for a new packet or reuse existing ones.
        // Existing logic: TrainBindingHandler.openTrainGuiFromPaper(player, uuid)
        
        // I'll need a packet to tell the server "I selected this train for this paper".
        IrAutoMod.NETWORK.sendToServer(new com.chuanshuoi9.network.BindTrainFromMapMessage(train.getUniqueID().toString()));
        mc.displayGuiScreen(null); // Close map
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

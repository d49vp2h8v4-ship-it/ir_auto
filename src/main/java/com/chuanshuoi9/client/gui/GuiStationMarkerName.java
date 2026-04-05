package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.network.StationMarkerRenameMessage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;

public class GuiStationMarkerName extends GuiScreen {
    private final BlockPos markerPos;
    private final String initialName;
    private GuiTextField nameField;
    private String status = "";

    public GuiStationMarkerName(BlockPos markerPos, String initialName) {
        this.markerPos = markerPos;
        this.initialName = initialName == null ? "" : initialName;
    }

    @Override
    public void initGui() {
        int left = width / 2 - 150;
        int top = height / 2 - 60;
        nameField = new GuiTextField(1, fontRenderer, left + 10, top + 40, 280, 18);
        nameField.setText(initialName);
        buttonList.clear();
        buttonList.add(new GuiButton(10, left + 10, top + 70, 80, 20, "保存"));
        buttonList.add(new GuiButton(11, left + 100, top + 70, 80, 20, "取消"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 150;
        int top = height / 2 - 60;
        drawRect(left - 1, top - 1, left + 301, top + 111, 0xFF666666);
        drawRect(left, top, left + 300, top + 110, 0xCC101010);
        drawCenteredString(fontRenderer, "修改站名", width / 2, top + 10, 0xFFFFFF);
        drawString(fontRenderer, "站名:", left + 10, top + 28, 0xFFFFFF);
        drawString(fontRenderer, status, left + 10, top + 94, 0xFFFF66);
        nameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 11) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == 10) {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isEmpty()) {
                status = "站名不能为空";
                return;
            }
            IrAutoMod.NETWORK.sendToServer(new StationMarkerRenameMessage(markerPos, name));
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (nameField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}


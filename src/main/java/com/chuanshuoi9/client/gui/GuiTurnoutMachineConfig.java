package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.item.ItemTurnoutMachine;
import com.chuanshuoi9.network.TurnoutConfigMessage;
import com.chuanshuoi9.train.TrainAutoPilotData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiTurnoutMachineConfig extends GuiScreen {
    private final EnumHand hand;
    private final ItemStack initialStack;

    private boolean blacklistMode = true;
    private final List<String> trainNumbers = new ArrayList<>();

    private GuiTextField inputField;
    private String status = "";

    public GuiTurnoutMachineConfig(EnumHand hand, ItemStack stack) {
        this.hand = hand;
        this.initialStack = stack == null ? ItemStack.EMPTY : stack.copy();
        loadFromStack(this.initialStack);
    }

    @Override
    public void initGui() {
        int left = width / 2 - 150;
        int top = height / 2 - 80;
        inputField = new GuiTextField(1, fontRenderer, left + 10, top + 46, 200, 18);
        buttonList.clear();
        buttonList.add(new GuiButton(10, left + 10, top + 20, 200, 20, modeLabel()));
        buttonList.add(new GuiButton(11, left + 215, top + 46, 75, 20, "添加"));
        buttonList.add(new GuiButton(12, left + 215, top + 70, 75, 20, "删最后"));
        buttonList.add(new GuiButton(13, left + 10, top + 150, 90, 20, "保存"));
        buttonList.add(new GuiButton(14, left + 110, top + 150, 90, 20, "清空"));
        buttonList.add(new GuiButton(15, left + 200, top + 150, 90, 20, "取消"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 150;
        int top = height / 2 - 80;
        drawRect(left - 1, top - 1, left + 301, top + 181, 0xFF666666);
        drawRect(left, top, left + 300, top + 180, 0xCC101010);
        drawCenteredString(fontRenderer, "道岔机配置", width / 2, top + 6, 0xFFFFFF);
        drawString(fontRenderer, "车次输入后点添加，可多个；放置前需先点一下轨道选定检测轨道", left + 10, top + 32, 0xAAAAAA);
        drawString(fontRenderer, "车次:", left + 10, top + 70, 0xFFFFFF);
        drawString(fontRenderer, "列表: " + trainNumbers.size(), left + 10, top + 94, 0x00D27F);
        drawString(fontRenderer, status, left + 10, top + 128, 0xFFFF66);

        inputField.drawTextBox();

        int maxLines = 3;
        int perLine = 40;
        for (int line = 0; line < maxLines; line++) {
            int start = line * perLine;
            if (start >= trainNumbers.size()) {
                break;
            }
            int end = Math.min(trainNumbers.size(), start + perLine);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(trainNumbers.get(i));
            }
            drawString(fontRenderer, sb.toString(), left + 10, top + 106 + line * 10, 0xCCCCCC);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 10) {
            blacklistMode = !blacklistMode;
            button.displayString = modeLabel();
            return;
        }
        if (button.id == 11) {
            addFromField();
            return;
        }
        if (button.id == 12) {
            if (!trainNumbers.isEmpty()) {
                trainNumbers.remove(trainNumbers.size() - 1);
                status = "已删除最后一个";
            }
            return;
        }
        if (button.id == 14) {
            trainNumbers.clear();
            status = "已清空";
            return;
        }
        if (button.id == 15) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == 13) {
            NBTTagCompound cfg = ItemTurnoutMachine.buildConfigTag(blacklistMode, trainNumbers);
            IrAutoMod.NETWORK.sendToServer(new TurnoutConfigMessage(hand, cfg));
            status = "已发送保存";
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (inputField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        inputField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void addFromField() {
        String raw = inputField.getText();
        String normalized = TrainAutoPilotData.normalizeTrainNumber(raw);
        if (normalized.isEmpty()) {
            status = "车次为空";
            return;
        }
        if (!TrainAutoPilotData.isTrainNumberFormatValid(normalized)) {
            status = "车次格式无效";
            return;
        }
        if (!trainNumbers.contains(normalized)) {
            trainNumbers.add(normalized);
            status = "已添加: " + normalized;
        } else {
            status = "已存在: " + normalized;
        }
        inputField.setText("");
    }

    private void loadFromStack(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }
        blacklistMode = !tag.hasKey(ItemTurnoutMachine.NBT_MATCH_TRIGGERS, Constants.NBT.TAG_BYTE) || tag.getBoolean(ItemTurnoutMachine.NBT_MATCH_TRIGGERS);
        trainNumbers.clear();
        if (tag.hasKey(ItemTurnoutMachine.NBT_LIST, Constants.NBT.TAG_LIST)) {
            NBTTagList list = tag.getTagList(ItemTurnoutMachine.NBT_LIST, Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                String n = TrainAutoPilotData.normalizeTrainNumber(list.getStringTagAt(i));
                if (!n.isEmpty() && !trainNumbers.contains(n)) {
                    trainNumbers.add(n);
                }
            }
        }
    }

    private String modeLabel() {
        return blacklistMode ? "模式: 黑名单(列表内不匹配)" : "模式: 白名单(仅列表匹配)";
    }
}

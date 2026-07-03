package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.TurnoutStatusClientCache;
import com.chuanshuoi9.network.TurnoutBlockConfigMessage;
import com.chuanshuoi9.network.TurnoutStatusRequestMessage;
import com.chuanshuoi9.train.TrainAutoPilotData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiTurnoutMachineBlock extends GuiScreen {
    private final BlockPos pos;
    private int tickCounter = 0;

    private boolean blacklistMode = true;
    private final List<String> trainNumbers = new ArrayList<>();
    private boolean dirtyLocal = false;

    private GuiTextField inputField;
    private String status = "";

    public GuiTurnoutMachineBlock(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        int left = width / 2 - 150;
        int top = height / 2 - 90;
        inputField = new GuiTextField(1, fontRenderer, left + 10, top + 58, 200, 18);
        buttonList.clear();
        buttonList.add(new GuiButton(1, left + 10, top + 170, 90, 20, "刷新"));
        buttonList.add(new GuiButton(2, left + 200, top + 170, 90, 20, "关闭"));

        buttonList.add(new GuiButton(10, left + 10, top + 30, 200, 20, modeLabel()));
        buttonList.add(new GuiButton(11, left + 215, top + 58, 75, 20, "添加"));
        buttonList.add(new GuiButton(12, left + 215, top + 82, 75, 20, "删最后"));
        buttonList.add(new GuiButton(13, left + 10, top + 142, 90, 20, "保存"));
        buttonList.add(new GuiButton(14, left + 110, top + 142, 90, 20, "清空"));

        dirtyLocal = false;
        status = "";
        request();
        applyFromCacheIfNeeded();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tickCounter++;
        if (tickCounter >= 10) {
            tickCounter = 0;
            request();
            applyFromCacheIfNeeded();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 150;
        int top = height / 2 - 90;
        drawRect(left - 1, top - 1, left + 301, top + 201, 0xFF666666);
        drawRect(left, top, left + 300, top + 200, 0xCC101010);
        drawCenteredString(fontRenderer, "道岔机", width / 2, top + 6, 0xFFFFFF);

        int y = top + 18;
        drawString(fontRenderer, "位置: " + fmtPos(pos), left + 10, y, 0xCCCCCC);
        y += 12;

        NBTTagCompound tag = TurnoutStatusClientCache.get(pos);
        int power = tag == null ? 0 : tag.getInteger("outputPower");
        String lastTrain = tag == null ? "" : tag.getString("lastTrainNumber");
        drawString(fontRenderer, "输出: " + power + "  最近车次: " + (lastTrain.isEmpty() ? "-" : lastTrain), left + 10, y, power > 0 ? 0x66FF66 : 0xCCCCCC);
        y += 12;

        drawString(fontRenderer, "车次:", left + 10, top + 82, 0xFFFFFF);
        drawString(fontRenderer, "列表: " + trainNumbers.size(), left + 10, top + 106, 0x00D27F);
        drawString(fontRenderer, status, left + 10, top + 130, 0xFFFF66);

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
            drawString(fontRenderer, sb.toString(), left + 10, top + 118 + line * 10, 0xCCCCCC);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            request();
            applyFromCacheIfNeeded();
            return;
        }
        if (button.id == 2) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == 10) {
            blacklistMode = !blacklistMode;
            dirtyLocal = true;
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
                dirtyLocal = true;
                status = "已删除最后一个";
            }
            return;
        }
        if (button.id == 14) {
            trainNumbers.clear();
            dirtyLocal = true;
            status = "已清空";
            return;
        }
        if (button.id == 13) {
            IrAutoMod.NETWORK.sendToServer(new TurnoutBlockConfigMessage(pos, blacklistMode, trainNumbers));
            status = "已发送保存";
            dirtyLocal = false;
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

    private void request() {
        if (pos == null) {
            return;
        }
        IrAutoMod.NETWORK.sendToServer(new TurnoutStatusRequestMessage(pos));
    }

    private void applyFromCacheIfNeeded() {
        if (dirtyLocal) {
            return;
        }
        NBTTagCompound tag = TurnoutStatusClientCache.get(pos);
        if (tag == null || tag.hasNoTags()) {
            return;
        }
        blacklistMode = tag.getBoolean("blacklist");
        trainNumbers.clear();
        if (tag.hasKey("trainNumbers", Constants.NBT.TAG_LIST)) {
            NBTTagList list = tag.getTagList("trainNumbers", Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                String n = TrainAutoPilotData.normalizeTrainNumber(list.getStringTagAt(i));
                if (!n.isEmpty() && !trainNumbers.contains(n)) {
                    trainNumbers.add(n);
                }
            }
        }
        for (GuiButton b : buttonList) {
            if (b.id == 10) {
                b.displayString = modeLabel();
                break;
            }
        }
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
            dirtyLocal = true;
            status = "已添加: " + normalized;
        } else {
            status = "已存在: " + normalized;
        }
        inputField.setText("");
    }

    private String modeLabel() {
        return blacklistMode ? "模式: 黑名单(列表内不匹配)" : "模式: 白名单(仅列表匹配)";
    }

    private static String fmtPos(BlockPos p) {
        if (p == null) {
            return "-";
        }
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }
}

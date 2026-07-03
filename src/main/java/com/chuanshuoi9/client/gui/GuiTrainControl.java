package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.network.TrainControlMessage;
import com.chuanshuoi9.train.TrainAutoPilotData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.io.IOException;

public class GuiTrainControl extends GuiScreen {
    private final String trainUuid;
    private final NBTTagCompound initialData;
    private GuiTextField xField;
    private GuiTextField yField;
    private GuiTextField zField;
    private GuiTextField speedField;
    private GuiTextField throttleField;
    private GuiTextField brakeField;
    private NBTTagCompound controlData;
    private String status = "";

    public GuiTrainControl(String trainUuid, NBTTagCompound initialData) {
        this.trainUuid = trainUuid == null ? "" : trainUuid;
        this.initialData = initialData == null ? new NBTTagCompound() : initialData.copy();
    }

    @Override
    public void initGui() {
        controlData = initialData.copy();
        TrainAutoPilotData.ensureDefaults(controlData);
        int left = width / 2 - 150;
        int top = height / 2 - 95;
        xField = new GuiTextField(1, fontRenderer, left + 55, top + 40, 60, 18);
        yField = new GuiTextField(2, fontRenderer, left + 120, top + 40, 45, 18);
        zField = new GuiTextField(3, fontRenderer, left + 170, top + 40, 60, 18);
        speedField = new GuiTextField(4, fontRenderer, left + 55, top + 66, 60, 18);
        throttleField = new GuiTextField(5, fontRenderer, left + 180, top + 66, 45, 18);
        brakeField = new GuiTextField(6, fontRenderer, left + 245, top + 66, 45, 18);
        buttonList.clear();
        buttonList.add(new GuiButton(10, left + 235, top + 38, 55, 20, "获取坐标"));
        buttonList.add(new GuiButton(11, left + 55, top + 92, 20, 20, "+"));
        buttonList.add(new GuiButton(12, left + 80, top + 92, 20, 20, "-"));
        buttonList.add(new GuiButton(13, left + 110, top + 92, 80, 20, "添加站台"));
        buttonList.add(new GuiButton(14, left + 195, top + 92, 95, 20, "保存并启动"));
        buttonList.add(new GuiButton(15, left + 110, top + 116, 80, 20, "停用自动"));
        buttonList.add(new GuiButton(16, left + 195, top + 116, 95, 20, "下一站"));
        readFromData();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 150;
        int top = height / 2 - 95;
        drawRect(left - 1, top - 1, left + 301, top + 191, 0xFF666666);
        drawRect(left, top, left + 300, top + 190, 0xCC101010);
        drawCenteredString(fontRenderer, "列车自动驾驶控制", width / 2, top + 8, 0xFFFFFF);
        drawString(fontRenderer, "手持控制纸右键列车绑定；潜行右键列车打开控制；对空气右键控制纸打开时刻表", left + 8, top + 22, 0xAAAAAA);
        drawString(fontRenderer, "X:", left + 40, top + 45, 0xFFFFFF);
        drawString(fontRenderer, "Y:", left + 112, top + 45, 0xFFFFFF);
        drawString(fontRenderer, "Z:", left + 162, top + 45, 0xFFFFFF);
        drawString(fontRenderer, "限速(km/h):", left + 8, top + 71, 0xFFFFFF);
        drawString(fontRenderer, "节流阀:", left + 145, top + 71, 0xFFFFFF);
        drawString(fontRenderer, "制动:", left + 215, top + 71, 0xFFFFFF);
        drawString(fontRenderer, "站台数: " + getStopCount(), left + 8, top + 98, 0xFFFFFF);
        drawString(fontRenderer, "当前站索引: " + controlData.getInteger(TrainAutoPilotData.CURRENT_INDEX), left + 8, top + 120, 0x00D27F);
        drawString(fontRenderer, "自动驾驶: " + (controlData.getBoolean(TrainAutoPilotData.ENABLED) ? "开启" : "关闭"), left + 8, top + 132, 0x00D27F);
        drawString(fontRenderer, "列车: " + trainUuid, left + 8, top + 144, 0x00D27F);
        drawString(fontRenderer, status, left + 8, top + 158, 0xFFFF66);
        xField.drawTextBox();
        yField.drawTextBox();
        zField.drawTextBox();
        speedField.drawTextBox();
        throttleField.drawTextBox();
        brakeField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 10) {
            if (mc.player != null) {
                xField.setText(String.valueOf(mc.player.posX));
                yField.setText(String.valueOf(mc.player.posY));
                zField.setText(String.valueOf(mc.player.posZ));
                status = "已填入玩家坐标";
            }
            return;
        }
        if (button.id == 11) {
            updateStopCount(getStopCount() + 1);
            status = "站台数 +1（编辑坐标后点添加站台）";
            return;
        }
        if (button.id == 12) {
            removeLastStop();
            status = "已删除最后一个站台";
            return;
        }
        if (button.id == 13) {
            if (appendStopFromFields()) {
                status = "已添加站台";
            } else {
                status = "坐标或限速格式无效";
            }
            return;
        }
        if (button.id == 14) {
            if (trainUuid.isEmpty()) {
                status = "列车标识为空";
                return;
            }
            NBTTagCompound payload = createPayloadFromGui();
            payload.setBoolean(TrainAutoPilotData.ENABLED, true);
            IrAutoMod.NETWORK.sendToServer(new TrainControlMessage(trainUuid, payload));
            controlData = payload;
            status = "已保存并启动 A→B→C...循环";
            return;
        }
        if (button.id == 15) {
            NBTTagCompound payload = createPayloadFromGui();
            payload.setBoolean(TrainAutoPilotData.ENABLED, false);
            IrAutoMod.NETWORK.sendToServer(new TrainControlMessage(trainUuid, payload));
            controlData = payload;
            status = "已停用自动驾驶";
            return;
        }
        if (button.id == 16) {
            NBTTagCompound payload = createPayloadFromGui();
            int count = payload.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND).tagCount();
            if (count > 0) {
                int next = (payload.getInteger(TrainAutoPilotData.CURRENT_INDEX) + 1) % count;
                payload.setInteger(TrainAutoPilotData.CURRENT_INDEX, next);
                IrAutoMod.NETWORK.sendToServer(new TrainControlMessage(trainUuid, payload));
                controlData = payload;
                status = "已切到下一站索引: " + next;
            } else {
                status = "没有站台可切换";
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (xField.textboxKeyTyped(typedChar, keyCode) || yField.textboxKeyTyped(typedChar, keyCode)
            || zField.textboxKeyTyped(typedChar, keyCode) || speedField.textboxKeyTyped(typedChar, keyCode)
            || throttleField.textboxKeyTyped(typedChar, keyCode) || brakeField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        xField.mouseClicked(mouseX, mouseY, mouseButton);
        yField.mouseClicked(mouseX, mouseY, mouseButton);
        zField.mouseClicked(mouseX, mouseY, mouseButton);
        speedField.mouseClicked(mouseX, mouseY, mouseButton);
        throttleField.mouseClicked(mouseX, mouseY, mouseButton);
        brakeField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void readFromData() {
        if (controlData.hasKey(TrainAutoPilotData.THROTTLE, Constants.NBT.TAG_FLOAT)) {
            throttleField.setText(String.format("%.2f", controlData.getFloat(TrainAutoPilotData.THROTTLE)));
        } else {
            throttleField.setText("0.60");
        }
        if (controlData.hasKey(TrainAutoPilotData.BRAKE, Constants.NBT.TAG_FLOAT)) {
            brakeField.setText(String.format("%.2f", controlData.getFloat(TrainAutoPilotData.BRAKE)));
        } else {
            brakeField.setText("0.00");
        }
        if (!controlData.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)) {
            controlData.setTag(TrainAutoPilotData.STOPS, new NBTTagList());
        }
        speedField.setText("60");
    }

    private boolean appendStopFromFields() {
        int x;
        int y;
        int z;
        float limit;
        try {
            x = (int) Math.round(Double.parseDouble(xField.getText().trim()));
            y = (int) Math.round(Double.parseDouble(yField.getText().trim()));
            z = (int) Math.round(Double.parseDouble(zField.getText().trim()));
            limit = Float.parseFloat(speedField.getText().trim());
        } catch (Exception ex) {
            return false;
        }
        limit = Math.max(1.0f, Math.min(600.0f, limit));
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        NBTTagCompound stop = new NBTTagCompound();
        stop.setInteger("x", x);
        stop.setInteger("y", y);
        stop.setInteger("z", z);
        stop.setFloat("limit", limit);
        stops.appendTag(stop);
        controlData.setTag(TrainAutoPilotData.STOPS, stops);
        return true;
    }

    private void removeLastStop() {
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            return;
        }
        NBTTagList newStops = new NBTTagList();
        for (int i = 0; i < stops.tagCount() - 1; i++) {
            newStops.appendTag(stops.get(i));
        }
        controlData.setTag(TrainAutoPilotData.STOPS, newStops);
        int max = Math.max(0, newStops.tagCount() - 1);
        controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, Math.min(controlData.getInteger(TrainAutoPilotData.CURRENT_INDEX), max));
    }

    private int getStopCount() {
        return controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND).tagCount();
    }

    private void updateStopCount(int count) {
        int target = Math.max(0, Math.min(64, count));
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        while (stops.tagCount() < target) {
            NBTTagCompound stop = new NBTTagCompound();
            stop.setInteger("x", 0);
            stop.setInteger("y", 64);
            stop.setInteger("z", 0);
            stop.setFloat("limit", 60.0f);
            stops.appendTag(stop);
        }
        if (stops.tagCount() > target) {
            NBTTagList newStops = new NBTTagList();
            for (int i = 0; i < target; i++) {
                newStops.appendTag(stops.get(i));
            }
            stops = newStops;
        }
        controlData.setTag(TrainAutoPilotData.STOPS, stops);
        int max = Math.max(0, stops.tagCount() - 1);
        controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, Math.min(controlData.getInteger(TrainAutoPilotData.CURRENT_INDEX), max));
    }

    private NBTTagCompound createPayloadFromGui() {
        NBTTagCompound tag = controlData.copy();
        float throttle = parseFloatOrDefault(throttleField.getText(), 0.6f);
        float brake = parseFloatOrDefault(brakeField.getText(), 0.0f);
        throttle = Math.max(0.0f, Math.min(1.0f, throttle));
        brake = Math.max(0.0f, Math.min(1.0f, brake));
        tag.setFloat(TrainAutoPilotData.THROTTLE, throttle);
        tag.setFloat(TrainAutoPilotData.BRAKE, brake);
        tag.setString("trainUuid", trainUuid);
        return tag;
    }

    private float parseFloatOrDefault(String text, float defaultValue) {
        try {
            return Float.parseFloat(text.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.network.TrainManagerSaveMessage;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.network.StationSyncMessage.StationData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.io.IOException;

public class GuiTrainTimetable extends GuiScreen {
    private final String trainUuid;
    private final NBTTagCompound initialData;
    private NBTTagCompound controlData;
    private int editIndex;
    private GuiTextField trainNumberField;
    private GuiTextField nameField;
    private GuiTextField limitField;
    private GuiTextField waitField;
    private String status = "";

    public GuiTrainTimetable(String trainUuid, NBTTagCompound initialData) {
        this.trainUuid = trainUuid == null ? "" : trainUuid;
        this.initialData = initialData == null ? new NBTTagCompound() : initialData.copy();
    }

    @Override
    public void initGui() {
        if (controlData == null) {
            controlData = initialData.copy();
        }
        TrainAutoPilotData.ensureDefaults(controlData);
        ensureAtLeastOneStop();
        editIndex = clampIndex(controlData.getInteger(TrainAutoPilotData.CURRENT_INDEX));
        int left = width / 2 - 150;
        int top = height / 2 - 95;
        trainNumberField = new GuiTextField(4, fontRenderer, left + 70, top + 30, 220, 18);
        nameField = new GuiTextField(1, fontRenderer, left + 70, top + 46, 220, 18);
        limitField = new GuiTextField(2, fontRenderer, left + 112, top + 70, 60, 18);
        waitField = new GuiTextField(3, fontRenderer, left + 250, top + 70, 40, 18);
        buttonList.clear();
        buttonList.add(new GuiButton(10, left + 8, top + 118, 55, 20, "上一条"));
        buttonList.add(new GuiButton(11, left + 66, top + 118, 55, 20, "下一条"));
        buttonList.add(new GuiButton(12, left + 126, top + 118, 80, 20, "添加条目"));
        buttonList.add(new GuiButton(13, left + 210, top + 118, 80, 20, "删除条目"));
        buttonList.add(new GuiButton(14, left + 8, top + 94, 80, 20, "选择坐标"));
        buttonList.add(new GuiButton(15, left + 126, top + 142, 80, 20, "仅保存"));
        buttonList.add(new GuiButton(16, left + 210, top + 142, 80, 20, "保存并启动"));
        buttonList.add(new GuiButton(17, left + 8, top + 142, 110, 20, "停用自动驾驶"));
        loadStopToFields();
        trainNumberField.setText(controlData.getString(TrainAutoPilotData.TRAIN_NUMBER));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 150;
        int top = height / 2 - 95;
        drawRect(left - 1, top - 1, left + 301, top + 191, 0xFF666666);
        drawRect(left, top, left + 300, top + 190, 0xCC101010);
        drawCenteredString(fontRenderer, "列车时刻表", width / 2, top + 8, 0xFFFFFF);
        String trainName = initialData.hasKey("trainName", Constants.NBT.TAG_STRING) ? initialData.getString("trainName") : "";
        drawString(fontRenderer, "列车: " + trainName, left + 8, top + 22, 0xAAAAAA);
        drawString(fontRenderer, "车次:", left + 8, top + 34, 0xFFFFFF);
        drawString(fontRenderer, "站台名:", left + 8, top + 50, 0xFFFFFF);
        drawString(fontRenderer, "区间限速(km/h):", left + 8, top + 74, 0xFFFFFF);
        drawString(fontRenderer, "停靠(秒):", left + 180, top + 74, 0xFFFFFF);
        drawString(fontRenderer, "车站坐标: " + formatPos(getCurrentStopPos()), left + 96, top + 98, 0xFFFFFF);
        drawString(fontRenderer, "条目: " + (editIndex + 1) + " / " + getStopCount(), left + 8, top + 98, 0x00D27F);
        drawString(fontRenderer, "自动驾驶: " + (controlData.getBoolean(TrainAutoPilotData.ENABLED) ? "开启" : "关闭"), left + 8, top + 166, 0x00D27F);
        drawString(fontRenderer, status, left + 8, top + 178, 0xFFFF66);
        trainNumberField.drawTextBox();
        nameField.drawTextBox();
        limitField.drawTextBox();
        waitField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 10) {
            applyFieldsToStop();
            editIndex = clampIndex(editIndex - 1);
            controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, editIndex);
            loadStopToFields();
            status = "已切换上一条";
            return;
        }
        if (button.id == 11) {
            applyFieldsToStop();
            editIndex = clampIndex(editIndex + 1);
            controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, editIndex);
            loadStopToFields();
            status = "已切换下一条";
            return;
        }
        if (button.id == 12) {
            applyFieldsToStop();
            addNewStop();
            loadStopToFields();
            status = "已添加条目";
            return;
        }
        if (button.id == 13) {
            deleteCurrentStop();
            loadStopToFields();
            status = "已删除条目";
            return;
        }
        if (button.id == 14) {
            applyFieldsToStop();
            mc.displayGuiScreen(new GuiRailwayMapStationPicker(this, editIndex, getCurrentStopPos()));
            return;
        }
        if (button.id == 15) {
            save(false);
            return;
        }
        if (button.id == 16) {
            save(true);
            return;
        }
        if (button.id == 17) {
            applyFieldsToStop();
            NBTTagCompound payload = controlData.copy();
            payload.setBoolean(TrainAutoPilotData.ENABLED, false);
            IrAutoMod.NETWORK.sendToServer(new TrainManagerSaveMessage(trainUuid, payload));
            controlData = payload;
            status = "已停用自动驾驶";
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (trainNumberField.textboxKeyTyped(typedChar, keyCode) || nameField.textboxKeyTyped(typedChar, keyCode) || limitField.textboxKeyTyped(typedChar, keyCode) || waitField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        trainNumberField.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        limitField.mouseClicked(mouseX, mouseY, mouseButton);
        waitField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    void applyPickedStationPos(int stopIndex, BlockPos pos) {
        if (pos == null) {
            status = "未选择坐标";
            return;
        }
        ensureAtLeastOneStop();
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        int index = Math.max(0, Math.min(stops.tagCount() - 1, stopIndex));
        NBTTagCompound stop = stops.getCompoundTagAt(index);
        stop.setInteger("x", pos.getX());
        stop.setInteger("y", pos.getY());
        stop.setInteger("z", pos.getZ());
        stops.set(index, stop);
        controlData.setTag(TrainAutoPilotData.STOPS, stops);
        status = "已选择车站坐标: " + formatPos(pos);
    }

    void applyPickedStation(int stopIndex, StationData station) {
        if (station == null) {
            status = "未选择站台";
            return;
        }
        BlockPos pos = new BlockPos(station.x, station.y, station.z);
        ensureAtLeastOneStop();
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        int index = Math.max(0, Math.min(stops.tagCount() - 1, stopIndex));
        NBTTagCompound stop = stops.getCompoundTagAt(index);
        stop.setInteger("x", pos.getX());
        stop.setInteger("y", pos.getY());
        stop.setInteger("z", pos.getZ());
        if (station.name != null && !station.name.trim().isEmpty()) {
            stop.setString("name", station.name.trim());
        }
        stops.set(index, stop);
        controlData.setTag(TrainAutoPilotData.STOPS, stops);
        status = "已选择站台: " + station.name + " " + formatPos(pos);
    }

    private void save(boolean enable) {
        if (trainUuid.isEmpty()) {
            status = "列车标识为空";
            return;
        }
        applyFieldsToStop();
        NBTTagCompound payload = controlData.copy();
        payload.setString(TrainAutoPilotData.TRAIN_NUMBER, trainNumberField.getText());
        payload.setBoolean(TrainAutoPilotData.ENABLED, enable || payload.getBoolean(TrainAutoPilotData.ENABLED));
        IrAutoMod.NETWORK.sendToServer(new TrainManagerSaveMessage(trainUuid, payload));
        controlData = payload;
        status = enable ? "已保存时刻表并启动" : "已保存时刻表";
    }

    private void ensureAtLeastOneStop() {
        if (!controlData.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)) {
            controlData.setTag(TrainAutoPilotData.STOPS, new NBTTagList());
        }
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            NBTTagCompound stop = defaultStop(1);
            stops.appendTag(stop);
            controlData.setTag(TrainAutoPilotData.STOPS, stops);
            controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, 0);
        }
    }

    private void loadStopToFields() {
        ensureAtLeastOneStop();
        editIndex = clampIndex(controlData.getInteger(TrainAutoPilotData.CURRENT_INDEX));
        NBTTagCompound stop = getCurrentStop();
        nameField.setText(stop.hasKey("name", Constants.NBT.TAG_STRING) ? stop.getString("name") : "");
        float limit = stop.hasKey("limit", Constants.NBT.TAG_FLOAT) ? stop.getFloat("limit") : 45.0f;
        limitField.setText(String.format("%.1f", limit));
        int waitTicks = stop.hasKey("waitTicks", Constants.NBT.TAG_INT) ? stop.getInteger("waitTicks") : 200;
        int waitSeconds = Math.max(1, waitTicks / 20);
        waitField.setText(Integer.toString(waitSeconds));
    }

    private void applyFieldsToStop() {
        ensureAtLeastOneStop();
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        editIndex = clampIndex(controlData.getInteger(TrainAutoPilotData.CURRENT_INDEX));
        NBTTagCompound stop = stops.getCompoundTagAt(editIndex);
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = "站台" + (editIndex + 1);
        }
        stop.setString("name", name);
        float limit = parseFloatOrDefault(limitField.getText(), 45.0f);
        limit = Math.max(1.0f, Math.min(600.0f, limit));
        stop.setFloat("limit", limit);
        int waitSeconds = parseIntOrDefault(waitField.getText(), 10);
        waitSeconds = Math.max(1, Math.min(300, waitSeconds));
        stop.setInteger("waitTicks", waitSeconds * 20);
        stops.set(editIndex, stop);
        controlData.setTag(TrainAutoPilotData.STOPS, stops);
    }

    private void addNewStop() {
        ensureAtLeastOneStop();
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        int nextIndex = stops.tagCount();
        stops.appendTag(defaultStop(nextIndex + 1));
        controlData.setTag(TrainAutoPilotData.STOPS, stops);
        editIndex = nextIndex;
        controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, editIndex);
    }

    private void deleteCurrentStop() {
        ensureAtLeastOneStop();
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 1) {
            controlData.setTag(TrainAutoPilotData.STOPS, new NBTTagList());
            ensureAtLeastOneStop();
            editIndex = 0;
            controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, 0);
            return;
        }
        int removeIndex = clampIndex(controlData.getInteger(TrainAutoPilotData.CURRENT_INDEX));
        NBTTagList newStops = new NBTTagList();
        for (int i = 0; i < stops.tagCount(); i++) {
            if (i == removeIndex) {
                continue;
            }
            newStops.appendTag(stops.get(i));
        }
        controlData.setTag(TrainAutoPilotData.STOPS, newStops);
        int max = Math.max(0, newStops.tagCount() - 1);
        editIndex = Math.min(removeIndex, max);
        controlData.setInteger(TrainAutoPilotData.CURRENT_INDEX, editIndex);
    }

    private NBTTagCompound defaultStop(int index) {
        NBTTagCompound stop = new NBTTagCompound();
        stop.setString("name", "站台" + index);
        stop.setFloat("limit", 45.0f);
        BlockPos pos = mc != null && mc.player != null ? mc.player.getPosition() : new BlockPos(0, 64, 0);
        stop.setInteger("x", pos.getX());
        stop.setInteger("y", pos.getY());
        stop.setInteger("z", pos.getZ());
        stop.setInteger("waitTicks", 200);
        return stop;
    }

    private int getStopCount() {
        return controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND).tagCount();
    }

    private NBTTagCompound getCurrentStop() {
        NBTTagList stops = controlData.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        return stops.getCompoundTagAt(editIndex);
    }

    private BlockPos getCurrentStopPos() {
        NBTTagCompound stop = getCurrentStop();
        return new BlockPos(stop.getInteger("x"), stop.getInteger("y"), stop.getInteger("z"));
    }

    private int clampIndex(int index) {
        int count = getStopCount();
        if (count <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(count - 1, index));
    }

    private static float parseFloatOrDefault(String text, float defaultValue) {
        try {
            return Float.parseFloat(text.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static int parseIntOrDefault(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "(未设置)";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}

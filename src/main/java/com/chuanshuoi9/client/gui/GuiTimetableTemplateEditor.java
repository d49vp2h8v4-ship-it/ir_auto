package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.TimetableTemplateClientCache;
import com.chuanshuoi9.network.TimetableTemplateDeleteMessage;
import com.chuanshuoi9.network.TimetableTemplateListRequestMessage;
import com.chuanshuoi9.network.TimetableTemplateLoadRequestMessage;
import com.chuanshuoi9.network.TimetableTemplateSaveMessage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.io.IOException;
import java.util.UUID;

public class GuiTimetableTemplateEditor extends GuiScreen implements StationPickerReceiver, GuiTimetableTemplatePicker.Receiver {
    private String editingId = "";
    private NBTTagCompound templateData;
    private int editIndex;
    private GuiTextField templateNameField;
    private GuiTextField stationNameField;
    private GuiTextField limitField;
    private GuiTextField waitField;
    private String status = "";

    @Override
    public void initGui() {
        if (templateData == null) {
            templateData = new NBTTagCompound();
            templateData.setString("name", "");
            templateData.setTag("stops", new NBTTagList());
        }
        ensureAtLeastOneStop();
        editIndex = clampIndex(editIndex);
        int left = width / 2 - 150;
        int top = height / 2 - 105;
        templateNameField = new GuiTextField(1, fontRenderer, left + 70, top + 26, 220, 18);
        stationNameField = new GuiTextField(2, fontRenderer, left + 70, top + 52, 220, 18);
        limitField = new GuiTextField(3, fontRenderer, left + 112, top + 76, 60, 18);
        waitField = new GuiTextField(4, fontRenderer, left + 250, top + 76, 40, 18);
        buttonList.clear();
        buttonList.add(new GuiButton(10, left + 8, top + 120, 55, 20, "上一条"));
        buttonList.add(new GuiButton(11, left + 66, top + 120, 55, 20, "下一条"));
        buttonList.add(new GuiButton(12, left + 126, top + 120, 80, 20, "添加条目"));
        buttonList.add(new GuiButton(13, left + 210, top + 120, 80, 20, "删除条目"));
        buttonList.add(new GuiButton(14, left + 8, top + 96, 80, 20, "选择坐标"));
        buttonList.add(new GuiButton(15, left + 8, top + 144, 80, 20, "新建模板"));
        buttonList.add(new GuiButton(16, left + 92, top + 144, 80, 20, "加载模板"));
        buttonList.add(new GuiButton(17, left + 176, top + 144, 55, 20, "保存"));
        buttonList.add(new GuiButton(18, left + 235, top + 144, 55, 20, "删除"));
        loadFields();
        templateNameField.setText(templateData.getString("name"));
        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 150;
        int top = height / 2 - 105;
        drawRect(left - 1, top - 1, left + 301, top + 211, 0xFF666666);
        drawRect(left, top, left + 300, top + 210, 0xCC101010);
        drawCenteredString(fontRenderer, "时刻表模板编辑器", width / 2, top + 8, 0xFFFFFF);
        drawString(fontRenderer, "模板名:", left + 8, top + 30, 0xFFFFFF);
        drawString(fontRenderer, "站台名:", left + 8, top + 56, 0xFFFFFF);
        drawString(fontRenderer, "区间限速(km/h):", left + 8, top + 80, 0xFFFFFF);
        drawString(fontRenderer, "停靠(秒):", left + 180, top + 80, 0xFFFFFF);
        drawString(fontRenderer, "车站坐标: " + formatPos(getCurrentStopPos()), left + 96, top + 100, 0xFFFFFF);
        drawString(fontRenderer, "条目: " + (editIndex + 1) + " / " + getStopCount(), left + 8, top + 100, 0x00D27F);
        drawString(fontRenderer, "模板ID: " + (editingId.isEmpty() ? "(未保存)" : editingId), left + 8, top + 170, 0xAAAAAA);
        drawString(fontRenderer, status, left + 8, top + 186, 0xFFFF66);
        templateNameField.drawTextBox();
        stationNameField.drawTextBox();
        limitField.drawTextBox();
        waitField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 10) {
            applyFields();
            editIndex = clampIndex(editIndex - 1);
            loadFields();
            status = "已切换上一条";
            return;
        }
        if (button.id == 11) {
            applyFields();
            editIndex = clampIndex(editIndex + 1);
            loadFields();
            status = "已切换下一条";
            return;
        }
        if (button.id == 12) {
            applyFields();
            addNewStop();
            loadFields();
            status = "已添加条目";
            return;
        }
        if (button.id == 13) {
            deleteCurrentStop();
            loadFields();
            status = "已删除条目";
            return;
        }
        if (button.id == 14) {
            applyFields();
            mc.displayGuiScreen(new GuiRailwayMapStationPicker(this, this, editIndex, getCurrentStopPos()));
            return;
        }
        if (button.id == 15) {
            newTemplate();
            status = "已新建模板";
            return;
        }
        if (button.id == 16) {
            IrAutoMod.NETWORK.sendToServer(new TimetableTemplateListRequestMessage());
            mc.displayGuiScreen(new GuiTimetableTemplatePicker(this, this));
            return;
        }
        if (button.id == 17) {
            saveTemplate();
            return;
        }
        if (button.id == 18) {
            deleteTemplate();
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        if (templateNameField.textboxKeyTyped(typedChar, keyCode)
                || stationNameField.textboxKeyTyped(typedChar, keyCode)
                || limitField.textboxKeyTyped(typedChar, keyCode)
                || waitField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        templateNameField.mouseClicked(mouseX, mouseY, mouseButton);
        stationNameField.mouseClicked(mouseX, mouseY, mouseButton);
        limitField.mouseClicked(mouseX, mouseY, mouseButton);
        waitField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void applyPickedStationPos(int stopIndex, net.minecraft.util.math.BlockPos pos) {
        if (pos == null) {
            status = "未选择坐标";
            return;
        }
        ensureAtLeastOneStop();
        NBTTagList stops = templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND);
        int index = Math.max(0, Math.min(stops.tagCount() - 1, stopIndex));
        NBTTagCompound stop = stops.getCompoundTagAt(index);
        stop.setInteger("x", pos.getX());
        stop.setInteger("y", pos.getY());
        stop.setInteger("z", pos.getZ());
        stops.set(index, stop);
        templateData.setTag("stops", stops);
        status = "已选择车站坐标: " + formatPos(pos);
    }

    @Override
    public void applyPickedStation(int stopIndex, com.chuanshuoi9.network.StationSyncMessage.StationData station) {
        if (station == null) {
            status = "未选择站台";
            return;
        }
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(station.x, station.y, station.z);
        applyPickedStationPos(stopIndex, pos);
        if (station.name != null && !station.name.trim().isEmpty()) {
            ensureAtLeastOneStop();
            NBTTagList stops = templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND);
            int index = Math.max(0, Math.min(stops.tagCount() - 1, stopIndex));
            NBTTagCompound stop = stops.getCompoundTagAt(index);
            stop.setString("name", station.name.trim());
            stops.set(index, stop);
            templateData.setTag("stops", stops);
        }
        status = "已选择站台: " + station.name + " " + formatPos(pos);
    }

    @Override
    public void onTemplatePicked(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return;
        }
        editingId = templateId;
        IrAutoMod.NETWORK.sendToServer(new TimetableTemplateLoadRequestMessage(templateId));
    }

    public void onTemplateLoaded(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return;
        }
        NBTTagCompound loaded = TimetableTemplateClientCache.getTemplate(templateId);
        if (loaded == null) {
            status = "模板加载失败";
            return;
        }
        editingId = templateId;
        templateData = loaded.copy();
        if (!templateData.hasKey("name", Constants.NBT.TAG_STRING)) {
            templateData.setString("name", "");
        }
        if (!templateData.hasKey("stops", Constants.NBT.TAG_LIST)) {
            templateData.setTag("stops", new NBTTagList());
        }
        ensureAtLeastOneStop();
        editIndex = clampIndex(0);
        loadFields();
        templateNameField.setText(templateData.getString("name"));
        status = "已加载模板";
    }

    private void newTemplate() {
        editingId = "";
        templateData = new NBTTagCompound();
        templateData.setString("name", "");
        templateData.setTag("stops", new NBTTagList());
        editIndex = 0;
        ensureAtLeastOneStop();
        loadFields();
        templateNameField.setText("");
    }

    private void saveTemplate() {
        applyFields();
        if (editingId == null || editingId.isEmpty()) {
            editingId = UUID.randomUUID().toString();
        }
        templateData.setString("id", editingId);
        templateData.setString("name", templateNameField.getText().trim());
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("id", editingId);
        payload.setTag("template", templateData.copy());
        IrAutoMod.NETWORK.sendToServer(new TimetableTemplateSaveMessage(payload));
        status = "已保存模板";
    }

    private void deleteTemplate() {
        if (editingId == null || editingId.isEmpty()) {
            status = "模板未保存";
            return;
        }
        IrAutoMod.NETWORK.sendToServer(new TimetableTemplateDeleteMessage(editingId));
        newTemplate();
        status = "已删除模板";
    }

    private void ensureAtLeastOneStop() {
        if (!templateData.hasKey("stops", Constants.NBT.TAG_LIST)) {
            templateData.setTag("stops", new NBTTagList());
        }
        NBTTagList stops = templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            NBTTagCompound stop = defaultStop(1);
            stops.appendTag(stop);
            templateData.setTag("stops", stops);
            editIndex = 0;
        }
    }

    private void loadFields() {
        ensureAtLeastOneStop();
        editIndex = clampIndex(editIndex);
        NBTTagCompound stop = getCurrentStop();
        stationNameField.setText(stop.hasKey("name", Constants.NBT.TAG_STRING) ? stop.getString("name") : "");
        float limit = stop.hasKey("limit", Constants.NBT.TAG_FLOAT) ? stop.getFloat("limit") : 45.0f;
        limitField.setText(String.format("%.1f", limit));
        int waitTicks = stop.hasKey("waitTicks", Constants.NBT.TAG_INT) ? stop.getInteger("waitTicks") : 200;
        int waitSeconds = Math.max(1, waitTicks / 20);
        waitField.setText(Integer.toString(waitSeconds));
    }

    private void applyFields() {
        ensureAtLeastOneStop();
        NBTTagList stops = templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND);
        editIndex = clampIndex(editIndex);
        NBTTagCompound stop = stops.getCompoundTagAt(editIndex);
        String name = stationNameField.getText().trim();
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
        templateData.setTag("stops", stops);
        templateData.setString("name", templateNameField.getText().trim());
    }

    private void addNewStop() {
        ensureAtLeastOneStop();
        NBTTagList stops = templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND);
        int nextIndex = stops.tagCount();
        stops.appendTag(defaultStop(nextIndex + 1));
        templateData.setTag("stops", stops);
        editIndex = nextIndex;
    }

    private void deleteCurrentStop() {
        ensureAtLeastOneStop();
        NBTTagList stops = templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 1) {
            templateData.setTag("stops", new NBTTagList());
            ensureAtLeastOneStop();
            editIndex = 0;
            return;
        }
        int removeIndex = clampIndex(editIndex);
        NBTTagList newStops = new NBTTagList();
        for (int i = 0; i < stops.tagCount(); i++) {
            if (i == removeIndex) {
                continue;
            }
            newStops.appendTag(stops.get(i));
        }
        templateData.setTag("stops", newStops);
        int max = Math.max(0, newStops.tagCount() - 1);
        editIndex = Math.min(removeIndex, max);
    }

    private int getStopCount() {
        return templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND).tagCount();
    }

    private NBTTagCompound getCurrentStop() {
        NBTTagList stops = templateData.getTagList("stops", Constants.NBT.TAG_COMPOUND);
        return stops.getCompoundTagAt(editIndex);
    }

    private net.minecraft.util.math.BlockPos getCurrentStopPos() {
        NBTTagCompound stop = getCurrentStop();
        return new net.minecraft.util.math.BlockPos(stop.getInteger("x"), stop.getInteger("y"), stop.getInteger("z"));
    }

    private int clampIndex(int index) {
        int count = getStopCount();
        if (count <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(count - 1, index));
    }

    private NBTTagCompound defaultStop(int index) {
        NBTTagCompound stop = new NBTTagCompound();
        stop.setString("name", "站台" + index);
        stop.setFloat("limit", 45.0f);
        net.minecraft.util.math.BlockPos pos = mc != null && mc.player != null ? mc.player.getPosition() : new net.minecraft.util.math.BlockPos(0, 64, 0);
        stop.setInteger("x", pos.getX());
        stop.setInteger("y", pos.getY());
        stop.setInteger("z", pos.getZ());
        stop.setInteger("waitTicks", 200);
        return stop;
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

    public void refreshFromCache() {
    }

    private static String formatPos(net.minecraft.util.math.BlockPos pos) {
        if (pos == null) {
            return "(未设置)";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}

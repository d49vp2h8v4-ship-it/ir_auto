package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.TrainManagerClientCache;
import com.chuanshuoi9.network.TrainManagerDeleteMessage;
import com.chuanshuoi9.network.TrainManagerDetailRequestMessage;
import com.chuanshuoi9.network.TrainManagerListRequestMessage;
import com.chuanshuoi9.train.TrainAutoPilotData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiTrainManager extends GuiScreen {
    private GuiTextField searchField;
    private int listScroll;
    private String selectedUuid = "";
    private int ticks;

    @Override
    public void initGui() {
        ticks = 0;
        int left = width / 2 - 180;
        int top = height / 2 - 110;
        searchField = new GuiTextField(1, fontRenderer, left + 10, top + 28, 160, 18);
        searchField.setMaxStringLength(64);
        buttonList.clear();
        buttonList.add(new GuiButton(2, left + 10, top + 210 - 22, 52, 20, "刷新"));
        buttonList.add(new GuiButton(3, left + 66, top + 210 - 22, 52, 20, "删除"));
        buttonList.add(new GuiButton(4, left + 122, top + 210 - 22, 72, 20, "编辑"));
        requestList();
    }

    @Override
    public void updateScreen() {
        ticks++;
        if (ticks % 60 == 0) {
            requestList();
        }
        if (!selectedUuid.isEmpty() && ticks % 20 == 0) {
            requestDetail(selectedUuid);
        }
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
        super.updateScreen();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (!isInsideList(mx, my)) {
            return;
        }
        int delta = dWheel > 0 ? -1 : 1;
        listScroll = Math.max(0, listScroll + delta);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 180;
        int top = height / 2 - 110;
        int right = left + 360;
        int bottom = top + 210;
        drawRect(left - 1, top - 1, right + 1, bottom + 1, 0xFF666666);
        drawRect(left, top, right, bottom, 0xCC101010);
        drawCenteredString(fontRenderer, "列车管理器", width / 2, top + 8, 0xFFFFFF);

        drawString(fontRenderer, "搜索:", left + 10, top + 16, 0xFFFFFF);
        searchField.drawTextBox();

        int listLeft = left + 10;
        int listTop = top + 52;
        int listRight = left + 180;
        int listBottom = top + 210 - 28;
        drawRect(listLeft - 1, listTop - 1, listRight + 1, listBottom + 1, 0xFF444444);
        drawRect(listLeft, listTop, listRight, listBottom, 0xAA000000);

        List<NBTTagCompound> entries = getFilteredEntries();
        int rowH = 24;
        int visibleRows = Math.max(1, (listBottom - listTop - 4) / rowH);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        listScroll = Math.max(0, Math.min(maxScroll, listScroll));

        for (int i = 0; i < visibleRows; i++) {
            int idx = listScroll + i;
            if (idx >= entries.size()) {
                break;
            }
            NBTTagCompound e = entries.get(idx);
            String uuid = e.getString("trainUuid");
            String number = e.getString("trainNumber");
            boolean online = e.getBoolean("online");
            String next = e.getString("nextStop");
            double speed = e.getDouble("speedKmh");
            String title = (number == null || number.isEmpty()) ? "(无车次)" : number;
            String sub = (online ? "在线 " : "离线 ") + String.format("%.1f", speed) + "km/h " + (next == null ? "" : next);

            int y = listTop + 2 + i * rowH;
            boolean selected = uuid != null && uuid.equals(selectedUuid);
            if (selected) {
                drawRect(listLeft + 1, y - 1, listRight - 1, y + rowH - 1, 0xFF2A2A5A);
            }
            drawString(fontRenderer, title, listLeft + 3, y, online ? 0x66FF66 : 0xFFFFFF);
            drawString(fontRenderer, sub, listLeft + 3, y + 10, 0xAAAAAA);
        }

        int detailLeft = left + 190;
        int detailTop = top + 28;
        int detailRight = right - 10;
        int detailBottom = bottom - 10;
        drawRect(detailLeft - 1, detailTop - 1, detailRight + 1, detailBottom + 1, 0xFF444444);
        drawRect(detailLeft, detailTop, detailRight, detailBottom, 0xAA000000);

        drawDetail(detailLeft + 6, detailTop + 6);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 2) {
            requestList();
            return;
        }
        if (button.id == 3) {
            if (!selectedUuid.isEmpty()) {
                IrAutoMod.NETWORK.sendToServer(new TrainManagerDeleteMessage(selectedUuid));
                requestList();
            }
            return;
        }
        if (button.id == 4) {
            if (!selectedUuid.isEmpty()) {
                NBTTagCompound detail = TrainManagerClientCache.getDetail(selectedUuid);
                if (detail != null && detail.hasKey("timetable", Constants.NBT.TAG_COMPOUND)) {
                    NBTTagCompound timetable = detail.getCompoundTag("timetable");
                    mc.displayGuiScreen(new GuiTrainTimetable(selectedUuid, timetable));
                } else {
                    requestDetail(selectedUuid);
                }
            }
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
        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            listScroll = 0;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && isInsideList(mouseX, mouseY)) {
            int left = width / 2 - 180;
            int top = height / 2 - 110;
            int listLeft = left + 10;
            int listTop = top + 52;
            int rowH = 24;
            int relY = mouseY - (listTop + 2);
            int row = relY / rowH;
            if (row >= 0) {
                List<NBTTagCompound> entries = getFilteredEntries();
                int idx = listScroll + row;
                if (idx >= 0 && idx < entries.size()) {
                    String uuid = entries.get(idx).getString("trainUuid");
                    if (uuid != null && !uuid.isEmpty()) {
                        selectedUuid = uuid;
                        requestDetail(uuid);
                    }
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void requestList() {
        if (mc == null || mc.player == null) {
            return;
        }
        IrAutoMod.NETWORK.sendToServer(new TrainManagerListRequestMessage());
    }

    private void requestDetail(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        IrAutoMod.NETWORK.sendToServer(new TrainManagerDetailRequestMessage(uuid));
    }

    private List<NBTTagCompound> getFilteredEntries() {
        NBTTagCompound listTag = TrainManagerClientCache.getList();
        if (listTag == null || !listTag.hasKey("list", Constants.NBT.TAG_LIST)) {
            return new ArrayList<>();
        }
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        NBTTagList list = listTag.getTagList("list", Constants.NBT.TAG_COMPOUND);
        List<NBTTagCompound> out = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            if (e == null) {
                continue;
            }
            if (q.isEmpty()) {
                out.add(e);
                continue;
            }
            String uuid = e.getString("trainUuid");
            String number = e.getString("trainNumber");
            if ((uuid != null && uuid.toLowerCase().contains(q)) || (number != null && number.toLowerCase().contains(q))) {
                out.add(e);
            }
        }
        return out;
    }

    private void drawDetail(int x, int y) {
        if (selectedUuid.isEmpty()) {
            drawString(fontRenderer, "从左侧选择一个列车文件", x, y, 0xFFFFFF);
            return;
        }
        NBTTagCompound detail = TrainManagerClientCache.getDetail(selectedUuid);
        if (detail == null) {
            drawString(fontRenderer, "正在获取列车信息...", x, y, 0xAAAAAA);
            return;
        }
        boolean online = detail.getBoolean("online");
        double speed = detail.getDouble("speedKmh");
        int dim = detail.getInteger("dim");
        BlockPos pos = BlockPos.fromLong(detail.getLong("pos"));
        String fileName = selectedUuid + ".dat";
        drawString(fontRenderer, "文件: " + fileName, x, y, 0xFFFFFF);
        drawString(fontRenderer, "在线: " + (online ? "是" : "否"), x, y + 12, online ? 0x66FF66 : 0xFF7777);
        drawString(fontRenderer, "速度: " + String.format("%.1f", speed) + " km/h", x, y + 24, 0xFFFFFF);
        drawString(fontRenderer, "维度: " + dim, x, y + 36, 0xFFFFFF);
        drawString(fontRenderer, "坐标: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), x, y + 48, 0xFFFFFF);

        NBTTagCompound timetable = detail.hasKey("timetable", Constants.NBT.TAG_COMPOUND) ? detail.getCompoundTag("timetable") : null;
        String trainNumber = timetable == null ? "" : timetable.getString(TrainAutoPilotData.TRAIN_NUMBER);
        int stopCount = timetable != null && timetable.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)
            ? timetable.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND).tagCount()
            : 0;
        String nextStop = readNextStopName(detail.hasKey("live", Constants.NBT.TAG_COMPOUND) ? detail.getCompoundTag("live") : timetable);
        drawString(fontRenderer, "车次: " + (trainNumber == null || trainNumber.isEmpty() ? "(未设置)" : trainNumber), x, y + 66, 0xFFFFFF);
        drawString(fontRenderer, "下一站: " + (nextStop == null || nextStop.isEmpty() ? "(未知)" : nextStop), x, y + 78, 0xFFFFFF);
        drawString(fontRenderer, "时刻表条目: " + stopCount, x, y + 90, 0xFFFFFF);
    }

    private static String readNextStopName(NBTTagCompound data) {
        if (data == null) {
            return "";
        }
        if (!data.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)) {
            return "";
        }
        NBTTagList stops = data.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            return "";
        }
        int index = Math.max(0, Math.min(stops.tagCount() - 1, data.getInteger(TrainAutoPilotData.CURRENT_INDEX)));
        NBTTagCompound stop = stops.getCompoundTagAt(index);
        if (stop.hasKey("name", Constants.NBT.TAG_STRING)) {
            String name = stop.getString("name");
            return name == null ? "" : name;
        }
        return "站台" + (index + 1);
    }

    private boolean isInsideList(int mouseX, int mouseY) {
        int left = width / 2 - 180;
        int top = height / 2 - 110;
        int listLeft = left + 10;
        int listTop = top + 52;
        int listRight = left + 180;
        int listBottom = top + 210 - 28;
        return mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
    }
}

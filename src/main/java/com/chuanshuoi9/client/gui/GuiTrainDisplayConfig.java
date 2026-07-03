package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.TrainManagerClientCache;
import com.chuanshuoi9.network.TrainDisplayConfigMessage;
import com.chuanshuoi9.network.TrainManagerDetailRequestMessage;
import com.chuanshuoi9.network.TrainManagerListRequestMessage;
import com.chuanshuoi9.network.StationSyncMessage.StationData;
import com.chuanshuoi9.tile.TileTrainDisplay;
import com.chuanshuoi9.train.TrainAutoPilotData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiTrainDisplayConfig extends GuiScreen {
    private final BlockPos controllerPos;
    private GuiTextField searchField;
    private int listScroll;
    private int selectedIndex = -1;
    private int pickIndex = -1;
    private int ticks;
    private final List<String> trains = new ArrayList<>();

    public GuiTrainDisplayConfig(BlockPos controllerPos) {
        this.controllerPos = controllerPos;
    }

    @Override
    public void initGui() {
        ticks = 0;
        int left = width / 2 - 190;
        int top = height / 2 - 110;
        searchField = new GuiTextField(1, fontRenderer, left + 10, top + 30, 170, 18);
        searchField.setMaxStringLength(64);
        buttonList.clear();
        buttonList.add(new GuiButton(2, left + 10, top + 210 - 22, 52, 20, "刷新"));
        buttonList.add(new GuiButton(3, left + 66, top + 210 - 22, 52, 20, "添加"));
        buttonList.add(new GuiButton(4, left + 122, top + 210 - 22, 52, 20, "移除"));
        buttonList.add(new GuiButton(6, left + 252, top + 210 - 22, 60, 20, "保存"));
        loadFromTile();
        requestList();
    }

    @Override
    public void updateScreen() {
        ticks++;
        if (ticks % 60 == 0) {
            requestList();
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
        int left = width / 2 - 190;
        int top = height / 2 - 110;
        int right = left + 380;
        int bottom = top + 210;
        drawRect(left - 1, top - 1, right + 1, bottom + 1, 0xFF666666);
        drawRect(left, top, right, bottom, 0xCC101010);
        drawCenteredString(fontRenderer, "列车大屏配置", width / 2, top + 8, 0xFFFFFF);

        TileTrainDisplay tile = getTile();
        int w = tile == null ? 1 : tile.getWidthBlocks();
        int h = tile == null ? 1 : tile.getHeightBlocks();
        int maxLines = computeMaxLines(h);
        drawString(fontRenderer, "尺寸: " + w + "x" + h + "  最大行数: " + maxLines, left + 10, top + 20, 0xAAAAAA);
        drawString(fontRenderer, "搜索:", left + 10, top + 52, 0xFFFFFF);
        searchField.drawTextBox();
        drawString(fontRenderer, "站点来源: 列车时刻表下一站", left + 200, top + 52, 0xFFFFFF);

        int listLeft = left + 10;
        int listTop = top + 78;
        int listRight = left + 190;
        int listBottom = top + 210 - 28;
        drawRect(listLeft - 1, listTop - 1, listRight + 1, listBottom + 1, 0xFF444444);
        drawRect(listLeft, listTop, listRight, listBottom, 0xAA000000);

        List<NBTTagCompound> entries = getFilteredEntries();
        int rowH = 12;
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
            String title = (number == null || number.isEmpty()) ? "(无车次)" : number;
            int y = listTop + 2 + i * rowH;
            if (idx == pickIndex) {
                drawRect(listLeft + 1, y - 1, listRight - 1, y + rowH - 1, 0xFF2A2A5A);
            }
            drawString(fontRenderer, title, listLeft + 3, y, online ? 0x66FF66 : 0xFFFFFF);
            if (uuid != null && !uuid.isEmpty() && trains.contains(uuid)) {
                drawString(fontRenderer, "*", listRight - 8, y, 0xFFFF66);
            }
        }

        int previewLeft = left + 200;
        int previewTop = top + 78;
        int previewRight = right - 10;
        int previewBottom = bottom - 28;
        drawRect(previewLeft - 1, previewTop - 1, previewRight + 1, previewBottom + 1, 0xFF444444);
        drawRect(previewLeft, previewTop, previewRight, previewBottom, 0xAA000000);
        drawString(fontRenderer, "预览", previewLeft + 6, previewTop + 6, 0xFFFFFF);

        int y0 = previewTop + 20;
        int shown = 0;
        for (int i = 0; i < trains.size() && shown < maxLines; i++) {
            String uuid = trains.get(i);
            String line = buildPreviewLine(uuid);
            if (line == null || line.isEmpty()) {
                continue;
            }
            int color = i == selectedIndex ? 0xFFFF66 : 0xFFFFFF;
            drawString(fontRenderer, line, previewLeft + 6, y0 + shown * 12, color);
            shown++;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 2) {
            requestList();
            return;
        }
        if (button.id == 3) {
            addPicked();
            return;
        }
        if (button.id == 4) {
            removeSelected();
            return;
        }
        if (button.id == 6) {
            save();
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
            int left = width / 2 - 190;
            int top = height / 2 - 110;
            int listTop = top + 78;
            int rowH = 12;
            int relY = mouseY - (listTop + 2);
            int row = relY / rowH;
            if (row >= 0) {
                List<NBTTagCompound> entries = getFilteredEntries();
                int idx = listScroll + row;
                if (idx >= 0 && idx < entries.size()) {
                    pickIndex = idx;
                    String uuid = entries.get(idx).getString("trainUuid");
                    if (uuid != null && !uuid.isEmpty()) {
                        IrAutoMod.NETWORK.sendToServer(new TrainManagerDetailRequestMessage(uuid));
                    }
                }
            }
        }
        if (mouseButton == 0 && isInsidePreview(mouseX, mouseY)) {
            int left = width / 2 - 190;
            int top = height / 2 - 110;
            int previewTop = top + 78;
            int relY = mouseY - (previewTop + 20);
            int row = relY / 12;
            int maxLines = computeMaxLines(getTile() == null ? 1 : getTile().getHeightBlocks());
            if (row >= 0 && row < maxLines && row < trains.size()) {
                selectedIndex = row;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    void applyPickedStation(StationData station) {
        // Deprecated: train display now reads stop names directly from train timetable data.
    }

    private TileTrainDisplay getTile() {
        if (mc == null || mc.world == null || controllerPos == null) {
            return null;
        }
        TileEntity te = mc.world.getTileEntity(controllerPos);
        return te instanceof TileTrainDisplay ? (TileTrainDisplay) te : null;
    }

    private void loadFromTile() {
        TileTrainDisplay tile = getTile();
        if (tile == null) {
            return;
        }
        TileTrainDisplay controller = tile.resolveControllerClient();
        if (controller == null) {
            return;
        }
        NBTTagCompound cfg = controller.snapshotConfig();
        trains.clear();
        if (cfg.hasKey("tdTrains", Constants.NBT.TAG_LIST)) {
            NBTTagList list = cfg.getTagList("tdTrains", Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                String s = list.getStringTagAt(i);
                if (s != null && !s.trim().isEmpty()) {
                    trains.add(s.trim());
                }
            }
        }
    }

    private void save() {
        TileTrainDisplay tile = getTile();
        if (tile == null) {
            return;
        }
        NBTTagCompound cfg = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        int maxLines = computeMaxLines(tile.getHeightBlocks());
        for (int i = 0; i < trains.size() && i < maxLines; i++) {
            list.appendTag(new net.minecraft.nbt.NBTTagString(trains.get(i)));
        }
        cfg.setTag("tdTrains", list);
        IrAutoMod.NETWORK.sendToServer(new TrainDisplayConfigMessage(tile.getControllerPos(), cfg));
        mc.displayGuiScreen(null);
    }

    private void requestList() {
        IrAutoMod.NETWORK.sendToServer(new TrainManagerListRequestMessage());
    }

    private void addPicked() {
        List<NBTTagCompound> entries = getFilteredEntries();
        if (pickIndex < 0 || pickIndex >= entries.size()) {
            return;
        }
        String uuid = entries.get(pickIndex).getString("trainUuid");
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        TileTrainDisplay tile = getTile();
        int maxLines = tile == null ? 20 : computeMaxLines(tile.getHeightBlocks());
        if (trains.size() >= maxLines) {
            return;
        }
        if (!trains.contains(uuid)) {
            trains.add(uuid);
        }
    }

    private void removeSelected() {
        if (selectedIndex < 0 || selectedIndex >= trains.size()) {
            return;
        }
        trains.remove(selectedIndex);
        selectedIndex = Math.min(selectedIndex, trains.size() - 1);
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

    private String buildPreviewLine(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return "";
        }
        NBTTagCompound detail = TrainManagerClientCache.getDetail(uuid);
        NBTTagCompound timetable = detail != null && detail.hasKey("timetable", Constants.NBT.TAG_COMPOUND) ? detail.getCompoundTag("timetable") : null;
        String trainNumber = timetable == null ? "" : TrainAutoPilotData.normalizeTrainNumber(timetable.getString(TrainAutoPilotData.TRAIN_NUMBER));
        String nextStop = "";
        if (detail != null) {
            if (detail.hasKey("live", Constants.NBT.TAG_COMPOUND)) {
                nextStop = readNextStopName(detail.getCompoundTag("live"));
            } else if (timetable != null) {
                nextStop = readNextStopName(timetable);
            }
        } else if (timetable != null) {
            nextStop = readNextStopName(timetable);
        }
        String num = trainNumber == null || trainNumber.isEmpty() ? "?" : trainNumber;
        String stop = normalizeStopName(nextStop);
        return num + "  " + stop + "  --";
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
        return normalizeStopName(stops.getCompoundTagAt(index).getString("name"));
    }

    private static int computeMaxLines(int heightBlocks) {
        int hPx = Math.max(1, heightBlocks) * 16;
        int fontH = net.minecraft.client.Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
        return Math.max(1, (hPx / (fontH + 1)) * 2);
    }

    private static String normalizeStopName(String value) {
        if (value == null) {
            return "未命名站点";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "未命名站点" : trimmed;
    }

    private boolean isInsideList(int mouseX, int mouseY) {
        int left = width / 2 - 190;
        int top = height / 2 - 110;
        int listLeft = left + 10;
        int listTop = top + 78;
        int listRight = left + 190;
        int listBottom = top + 210 - 28;
        return mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
    }

    private boolean isInsidePreview(int mouseX, int mouseY) {
        int left = width / 2 - 190;
        int top = height / 2 - 110;
        int previewLeft = left + 200;
        int previewTop = top + 78;
        int previewRight = left + 380 - 10;
        int previewBottom = top + 210 - 28;
        return mouseX >= previewLeft && mouseX <= previewRight && mouseY >= previewTop && mouseY <= previewBottom;
    }
}

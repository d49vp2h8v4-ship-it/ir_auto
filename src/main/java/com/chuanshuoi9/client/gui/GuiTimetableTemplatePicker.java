package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.TimetableTemplateClientCache;
import com.chuanshuoi9.network.TimetableTemplateListRequestMessage;
import com.chuanshuoi9.network.TimetableTemplateLoadRequestMessage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiTimetableTemplatePicker extends GuiScreen {
    public interface Receiver {
        void onTemplatePicked(String templateId);
    }

    private final GuiScreen parent;
    private final Receiver receiver;
    private GuiTextField searchField;
    private int listScroll;
    private String selectedId = "";

    public GuiTimetableTemplatePicker(GuiScreen parent, Receiver receiver) {
        this.parent = parent;
        this.receiver = receiver;
    }

    @Override
    public void initGui() {
        int left = width / 2 - 180;
        int top = height / 2 - 110;
        searchField = new GuiTextField(1, fontRenderer, left + 10, top + 28, 160, 18);
        searchField.setMaxStringLength(64);
        buttonList.clear();
        buttonList.add(new GuiButton(2, left + 10, top + 210 - 22, 52, 20, "刷新"));
        buttonList.add(new GuiButton(3, left + 66, top + 210 - 22, 52, 20, "选择"));
        buttonList.add(new GuiButton(4, left + 122, top + 210 - 22, 52, 20, "取消"));
        requestList();
        super.initGui();
    }

    @Override
    public void updateScreen() {
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
        drawCenteredString(fontRenderer, "选择时刻表模板", width / 2, top + 8, 0xFFFFFF);

        drawString(fontRenderer, "搜索:", left + 10, top + 16, 0xFFFFFF);
        searchField.drawTextBox();

        int listLeft = left + 10;
        int listTop = top + 52;
        int listRight = right - 10;
        int listBottom = top + 210 - 28;
        drawRect(listLeft - 1, listTop - 1, listRight + 1, listBottom + 1, 0xFF444444);
        drawRect(listLeft, listTop, listRight, listBottom, 0xAA000000);

        List<NBTTagCompound> entries = getFilteredEntries();
        int rowH = 22;
        int visibleRows = Math.max(1, (listBottom - listTop - 4) / rowH);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        listScroll = Math.max(0, Math.min(maxScroll, listScroll));

        for (int i = 0; i < visibleRows; i++) {
            int idx = listScroll + i;
            if (idx >= entries.size()) {
                break;
            }
            NBTTagCompound e = entries.get(idx);
            String id = e.getString("id");
            String name = e.getString("name");
            String title = (name == null || name.trim().isEmpty()) ? "(未命名模板)" : name.trim();

            int y = listTop + 2 + i * rowH;
            boolean selected = id != null && id.equals(selectedId);
            if (selected) {
                drawRect(listLeft + 1, y - 1, listRight - 1, y + rowH - 1, 0xFF2A2A5A);
            }
            drawString(fontRenderer, title, listLeft + 4, y + 6, 0xFFFFFF);
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
            if (selectedId != null && !selectedId.isEmpty()) {
                if (receiver != null) {
                    receiver.onTemplatePicked(selectedId);
                }
                IrAutoMod.NETWORK.sendToServer(new TimetableTemplateLoadRequestMessage(selectedId));
                mc.displayGuiScreen(parent);
            }
            return;
        }
        if (button.id == 4) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(parent);
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
            int rowH = 22;
            int relY = mouseY - (listTop + 2);
            int row = relY / rowH;
            if (row >= 0) {
                List<NBTTagCompound> entries = getFilteredEntries();
                int idx = listScroll + row;
                if (idx >= 0 && idx < entries.size()) {
                    String id = entries.get(idx).getString("id");
                    if (id != null && !id.isEmpty()) {
                        selectedId = id;
                        IrAutoMod.NETWORK.sendToServer(new TimetableTemplateLoadRequestMessage(id));
                    }
                }
            }
        }
    }

    private void requestList() {
        if (mc == null || mc.player == null) {
            return;
        }
        IrAutoMod.NETWORK.sendToServer(new TimetableTemplateListRequestMessage());
    }

    private List<NBTTagCompound> getFilteredEntries() {
        NBTTagCompound listTag = TimetableTemplateClientCache.getList();
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
            String id = e.getString("id");
            String name = e.getString("name");
            if ((id != null && id.toLowerCase().contains(q)) || (name != null && name.toLowerCase().contains(q))) {
                out.add(e);
            }
        }
        return out;
    }

    private boolean isInsideList(int mouseX, int mouseY) {
        int left = width / 2 - 180;
        int top = height / 2 - 110;
        int listLeft = left + 10;
        int listTop = top + 52;
        int listRight = left + 360 - 10;
        int listBottom = top + 210 - 28;
        return mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom;
    }

    public void refreshFromCache() {
    }
}

package com.chuanshuoi9.client.gui;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.SignalStatusClientCache;
import com.chuanshuoi9.network.SignalStatusRequestMessage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;

public class GuiTrainSignalStatus extends GuiScreen {
    private final BlockPos pos;
    private int tickCounter = 0;

    public GuiTrainSignalStatus(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - 150;
        int top = height / 2 - 80;
        buttonList.add(new GuiButton(1, left + 10, top + 150, 90, 20, "刷新"));
        buttonList.add(new GuiButton(2, left + 200, top + 150, 90, 20, "关闭"));
        request();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tickCounter++;
        if (tickCounter >= 10) {
            tickCounter = 0;
            request();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 150;
        int top = height / 2 - 80;
        drawRect(left - 1, top - 1, left + 301, top + 181, 0xFF666666);
        drawRect(left, top, left + 300, top + 180, 0xCC101010);
        drawCenteredString(fontRenderer, "信号机状态", width / 2, top + 6, 0xFFFFFF);

        int y = top + 22;
        drawString(fontRenderer, "位置: " + fmtPos(pos), left + 10, y, 0xCCCCCC);
        y += 12;

        NBTTagCompound tag = SignalStatusClientCache.get(pos);
        if (tag == null || tag.hasNoTags()) {
            drawString(fontRenderer, "等待服务器数据...", left + 10, y, 0xFFFF66);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        String facing = tag.getString("facing");
        drawString(fontRenderer, "朝向: " + (facing.isEmpty() ? "-" : facing), left + 10, y, 0xCCCCCC);
        y += 12;

        BlockPos a = BlockPos.fromLong(tag.getLong("a"));
        BlockPos b = BlockPos.fromLong(tag.getLong("b"));
        drawString(fontRenderer, "A: " + fmtPos(a), left + 10, y, 0x00D27F);
        y += 12;
        drawString(fontRenderer, "B: " + fmtPos(b), left + 10, y, 0x00D27F);
        y += 14;

        drawSegmentLine(left, y, "B→A", tag, "ba_");

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int drawSegmentLine(int left, int y, String title, NBTTagCompound tag, String prefix) {
        int keys = tag.getInteger(prefix + "keys");
        boolean hasTurnout = tag.getBoolean(prefix + "turnout");
        boolean aligned = tag.getBoolean(prefix + "aligned");
        boolean occupied = tag.getBoolean(prefix + "occupied");
        int trainCount = tag.getInteger(prefix + "trainCount");
        double maxSpeed = tag.getDouble(prefix + "maxSpeedKmh");
        double clearTime = tag.getDouble(prefix + "clearTimeSec");
        double approachSpeed = tag.getDouble(prefix + "approachSpeedKmh");
        double approachEta = tag.getDouble(prefix + "approachEtaSec");
        double followSlowTo = tag.hasKey(prefix + "followSlowToKmh") ? tag.getDouble(prefix + "followSlowToKmh") : Double.NaN;
        double followLimit = tag.hasKey(prefix + "followLimitKmh") ? tag.getDouble(prefix + "followLimitKmh") : Double.NaN;
        String strategy = tag.getString(prefix + "strategy");

        String line1 = title
            + " keys=" + keys
            + " 道岔=" + (hasTurnout ? "是" : "否")
            + " 可通行=" + (aligned ? "是" : "否")
            + " 占用=" + (occupied ? "是" : "否");
        int color1 = occupied ? 0xFF6666 : (aligned ? 0x66FF66 : 0xFFFF66);
        drawString(fontRenderer, line1, left + 10, y, color1);
        y += 12;

        String line2 = "列车=" + trainCount
            + " vmax=" + fmt1(maxSpeed) + "km/h"
            + " 清空=" + fmtTime(clearTime)
            + " 来车=" + fmt1(approachSpeed) + "km/h"
            + " 到达=" + fmtTime(approachEta)
            + " 策略=" + (strategy.isEmpty() ? "-" : strategy);
        int color2 = strategy.contains("减速") ? 0xFFFF66 : (strategy.contains("制动") ? 0xFF6666 : 0x66FF66);
        drawString(fontRenderer, line2, left + 10, y, color2);
        y += 12;

        String line3 = "后车减速到=" + fmtLimit(followSlowTo) + "km/h"
            + " 后车限速=" + fmtLimit(followLimit) + "km/h";
        int color3 = Double.isFinite(followSlowTo) ? 0xFFFF66 : 0xCCCCCC;
        drawString(fontRenderer, line3, left + 10, y, color3);
        return y + 12;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            request();
            return;
        }
        if (button.id == 2) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void request() {
        if (pos == null) {
            return;
        }
        IrAutoMod.NETWORK.sendToServer(new SignalStatusRequestMessage(pos));
    }

    private static String fmtPos(BlockPos p) {
        if (p == null) {
            return "-";
        }
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static String fmt1(double v) {
        if (!Double.isFinite(v)) {
            return "-";
        }
        return String.format("%.1f", v);
    }

    private static String fmtLimit(double v) {
        if (Double.isNaN(v)) {
            return "-";
        }
        if (Double.isInfinite(v)) {
            return "∞";
        }
        if (!Double.isFinite(v)) {
            return "-";
        }
        return String.format("%.1f", v);
    }

    private static String fmtTime(double sec) {
        if (!Double.isFinite(sec)) {
            return "∞";
        }
        if (sec <= 0.0) {
            return "0s";
        }
        if (sec < 60.0) {
            return String.format("%.0fs", sec);
        }
        return String.format("%.1fmin", sec / 60.0);
    }
}

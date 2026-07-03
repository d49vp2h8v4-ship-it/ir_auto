package com.chuanshuoi9.client.hud;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.TrainManagerClientCache;
import com.chuanshuoi9.item.ModItems;
import com.chuanshuoi9.network.TrainManagerListRequestMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID, value = Side.CLIENT)
public class TrainWarningHud {
    private static final int UPDATE_TICKS = 24;
    private static final double RANGE_M = 2000.0;
    private static final double RANGE_M_SQ = RANGE_M * RANGE_M;

    private static int tickCounter;
    private static List<String> cachedLines = new ArrayList<>();
    private static boolean cachedHasDevice;
    private static int lastRequestTick = -UPDATE_TICKS;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            cachedLines = new ArrayList<>();
            cachedHasDevice = false;
            tickCounter = 0;
            return;
        }
        EntityPlayer player = mc.player;
        boolean has = hasWarningDevice(player.inventory);
        cachedHasDevice = has;
        if (!has) {
            cachedLines = new ArrayList<>();
            tickCounter = 0;
            return;
        }
        tickCounter++;
        if (tickCounter % UPDATE_TICKS != 0) {
            return;
        }
        requestTrainList();
        cachedLines = computeLines(player);
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!cachedHasDevice) {
            return;
        }
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        if (cachedLines == null || cachedLines.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        ScaledResolution res = new ScaledResolution(mc);
        int pad = 6;
        int y = pad;
        for (String line : cachedLines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            int w = fr.getStringWidth(line);
            int x = res.getScaledWidth() - pad - w;
            fr.drawStringWithShadow(line, x, y, 0xFFFFFF);
            y += fr.FONT_HEIGHT + 2;
        }
    }

    private static boolean hasWarningDevice(IInventory inv) {
        if (inv == null) {
            return false;
        }
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == ModItems.TRAIN_WARNING_DEVICE) {
                return true;
            }
        }
        return false;
    }

    private static List<String> computeLines(EntityPlayer player) {
        NBTTagCompound listTag = TrainManagerClientCache.getList();
        List<String> out = new ArrayList<>();
        if (listTag == null || !listTag.hasKey("list", Constants.NBT.TAG_LIST)) {
            out.add("列车数据同步中...");
            return out;
        }

        NBTTagList list = listTag.getTagList("list", Constants.NBT.TAG_COMPOUND);
        NBTTagCompound nearest = null;
        double bestSq = Double.MAX_VALUE;
        int playerDim = player.world == null || player.world.provider == null ? 0 : player.world.provider.getDimension();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry == null) {
                continue;
            }
            if (!entry.getBoolean("online")) {
                continue;
            }
            if (entry.getInteger("dim") != playerDim) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(entry.getLong("pos"));
            double dx = pos.getX() + 0.5 - player.posX;
            double dz = pos.getZ() + 0.5 - player.posZ;
            double dsq = dx * dx + dz * dz;
            if (dsq > RANGE_M_SQ) {
                continue;
            }
            if (dsq < bestSq) {
                bestSq = dsq;
                nearest = entry;
            }
        }

        if (nearest == null) {
            out.add("附近2km无列车");
            return out;
        }

        double dist = Math.sqrt(bestSq);
        double speed = Math.abs(nearest.getDouble("speedKmh"));
        String trainNumber = nearest.getString("trainNumber");
        if (trainNumber == null || trainNumber.isEmpty()) {
            trainNumber = "?";
        }
        String nextStop = normalizeStopName(nearest.getString("nextStop"));
        out.add("车次: " + trainNumber);
        out.add(String.format("速度: %.1f km/h", speed));
        out.add(formatDistance(dist));
        out.add("下一站: " + nextStop);
        return out;
    }

    private static String formatDistance(double meters) {
        if (meters >= 1000.0) {
            return String.format("距离: %.2f km", meters / 1000.0);
        }
        return String.format("距离: %.0f m", meters);
    }

    private static String normalizeStopName(String name) {
        if (name == null) {
            return "未命名站点";
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "未命名站点" : trimmed;
    }

    private static void requestTrainList() {
        if (tickCounter - lastRequestTick < UPDATE_TICKS) {
            return;
        }
        lastRequestTick = tickCounter;
        IrAutoMod.NETWORK.sendToServer(new TrainManagerListRequestMessage());
    }
}

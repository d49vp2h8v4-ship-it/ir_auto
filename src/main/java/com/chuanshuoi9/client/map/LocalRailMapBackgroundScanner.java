package com.chuanshuoi9.client.map;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.client.gui.GuiRailwayMap;
import com.chuanshuoi9.client.gui.GuiRailwayMapStationPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID, value = Side.CLIENT)
public class LocalRailMapBackgroundScanner {
    private static final int RAIL_SCAN_TICKS_MAP_OPEN = 20;
    private static final int RAIL_SCAN_TICKS_BACKGROUND = 100;
    private static final int RAIL_SCAN_MIN_TICKS_ON_MOVE = 40;

    private static final int STATION_SCAN_TICKS_MAP_OPEN = 60;
    private static final int STATION_SCAN_TICKS_BACKGROUND = 300;

    private static int ticksSinceRailScan = 0;
    private static int ticksSinceStationScan = 0;
    private static int lastDimension = Integer.MIN_VALUE;
    private static int lastScannedChunkX = Integer.MIN_VALUE;
    private static int lastScannedChunkZ = Integer.MIN_VALUE;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null || !mc.isSingleplayer()) {
            lastDimension = Integer.MIN_VALUE;
            lastScannedChunkX = Integer.MIN_VALUE;
            lastScannedChunkZ = Integer.MIN_VALUE;
            ticksSinceRailScan = 0;
            ticksSinceStationScan = 0;
            return;
        }

        boolean mapOpen = mc.currentScreen instanceof GuiRailwayMap || mc.currentScreen instanceof GuiRailwayMapStationPicker;
        int railTarget = mapOpen ? RAIL_SCAN_TICKS_MAP_OPEN : RAIL_SCAN_TICKS_BACKGROUND;
        int stationTarget = mapOpen ? STATION_SCAN_TICKS_MAP_OPEN : STATION_SCAN_TICKS_BACKGROUND;

        int dim = mc.world.provider.getDimension();
        ChunkPos chunkPos = new ChunkPos(mc.player.getPosition());

        if (dim != lastDimension) {
            lastDimension = dim;
            lastScannedChunkX = chunkPos.x;
            lastScannedChunkZ = chunkPos.z;
            ticksSinceRailScan = railTarget;
            ticksSinceStationScan = stationTarget;
        } else {
            ticksSinceRailScan++;
            ticksSinceStationScan++;
        }

        boolean movedChunkSinceLastScan = chunkPos.x != lastScannedChunkX || chunkPos.z != lastScannedChunkZ;
        boolean shouldRailScan = ticksSinceRailScan >= railTarget || (movedChunkSinceLastScan && ticksSinceRailScan >= RAIL_SCAN_MIN_TICKS_ON_MOVE);
        if (!shouldRailScan) {
            return;
        }

        boolean shouldStationScan = mapOpen || ticksSinceStationScan >= stationTarget;
        LocalRailMapScanner.refreshClientCacheFromLocalWorld(shouldStationScan);
        ticksSinceRailScan = 0;
        if (shouldStationScan) {
            ticksSinceStationScan = 0;
        }
        lastScannedChunkX = chunkPos.x;
        lastScannedChunkZ = chunkPos.z;
    }
}

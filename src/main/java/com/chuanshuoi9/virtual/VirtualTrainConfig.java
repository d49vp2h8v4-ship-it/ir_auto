package com.chuanshuoi9.virtual;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class VirtualTrainConfig {
    public static boolean enabled = false;
    public static int activateRangeBlocks = 256;
    public static int idleTicksBeforeVirtualize = 20 * 10;
    public static int simTickInterval = 5;
    public static int virtualizeScanInterval = 20;
    public static int signalIndexRebuildInterval = 40;
    public static int loadedTrainKeysRecalcInterval = 20;
    public static int segmentOccupancyRecalcInterval = 20;
    public static int maxTraceStepsPerTick = 2000;
    public static int maxRouteNodes = 20000;
    public static double minSpeedKmhToVirtualize = 0.2;
    public static int maxSignalsToCheck = 128;

    public static void load(File suggestedConfigFile) {
        Configuration cfg = new Configuration(suggestedConfigFile);
        cfg.load();
        String cat = "ir_virtual_train";
        enabled = cfg.getBoolean("enabled", cat, enabled, "");
        activateRangeBlocks = cfg.getInt("activateRangeBlocks", cat, activateRangeBlocks, 16, 4096, "");
        idleTicksBeforeVirtualize = cfg.getInt("idleTicksBeforeVirtualize", cat, idleTicksBeforeVirtualize, 0, 20 * 60 * 60, "");
        simTickInterval = cfg.getInt("simTickInterval", cat, simTickInterval, 1, 200, "");
        virtualizeScanInterval = cfg.getInt("virtualizeScanInterval", cat, virtualizeScanInterval, 1, 2000, "");
        signalIndexRebuildInterval = cfg.getInt("signalIndexRebuildInterval", cat, signalIndexRebuildInterval, 1, 2000, "");
        loadedTrainKeysRecalcInterval = cfg.getInt("loadedTrainKeysRecalcInterval", cat, loadedTrainKeysRecalcInterval, 1, 2000, "");
        segmentOccupancyRecalcInterval = cfg.getInt("segmentOccupancyRecalcInterval", cat, segmentOccupancyRecalcInterval, 1, 2000, "");
        maxTraceStepsPerTick = cfg.getInt("maxTraceStepsPerTick", cat, maxTraceStepsPerTick, 100, 20000, "");
        maxRouteNodes = cfg.getInt("maxRouteNodes", cat, maxRouteNodes, 500, 200000, "");
        minSpeedKmhToVirtualize = cfg.getFloat("minSpeedKmhToVirtualize", cat, (float) minSpeedKmhToVirtualize, 0.0f, 3000.0f, "");
        maxSignalsToCheck = cfg.getInt("maxSignalsToCheck", cat, maxSignalsToCheck, 0, 2000, "");
        if (cfg.hasChanged()) {
            cfg.save();
        }
    }
}

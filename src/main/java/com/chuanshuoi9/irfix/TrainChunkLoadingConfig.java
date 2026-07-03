package com.chuanshuoi9.irfix;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class TrainChunkLoadingConfig {
    public static boolean enabled = true;
    public static double speedThresholdKmh = 0.05;
    public static int movingRadiusChunks = 4;
    public static int idleRadiusChunks = 2;
    public static int keepAliveTicksAfterStop = 600;   // 30 seconds
    public static int aheadChunksMax = 5;
    public static int aheadRadiusChunks = 3;
    public static int releaseDelayTicks = 100;          // 5 second grace before unload

    public static void load(File suggestedConfigFile) {
        Configuration cfg = new Configuration(suggestedConfigFile);
        cfg.load();
        String cat = "ir_chunk_loading";
        enabled = cfg.getBoolean("enabled", cat, enabled, "");
        speedThresholdKmh = cfg.getFloat("speedThresholdKmh", cat, (float) speedThresholdKmh, 0.0f, 3000.0f, "");
        movingRadiusChunks = cfg.getInt("movingRadiusChunks", cat, movingRadiusChunks, 0, 8, "");
        idleRadiusChunks = cfg.getInt("idleRadiusChunks", cat, idleRadiusChunks, 0, 4, "");
        keepAliveTicksAfterStop = cfg.getInt("keepAliveTicksAfterStop", cat, keepAliveTicksAfterStop, 0, 20 * 60 * 10, "");
        aheadChunksMax = cfg.getInt("aheadChunksMax", cat, aheadChunksMax, 0, 10, "");
        aheadRadiusChunks = cfg.getInt("aheadRadiusChunks", cat, aheadRadiusChunks, 0, 4, "");
        releaseDelayTicks = cfg.getInt("releaseDelayTicks", cat, releaseDelayTicks, 0, 2000, "");
        if (cfg.hasChanged()) {
            cfg.save();
        }
    }
}

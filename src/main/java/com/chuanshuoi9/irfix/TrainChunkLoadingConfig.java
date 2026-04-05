package com.chuanshuoi9.irfix;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class TrainChunkLoadingConfig {
    public static boolean enabled = true;
    public static double speedThresholdKmh = 0.05;
    public static int movingRadiusChunks = 2;
    public static int idleRadiusChunks = 1;
    public static int keepAliveTicksAfterStop = 600;
    public static int aheadChunksMax = 4;
    public static int aheadRadiusChunks = 1;
    public static int maxChunksPerTicket = 64;
    public static int maxTicketsPerWorld = 128;

    public static void load(File suggestedConfigFile) {
        Configuration cfg = new Configuration(suggestedConfigFile);
        cfg.load();
        String cat = "ir_chunk_loading";
        enabled = cfg.getBoolean("enabled", cat, enabled, "");
        speedThresholdKmh = cfg.getFloat("speedThresholdKmh", cat, (float) speedThresholdKmh, 0.0f, 3000.0f, "");
        movingRadiusChunks = cfg.getInt("movingRadiusChunks", cat, movingRadiusChunks, 0, 4, "");
        idleRadiusChunks = cfg.getInt("idleRadiusChunks", cat, idleRadiusChunks, 0, 2, "");
        keepAliveTicksAfterStop = cfg.getInt("keepAliveTicksAfterStop", cat, keepAliveTicksAfterStop, 0, 20 * 60 * 10, "");
        aheadChunksMax = cfg.getInt("aheadChunksMax", cat, aheadChunksMax, 0, 8, "");
        aheadRadiusChunks = cfg.getInt("aheadRadiusChunks", cat, aheadRadiusChunks, 0, 2, "");
        maxChunksPerTicket = cfg.getInt("maxChunksPerTicket", cat, maxChunksPerTicket, 1, 2000, "");
        maxTicketsPerWorld = cfg.getInt("maxTicketsPerWorld", cat, maxTicketsPerWorld, 1, 2000, "");
        if (cfg.hasChanged()) {
            cfg.save();
        }
    }
}

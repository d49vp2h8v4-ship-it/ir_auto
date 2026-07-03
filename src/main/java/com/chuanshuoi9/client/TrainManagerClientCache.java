package com.chuanshuoi9.client;

import net.minecraft.nbt.NBTTagCompound;

public class TrainManagerClientCache {
    private static NBTTagCompound LIST = new NBTTagCompound();
    private static final java.util.Map<String, NBTTagCompound> DETAILS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, long[]> TRAIN_POS = new java.util.HashMap<>();

    public static void updateList(NBTTagCompound tag) {
        LIST = tag == null ? new NBTTagCompound() : tag;
    }

    public static NBTTagCompound getList() {
        return LIST;
    }

    public static void updateDetail(String trainUuid, NBTTagCompound tag) {
        if (trainUuid == null) {
            return;
        }
        DETAILS.put(trainUuid, tag == null ? new NBTTagCompound() : tag);
    }

    public static NBTTagCompound getDetail(String trainUuid) {
        if (trainUuid == null) {
            return null;
        }
        return DETAILS.get(trainUuid);
    }

    public static void updateTrainPositions(int dimension, long[] packedXZ) {
        TRAIN_POS.put(dimension, packedXZ == null ? new long[0] : packedXZ);
    }

    public static long[] getTrainPositions(int dimension) {
        return TRAIN_POS.getOrDefault(dimension, new long[0]);
    }
}

package com.chuanshuoi9.client;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class SignalStatusClientCache {
    private static final Map<Long, NBTTagCompound> DATA = new HashMap<>();

    public static void update(BlockPos pos, NBTTagCompound tag) {
        if (pos == null) {
            return;
        }
        DATA.put(pos.toLong(), tag == null ? new NBTTagCompound() : tag);
    }

    public static NBTTagCompound get(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return DATA.get(pos.toLong());
    }
}

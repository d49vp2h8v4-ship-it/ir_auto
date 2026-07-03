package com.chuanshuoi9.train;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TrainTimetableStorage {
    private static final String DIR_NAME = "ir_time";

    public static File getStorageDir(World world) {
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, DIR_NAME);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        return storageDir;
    }

    public static void saveTimetable(Entity train, NBTTagCompound data) {
        if (train == null || train.world == null || train.world.isRemote) return;
        
        File dir = getStorageDir(train.world);
        File file = new File(dir, train.getUniqueID().toString() + ".dat");
        
        try {
            CompressedStreamTools.writeCompressed(data, new java.io.FileOutputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static NBTTagCompound loadTimetable(Entity train) {
        if (train == null || train.world == null || train.world.isRemote) return null;
        
        File dir = getStorageDir(train.world);
        File file = new File(dir, train.getUniqueID().toString() + ".dat");
        
        if (!file.exists()) return null;
        
        try {
            return CompressedStreamTools.readCompressed(new java.io.FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Set<UUID> getAllTimetables(World world) {
        if (world == null || world.isRemote) {
            return Collections.emptySet();
        }
        File dir = getStorageDir(world);
        File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".dat"));
        if (files == null || files.length == 0) {
            return Collections.emptySet();
        }
        Set<UUID> out = new HashSet<>();
        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith(".dat")) {
                continue;
            }
            String id = name.substring(0, name.length() - 4);
            try {
                out.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static Set<UUID> getAllTimetables() {
        if (FMLCommonHandler.instance().getMinecraftServerInstance() == null) {
            return Collections.emptySet();
        }
        World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        return getAllTimetables(world);
    }

    public static NBTTagCompound getTimetable(World world, UUID trainId) {
        if (world == null || world.isRemote || trainId == null) {
            return null;
        }
        File dir = getStorageDir(world);
        File file = new File(dir, trainId.toString() + ".dat");
        if (!file.exists()) {
            return null;
        }
        try {
            return CompressedStreamTools.readCompressed(new java.io.FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static NBTTagCompound getTimetable(UUID trainId) {
        if (FMLCommonHandler.instance().getMinecraftServerInstance() == null) {
            return null;
        }
        World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        return getTimetable(world, trainId);
    }
}

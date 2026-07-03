package com.chuanshuoi9.train;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimetableTemplateStorage {
    private static final String DIR_NAME = "ir_time_templates";

    public static File getStorageDir(World world) {
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, DIR_NAME);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        return storageDir;
    }

    public static void saveTemplate(World world, UUID templateId, NBTTagCompound data) {
        if (world == null || world.isRemote || templateId == null || data == null) {
            return;
        }
        File dir = getStorageDir(world);
        File file = new File(dir, templateId.toString() + ".dat");
        try {
            CompressedStreamTools.writeCompressed(data, new java.io.FileOutputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static NBTTagCompound loadTemplate(World world, UUID templateId) {
        if (world == null || world.isRemote || templateId == null) {
            return null;
        }
        File dir = getStorageDir(world);
        File file = new File(dir, templateId.toString() + ".dat");
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

    public static boolean deleteTemplate(World world, UUID templateId) {
        if (world == null || world.isRemote || templateId == null) {
            return false;
        }
        File dir = getStorageDir(world);
        File file = new File(dir, templateId.toString() + ".dat");
        if (!file.exists()) {
            return false;
        }
        return file.delete();
    }

    public static Map<UUID, String> listTemplates(World world) {
        if (world == null || world.isRemote) {
            return Collections.emptyMap();
        }
        File dir = getStorageDir(world);
        File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".dat"));
        if (files == null || files.length == 0) {
            return Collections.emptyMap();
        }
        Map<UUID, String> out = new HashMap<>();
        for (File f : files) {
            String name = f.getName();
            if (name == null || !name.endsWith(".dat")) {
                continue;
            }
            String id = name.substring(0, name.length() - 4);
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            NBTTagCompound data = loadTemplate(world, uuid);
            String display = data != null ? data.getString("name") : "";
            out.put(uuid, display == null ? "" : display);
        }
        return out;
    }
}


package com.chuanshuoi9.client;

import net.minecraft.nbt.NBTTagCompound;

public class TimetableTemplateClientCache {
    private static NBTTagCompound LIST = new NBTTagCompound();
    private static final java.util.Map<String, NBTTagCompound> TEMPLATES = new java.util.HashMap<>();

    public static void updateList(NBTTagCompound tag) {
        LIST = tag == null ? new NBTTagCompound() : tag;
    }

    public static NBTTagCompound getList() {
        return LIST;
    }

    public static void updateTemplate(String templateId, NBTTagCompound tag) {
        if (templateId == null) {
            return;
        }
        TEMPLATES.put(templateId, tag == null ? new NBTTagCompound() : tag);
    }

    public static NBTTagCompound getTemplate(String templateId) {
        if (templateId == null) {
            return null;
        }
        return TEMPLATES.get(templateId);
    }
}


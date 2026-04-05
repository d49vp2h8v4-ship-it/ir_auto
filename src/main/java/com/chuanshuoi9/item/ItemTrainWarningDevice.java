package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import net.minecraft.item.Item;

public class ItemTrainWarningDevice extends Item {
    public ItemTrainWarningDevice() {
        setRegistryName(IrAutoMod.MODID, "train_warning_device");
        setUnlocalizedName(IrAutoMod.MODID + ".train_warning_device");
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }
}


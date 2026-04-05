package com.chuanshuoi9.client;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.item.ModItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID, value = Side.CLIENT)
public class ModItemModels {
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ModItems.RAILWAY_MAP, 0, new ModelResourceLocation(ModItems.RAILWAY_MAP.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.TRAIN_CONTROL_PAPER, 0, new ModelResourceLocation(ModItems.TRAIN_CONTROL_PAPER.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.TRAIN_MANAGER, 0, new ModelResourceLocation(ModItems.TRAIN_MANAGER.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.TRAIN_SIGNAL, 0, new ModelResourceLocation(ModItems.TRAIN_SIGNAL.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.TURNOUT_MACHINE, 0, new ModelResourceLocation(ModItems.TURNOUT_MACHINE.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.STATION_MARKER, 0, new ModelResourceLocation(ModItems.STATION_MARKER.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.SIGNAL_LIGHT, 0, new ModelResourceLocation(ModItems.SIGNAL_LIGHT.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.TRAIN_DISPLAY, 0, new ModelResourceLocation(ModItems.TRAIN_DISPLAY.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ModItems.TRAIN_WARNING_DEVICE, 0, new ModelResourceLocation(ModItems.TRAIN_WARNING_DEVICE.getRegistryName(), "inventory"));
    }
}

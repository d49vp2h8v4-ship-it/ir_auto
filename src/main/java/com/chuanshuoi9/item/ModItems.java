package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID)
public class ModItems {
    public static final ItemRailwayMap RAILWAY_MAP = new ItemRailwayMap();
    public static final ItemTrainControlPaper TRAIN_CONTROL_PAPER = new ItemTrainControlPaper();
    public static final ItemTrainManager TRAIN_MANAGER = new ItemTrainManager();
    public static final ItemTrainSignal TRAIN_SIGNAL = new ItemTrainSignal();
    public static final ItemTurnoutMachine TURNOUT_MACHINE = new ItemTurnoutMachine();
    public static final ItemStationMarker STATION_MARKER = new ItemStationMarker();
    public static final ItemSignalLight SIGNAL_LIGHT = new ItemSignalLight();
    public static final ItemTrainDisplay TRAIN_DISPLAY = new ItemTrainDisplay();
    public static final ItemTrainWarningDevice TRAIN_WARNING_DEVICE = new ItemTrainWarningDevice();

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(RAILWAY_MAP);
        event.getRegistry().register(TRAIN_CONTROL_PAPER);
        event.getRegistry().register(TRAIN_MANAGER);
        event.getRegistry().register(TRAIN_SIGNAL);
        event.getRegistry().register(TURNOUT_MACHINE);
        event.getRegistry().register(STATION_MARKER);
        event.getRegistry().register(SIGNAL_LIGHT);
        event.getRegistry().register(TRAIN_DISPLAY);
        event.getRegistry().register(TRAIN_WARNING_DEVICE);
    }
}

package com.chuanshuoi9.block;

import com.chuanshuoi9.IrAutoMod;
import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID)
public class ModBlocks {
    public static final BlockTrainSignal TRAIN_SIGNAL = new BlockTrainSignal();
    public static final BlockTurnoutMachine TURNOUT_MACHINE = new BlockTurnoutMachine();
    public static final BlockStationMarker STATION_MARKER = new BlockStationMarker();
    public static final BlockSignalLight SIGNAL_LIGHT = new BlockSignalLight();
    public static final BlockTrainDisplay TRAIN_DISPLAY = new BlockTrainDisplay();

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(TRAIN_SIGNAL);
        event.getRegistry().register(TURNOUT_MACHINE);
        event.getRegistry().register(STATION_MARKER);
        event.getRegistry().register(SIGNAL_LIGHT);
        event.getRegistry().register(TRAIN_DISPLAY);
    }
}

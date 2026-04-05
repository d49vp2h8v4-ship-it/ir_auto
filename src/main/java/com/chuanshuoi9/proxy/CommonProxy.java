package com.chuanshuoi9.proxy;

import com.chuanshuoi9.network.StationSyncMessage;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.util.List;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }

    public void handleRailMapSync(int dimension, long[] railPositions, long[] signalPositions) {
    }

    public void handleStationSync(List<StationSyncMessage.StationData> stations) {
    }

    public void handleTrainManagerListSync(NBTTagCompound payload) {
    }

    public void handleTrainManagerDetailSync(NBTTagCompound payload) {
    }

    public void handleTrainPositionsSync(int dimension, long[] packedXZ) {
    }

    public void openTrainControlGui(String trainUuid, NBTTagCompound payload) {
    }

    public void openTrainTimetableGui(String trainUuid, NBTTagCompound payload) {
    }

    public void openTrainDisplayGui(BlockPos pos) {
    }

    public void openStationMarkerNameGui(BlockPos pos) {
    }

    public void openTurnoutMachineConfigGui(EnumHand hand, ItemStack stack) {
    }

    public void openTurnoutMachineGui(BlockPos pos) {
    }

    public void openTrainSignalStatusGui(BlockPos pos) {
    }

    public void handleSignalStatusSync(BlockPos pos, NBTTagCompound payload) {
    }

    public void handleTurnoutStatusSync(BlockPos pos, NBTTagCompound payload) {
    }
}

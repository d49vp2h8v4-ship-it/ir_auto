package com.chuanshuoi9.irfix;

import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

public class IrAutoChunkLoadingCallback implements ForgeChunkManager.LoadingCallback {
    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
    }
}

package com.chuanshuoi9.signal;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.BlockSignal3;
import com.chuanshuoi9.tile.TileSignal3;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 统一 tick 三状态信号机，不依赖 Block.updateTick。
 */
@Mod.EventBusSubscriber(modid = IrAutoMod.MODID)
public class Signal3TickHandler {

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        World world = event.world;
        if (world.isRemote) return;
        if (world.getTotalWorldTime() % 5 != 0) return; // every 5 ticks

        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileSignal3)) continue;
            TileSignal3 sig = (TileSignal3) te;
            BlockPos pos = sig.getPos();

            BlockPos a = sig.getRailA(), b = sig.getRailB();
            int newAspect;

            if (a == null || b == null) {
                newAspect = 0;
            } else {
                TrainSignalController.SignalSegmentReport rep =
                    TrainSignalController.getSignalSegmentReport(world, a, b);
                boolean occupied = rep != null && rep.occupied;

                if (occupied) {
                    newAspect = 0;
                } else {
                    TileSignal3 prev = sig.getPrev();
                    if (prev != null && prev.getAspect() == 0) {
                        newAspect = 1;
                    } else {
                        newAspect = 2;
                    }
                }
            }

            if (newAspect != sig.getAspect()) {
                sig.setAspect(newAspect);
                world.setBlockState(pos, world.getBlockState(pos)
                    .withProperty(BlockSignal3.RED, newAspect == 0)
                    .withProperty(BlockSignal3.YELLOW, newAspect == 1)
                    .withProperty(BlockSignal3.GREEN, newAspect == 2), 2);
            }
        }
    }
}

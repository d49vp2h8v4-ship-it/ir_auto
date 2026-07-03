package com.chuanshuoi9.util;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class IrRailUtil {
    public static boolean isIrRail(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te != null) {
            String className = te.getClass().getName();
            if (className.startsWith("cam72cam.immersiverailroading")
                && (className.contains(".tile.TileRail") || className.contains(".tile.track.TileRail"))) {
                return true;
            }
        }
        Block block = world.getBlockState(pos).getBlock();
        ResourceLocation name = block.getRegistryName();
        if (name == null) {
            return false;
        }
        return "immersiverailroading".equals(name.getResourceDomain()) && name.getResourcePath().contains("rail");
    }
}


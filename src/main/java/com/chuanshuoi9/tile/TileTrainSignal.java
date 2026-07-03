package com.chuanshuoi9.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public class TileTrainSignal extends TileEntity {
    private BlockPos railA;
    private BlockPos railB;
    private EnumFacing facing = EnumFacing.NORTH;

    public BlockPos getRailA() {
        return railA;
    }

    public void setRailA(BlockPos railA) {
        this.railA = railA;
        markDirty();
    }

    public BlockPos getRailB() {
        return railB;
    }

    public void setRailB(BlockPos railB) {
        this.railB = railB;
        markDirty();
    }

    public EnumFacing getFacing() {
        return facing;
    }

    public void setFacing(EnumFacing facing) {
        if (facing != null && facing.getAxis().isHorizontal()) {
            this.facing = facing;
            markDirty();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (railA != null) {
            compound.setLong("railA", railA.toLong());
        }
        if (railB != null) {
            compound.setLong("railB", railB.toLong());
        }
        compound.setByte("facing", (byte) facing.getHorizontalIndex());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("railA")) {
            railA = BlockPos.fromLong(compound.getLong("railA"));
        } else {
            railA = null;
        }
        if (compound.hasKey("railB")) {
            railB = BlockPos.fromLong(compound.getLong("railB"));
        } else {
            railB = null;
        }
        try {
            facing = compound.hasKey("facing") ? EnumFacing.getHorizontal(compound.getByte("facing") & 3) : EnumFacing.NORTH;
        } catch (Throwable ignored) {
            facing = EnumFacing.NORTH;
        }
        if (facing == null || !facing.getAxis().isHorizontal()) {
            facing = EnumFacing.NORTH;
        }
    }
}

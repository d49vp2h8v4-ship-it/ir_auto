package com.chuanshuoi9.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class TileSignal3 extends TileEntity {
    /** Section start (entry point, closer to train origin) */
    private BlockPos railA;
    /** Section end (exit point, downstream) */
    private BlockPos railB;
    /** The signal BEFORE this one in travel direction (upstream, closer to A) */
    public BlockPos prevSignal;
    /** RED=0, YELLOW=1, GREEN=2 */
    private int aspect;

    public BlockPos getRailA() { return railA; }
    public void setRailA(BlockPos p) { railA = p; markDirty(); }
    public BlockPos getRailB() { return railB; }
    public void setRailB(BlockPos p) { railB = p; markDirty(); }
    public int getAspect() { return aspect; }
    public void setAspect(int a) { aspect = a; markDirty(); }

    public TileSignal3 getPrev() {
        if (prevSignal == null || world == null) return null;
        TileEntity te = world.getTileEntity(prevSignal);
        return te instanceof TileSignal3 ? (TileSignal3) te : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (railA != null) nbt.setLong("ra", railA.toLong());
        if (railB != null) nbt.setLong("rb", railB.toLong());
        if (prevSignal != null) nbt.setLong("prev", prevSignal.toLong());
        nbt.setInteger("asp", aspect);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        railA = nbt.hasKey("ra") ? BlockPos.fromLong(nbt.getLong("ra")) : null;
        railB = nbt.hasKey("rb") ? BlockPos.fromLong(nbt.getLong("rb")) : null;
        prevSignal = nbt.hasKey("prev") ? BlockPos.fromLong(nbt.getLong("prev")) : null;
        aspect = nbt.getInteger("asp");
    }
}

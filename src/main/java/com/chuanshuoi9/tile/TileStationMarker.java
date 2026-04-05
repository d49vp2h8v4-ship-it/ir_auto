package com.chuanshuoi9.tile;

import com.chuanshuoi9.map.MapSyncTickHandler;
import com.chuanshuoi9.map.StationMarkerSavedData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TileStationMarker extends TileEntity {
    private BlockPos railPos;
    private String stationName = "站台";

    public BlockPos getRailPos() {
        return railPos;
    }

    public String getStationName() {
        return stationName == null ? "" : stationName;
    }

    public void configure(BlockPos railPos, String stationName) {
        this.railPos = railPos;
        if (stationName != null && !stationName.trim().isEmpty()) {
            this.stationName = stationName.trim();
        }
        persistToSavedData();
        markAndSync();
    }

    public void setStationName(String name) {
        if (name == null) {
            return;
        }
        String v = name.trim();
        if (v.length() > 32) {
            v = v.substring(0, 32);
        }
        if (v.isEmpty()) {
            return;
        }
        if (v.equals(getStationName())) {
            return;
        }
        this.stationName = v;
        persistToSavedData();
        markAndSync();
    }

    public void onRemoved() {
        if (world == null || world.isRemote) {
            return;
        }
        StationMarkerSavedData data = StationMarkerSavedData.get(world);
        if (railPos != null) {
            data.removeStation(world.provider.getDimension(), railPos);
            MapSyncTickHandler.syncStationsToAll();
        }
    }

    private void persistToSavedData() {
        if (world == null || world.isRemote) {
            return;
        }
        if (railPos == null) {
            return;
        }
        StationMarkerSavedData data = StationMarkerSavedData.get(world);
        data.putStation(world.provider.getDimension(), railPos, getStationName());
        MapSyncTickHandler.syncStationsToAll();
    }

    private void markAndSync() {
        markDirty();
        if (world != null) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (railPos != null) {
            compound.setLong("railPos", railPos.toLong());
        }
        compound.setString("stationName", getStationName());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("railPos")) {
            railPos = BlockPos.fromLong(compound.getLong("railPos"));
        } else {
            railPos = null;
        }
        stationName = compound.getString("stationName");
        if (stationName == null || stationName.trim().isEmpty()) {
            stationName = "站台";
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt != null && pkt.getNbtCompound() != null) {
            readFromNBT(pkt.getNbtCompound());
        }
    }
}


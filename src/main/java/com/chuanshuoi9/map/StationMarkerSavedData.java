package com.chuanshuoi9.map;

import com.chuanshuoi9.network.StationSyncMessage;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StationMarkerSavedData extends WorldSavedData {
    public static final String DATA_NAME = "ir_station_marker_data";

    private final Map<Integer, Map<Long, String>> stationsByDim = new HashMap<>();

    public StationMarkerSavedData() {
        super(DATA_NAME);
    }

    public StationMarkerSavedData(String name) {
        super(name);
    }

    public static StationMarkerSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        StationMarkerSavedData data = (StationMarkerSavedData) storage.getOrLoadData(StationMarkerSavedData.class, DATA_NAME);
        if (data == null) {
            data = new StationMarkerSavedData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void putStation(int dim, BlockPos railPos, String name) {
        if (railPos == null) {
            return;
        }
        String v = name == null ? "" : name.trim();
        if (v.length() > 32) {
            v = v.substring(0, 32);
        }
        if (v.isEmpty()) {
            return;
        }
        Map<Long, String> map = stationsByDim.computeIfAbsent(dim, k -> new HashMap<>());
        String prev = map.put(railPos.toLong(), v);
        if (prev == null || !prev.equals(v)) {
            markDirty();
        }
    }

    public void removeStation(int dim, BlockPos railPos) {
        if (railPos == null) {
            return;
        }
        Map<Long, String> map = stationsByDim.get(dim);
        if (map == null) {
            return;
        }
        if (map.remove(railPos.toLong()) != null) {
            markDirty();
        }
    }

    public List<StationSyncMessage.StationData> getStations(int dim) {
        Map<Long, String> map = stationsByDim.get(dim);
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }
        List<StationSyncMessage.StationData> out = new ArrayList<>(map.size());
        for (Map.Entry<Long, String> e : map.entrySet()) {
            BlockPos pos = BlockPos.fromLong(e.getKey());
            String name = e.getValue();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            out.add(new StationSyncMessage.StationData(pos.getX(), pos.getY(), pos.getZ(), name));
        }
        return out;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        stationsByDim.clear();
        NBTTagList dims = nbt.getTagList("dims", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dims.tagCount(); i++) {
            NBTTagCompound dimTag = dims.getCompoundTagAt(i);
            int dim = dimTag.getInteger("dim");
            Map<Long, String> map = new HashMap<>();
            NBTTagList list = dimTag.getTagList("stations", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < list.tagCount(); j++) {
                NBTTagCompound s = list.getCompoundTagAt(j);
                long p = s.getLong("pos");
                String name = s.getString("name");
                if (name != null && !name.trim().isEmpty()) {
                    map.put(p, name);
                }
            }
            if (!map.isEmpty()) {
                stationsByDim.put(dim, map);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList dims = new NBTTagList();
        for (Map.Entry<Integer, Map<Long, String>> entry : stationsByDim.entrySet()) {
            NBTTagCompound dimTag = new NBTTagCompound();
            dimTag.setInteger("dim", entry.getKey());
            NBTTagList list = new NBTTagList();
            for (Map.Entry<Long, String> s : entry.getValue().entrySet()) {
                if (s.getValue() == null || s.getValue().trim().isEmpty()) {
                    continue;
                }
                NBTTagCompound st = new NBTTagCompound();
                st.setLong("pos", s.getKey());
                st.setString("name", s.getValue());
                list.appendTag(st);
            }
            dimTag.setTag("stations", list);
            dims.appendTag(dimTag);
        }
        compound.setTag("dims", dims);
        return compound;
    }
}


package com.chuanshuoi9.virtual;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VirtualTrainSavedData extends WorldSavedData {
    public static final String DATA_NAME = "ir_auto_virtual_trains";

    private final Map<UUID, VirtualTrainState> trains = new HashMap<>();
    private transient int cacheVersion = 0;
    private transient int cacheBuiltForVersion = -1;
    private transient Map<Integer, List<VirtualTrainState>> byDimCache = new HashMap<>();

    public VirtualTrainSavedData() {
        super(DATA_NAME);
    }

    public VirtualTrainSavedData(String name) {
        super(name);
    }

    public static VirtualTrainSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        VirtualTrainSavedData data = (VirtualTrainSavedData) storage.getOrLoadData(VirtualTrainSavedData.class, DATA_NAME);
        if (data == null) {
            data = new VirtualTrainSavedData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public Map<UUID, VirtualTrainState> all() {
        return trains;
    }

    public List<VirtualTrainState> byDimension(int dim) {
        if (cacheBuiltForVersion != cacheVersion) {
            rebuildByDimCache();
        }
        List<VirtualTrainState> list = byDimCache.get(dim);
        return list == null ? Collections.emptyList() : list;
    }

    public void put(VirtualTrainState state) {
        if (state == null || state.id == null) {
            return;
        }
        trains.put(state.id, state);
        invalidateCache();
        markDirty();
    }

    public void remove(UUID id) {
        if (id == null) {
            return;
        }
        if (trains.remove(id) != null) {
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        trains.clear();
        NBTTagList list = nbt.getTagList("trains", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            VirtualTrainState st = VirtualTrainState.fromTag(t);
            if (st != null && st.id != null) {
                trains.put(st.id, st);
            }
        }
        invalidateCache();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (VirtualTrainState st : trains.values()) {
            if (st == null || st.id == null) continue;
            list.appendTag(st.toTag());
        }
        compound.setTag("trains", list);
        return compound;
    }

    private void invalidateCache() {
        cacheVersion++;
    }

    private void rebuildByDimCache() {
        Map<Integer, List<VirtualTrainState>> map = new HashMap<>();
        for (VirtualTrainState st : trains.values()) {
            if (st == null) {
                continue;
            }
            map.computeIfAbsent(st.dimension, __ -> new ArrayList<>()).add(st);
        }
        byDimCache = map;
        cacheBuiltForVersion = cacheVersion;
    }

    public static class CarSnapshot {
        public NBTTagCompound entityTag;
        public double relX;
        public double relY;
        public double relZ;
    }

    public static class VirtualTrainState {
        public UUID id;
        public UUID controlTrainId;
        public NBTTagCompound controlRoot;
        public int dimension;
        public double x;
        public double y;
        public double z;
        public double dirX;
        public double dirZ;
        public double speedKmh;
        public long currentKey;
        public long nextKey;
        public long prevKey;
        public double edgeProgress;
        public double edgeLength;
        public long lastActiveTick;
        public List<CarSnapshot> cars = new ArrayList<>();
        public List<Long> recentKeys = new ArrayList<>();
        public long lastSimTick;

        public Vec3d pos() {
            return new Vec3d(x, y, z);
        }

        public NBTTagCompound toTag() {
            NBTTagCompound t = new NBTTagCompound();
            t.setUniqueId("id", id);
            if (controlTrainId != null) {
                t.setUniqueId("ct", controlTrainId);
            }
            if (controlRoot != null) {
                t.setTag("ctrl", controlRoot);
            }
            t.setInteger("dim", dimension);
            t.setDouble("x", x);
            t.setDouble("y", y);
            t.setDouble("z", z);
            t.setDouble("dx", dirX);
            t.setDouble("dz", dirZ);
            t.setDouble("v", speedKmh);
            t.setLong("ck", currentKey);
            t.setLong("nk", nextKey);
            t.setLong("pk", prevKey);
            t.setDouble("p", edgeProgress);
            t.setDouble("l", edgeLength);
            t.setLong("last", lastActiveTick);
            t.setLong("sim", lastSimTick);
            NBTTagList carList = new NBTTagList();
            for (CarSnapshot c : cars) {
                if (c == null || c.entityTag == null) continue;
                NBTTagCompound ct = new NBTTagCompound();
                ct.setTag("e", c.entityTag);
                ct.setDouble("rx", c.relX);
                ct.setDouble("ry", c.relY);
                ct.setDouble("rz", c.relZ);
                carList.appendTag(ct);
            }
            t.setTag("cars", carList);
            return t;
        }

        public static VirtualTrainState fromTag(NBTTagCompound t) {
            if (t == null) {
                return null;
            }
            VirtualTrainState st = new VirtualTrainState();
            try {
                st.id = t.getUniqueId("id");
            } catch (Throwable ignored) {
                st.id = null;
            }
            if (st.id == null) {
                return null;
            }
            try {
                if (t.hasUniqueId("ct")) {
                    st.controlTrainId = t.getUniqueId("ct");
                }
            } catch (Throwable ignored) {
                st.controlTrainId = null;
            }
            if (t.hasKey("ctrl", Constants.NBT.TAG_COMPOUND)) {
                st.controlRoot = t.getCompoundTag("ctrl");
            }
            st.dimension = t.getInteger("dim");
            st.x = t.getDouble("x");
            st.y = t.getDouble("y");
            st.z = t.getDouble("z");
            st.dirX = t.getDouble("dx");
            st.dirZ = t.getDouble("dz");
            st.speedKmh = t.getDouble("v");
            st.currentKey = t.getLong("ck");
            st.nextKey = t.getLong("nk");
            st.prevKey = t.getLong("pk");
            st.edgeProgress = t.getDouble("p");
            st.edgeLength = t.getDouble("l");
            st.lastActiveTick = t.getLong("last");
            st.lastSimTick = t.hasKey("sim", Constants.NBT.TAG_LONG) ? t.getLong("sim") : st.lastActiveTick;
            NBTTagList carList = t.getTagList("cars", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < carList.tagCount(); i++) {
                NBTTagCompound ct = carList.getCompoundTagAt(i);
                if (!ct.hasKey("e", Constants.NBT.TAG_COMPOUND)) continue;
                CarSnapshot c = new CarSnapshot();
                c.entityTag = ct.getCompoundTag("e");
                c.relX = ct.getDouble("rx");
                c.relY = ct.getDouble("ry");
                c.relZ = ct.getDouble("rz");
                st.cars.add(c);
            }
            return st;
        }
    }
}

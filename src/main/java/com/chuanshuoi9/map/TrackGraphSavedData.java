package com.chuanshuoi9.map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TrackGraphSavedData extends WorldSavedData {
    public static final String DATA_NAME = "ir_auto_track_graph";

    private final Map<Integer, Map<Long, Map<Long, Double>>> edgesByDimension = new HashMap<>();

    public TrackGraphSavedData() {
        super(DATA_NAME);
    }

    public TrackGraphSavedData(String name) {
        super(name);
    }

    public static TrackGraphSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        TrackGraphSavedData data = (TrackGraphSavedData) storage.getOrLoadData(TrackGraphSavedData.class, DATA_NAME);
        if (data == null) {
            data = new TrackGraphSavedData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void putEdge(int dimension, long fromKey, long toKey, double length) {
        if (fromKey == toKey) {
            return;
        }
        if (!Double.isFinite(length) || length <= 0.0) {
            return;
        }
        Map<Long, Map<Long, Double>> dim = edgesByDimension.computeIfAbsent(dimension, k -> new HashMap<>());
        Map<Long, Double> out = dim.computeIfAbsent(fromKey, k -> new HashMap<>());
        Double prev = out.get(toKey);
        if (prev == null || Math.abs(prev - length) > 1e-6) {
            out.put(toKey, length);
            markDirty();
        }
    }

    public Map<Long, Double> getOutgoing(int dimension, long fromKey) {
        Map<Long, Map<Long, Double>> dim = edgesByDimension.get(dimension);
        if (dim == null) {
            return Collections.emptyMap();
        }
        Map<Long, Double> out = dim.get(fromKey);
        if (out == null) {
            return Collections.emptyMap();
        }
        return out;
    }

    public Set<Long> getNodes(int dimension) {
        Map<Long, Map<Long, Double>> dim = edgesByDimension.get(dimension);
        if (dim == null || dim.isEmpty()) {
            return Collections.emptySet();
        }
        return dim.keySet();
    }

    public static BlockPos toPos(long railKey) {
        return BlockPos.fromLong(railKey);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        edgesByDimension.clear();
        NBTTagList dims = nbt.getTagList("dims", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dims.tagCount(); i++) {
            NBTTagCompound dimTag = dims.getCompoundTagAt(i);
            int dim = dimTag.getInteger("id");
            Map<Long, Map<Long, Double>> edges = new HashMap<>();
            NBTTagList nodes = dimTag.getTagList("nodes", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < nodes.tagCount(); j++) {
                NBTTagCompound nodeTag = nodes.getCompoundTagAt(j);
                long from = nodeTag.getLong("from");
                Map<Long, Double> out = new HashMap<>();
                NBTTagList outs = nodeTag.getTagList("out", Constants.NBT.TAG_COMPOUND);
                for (int k = 0; k < outs.tagCount(); k++) {
                    NBTTagCompound e = outs.getCompoundTagAt(k);
                    long to = e.getLong("to");
                    double len = e.getDouble("len");
                    if (from != to && Double.isFinite(len) && len > 0.0) {
                        out.put(to, len);
                    }
                }
                if (!out.isEmpty()) {
                    edges.put(from, out);
                }
            }
            if (!edges.isEmpty()) {
                edgesByDimension.put(dim, edges);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList dims = new NBTTagList();
        for (Map.Entry<Integer, Map<Long, Map<Long, Double>>> dimEntry : edgesByDimension.entrySet()) {
            if (dimEntry == null) continue;
            Integer dim = dimEntry.getKey();
            Map<Long, Map<Long, Double>> edges = dimEntry.getValue();
            if (dim == null || edges == null || edges.isEmpty()) continue;
            NBTTagCompound dimTag = new NBTTagCompound();
            dimTag.setInteger("id", dim);
            NBTTagList nodes = new NBTTagList();
            for (Map.Entry<Long, Map<Long, Double>> node : edges.entrySet()) {
                if (node == null) continue;
                Long from = node.getKey();
                Map<Long, Double> outs = node.getValue();
                if (from == null || outs == null || outs.isEmpty()) continue;
                NBTTagCompound nodeTag = new NBTTagCompound();
                nodeTag.setLong("from", from);
                NBTTagList outList = new NBTTagList();
                for (Map.Entry<Long, Double> e : outs.entrySet()) {
                    if (e == null) continue;
                    Long to = e.getKey();
                    Double len = e.getValue();
                    if (to == null || len == null) continue;
                    if (from.equals(to) || !Double.isFinite(len) || len <= 0.0) continue;
                    NBTTagCompound edge = new NBTTagCompound();
                    edge.setLong("to", to);
                    edge.setDouble("len", len);
                    outList.appendTag(edge);
                }
                if (outList.tagCount() > 0) {
                    nodeTag.setTag("out", outList);
                    nodes.appendTag(nodeTag);
                }
            }
            if (nodes.tagCount() > 0) {
                dimTag.setTag("nodes", nodes);
                dims.appendTag(dimTag);
            }
        }
        compound.setTag("dims", dims);
        return compound;
    }
}


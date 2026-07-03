package com.chuanshuoi9.map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;
import java.util.stream.Collectors;

public class RailMapSavedData extends WorldSavedData {
    public static final String DATA_NAME = "ir_map_data";
    private final Map<Integer, Set<Long>> railsByDimension = new HashMap<>();
    private final Map<Integer, Map<Long, Set<Long>>> signalsByDimension = new HashMap<>();

    public RailMapSavedData() {
        super(DATA_NAME);
    }

    public RailMapSavedData(String name) {
        super(name);
    }

    public static RailMapSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        RailMapSavedData data = (RailMapSavedData) storage.getOrLoadData(RailMapSavedData.class, DATA_NAME);
        if (data == null) {
            data = new RailMapSavedData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    // ── Rails ──────────────────────────────────────────────

    public void addRail(int dimension, BlockPos pos) {
        Set<Long> set = railsByDimension.computeIfAbsent(dimension, key -> new HashSet<>());
        if (set.add(pos.toLong())) markDirty();
    }

    public Set<BlockPos> getRails(int dimension) {
        Set<Long> set = railsByDimension.get(dimension);
        if (set == null || set.isEmpty()) return Collections.emptySet();
        return set.stream().map(BlockPos::fromLong).collect(Collectors.toSet());
    }

    public Set<Long> getRailLongs(int dimension) {
        Set<Long> set = railsByDimension.get(dimension);
        return set == null ? Collections.emptySet() : set;
    }

    // ── Signals ────────────────────────────────────────────

    public Set<Long> getSignalRailLongs(int dimension) {
        Map<Long, Set<Long>> signals = signalsByDimension.get(dimension);
        if (signals == null || signals.isEmpty()) return Collections.emptySet();
        Set<Long> all = new HashSet<>();
        for (Set<Long> r : signals.values()) all.addAll(r);
        return all;
    }

    public Map<Long, Set<Long>> getSignalSegments(int dimension) {
        Map<Long, Set<Long>> s = signalsByDimension.get(dimension);
        return s == null ? Collections.emptyMap() : s;
    }

    // ── Reconciliation ─────────────────────────────────────

    public int reconcileRailsInArea(int dimension, Collection<BlockPos> scanned,
                                    Map<Long, Set<Long>> scannedSignals, Set<Long> seenSignals,
                                    Set<Long> scannedChunks,
                                    int minX, int maxX, int minZ, int maxZ) {
        Set<Long> set = railsByDimension.computeIfAbsent(dimension, k -> new HashSet<>());
        Map<Long, Set<Long>> signals = signalsByDimension.computeIfAbsent(dimension, k -> new HashMap<>());

        Set<Long> scannedLongs = new HashSet<>(Math.max(16, scanned.size() * 2));
        int added = 0;
        for (BlockPos pos : scanned) {
            long packed = pos.toLong();
            scannedLongs.add(packed);
            if (set.add(packed)) added++;
        }
        for (Map.Entry<Long, Set<Long>> e : scannedSignals.entrySet()) {
            Set<Long> np = e.getValue();
            if (np == null || np.isEmpty()) continue;
            Set<Long> ex = signals.get(e.getKey());
            if (ex == null || !ex.equals(np)) {
                signals.put(e.getKey(), new HashSet<>(np));
                added++;
            }
        }

        int removed = 0;
        Iterator<Long> it = set.iterator();
        while (it.hasNext()) {
            long packed = it.next();
            BlockPos pos = BlockPos.fromLong(packed);
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            long ck = (((long) cx) << 32) ^ (cz & 0xffffffffL);
            if (scannedChunks.contains(ck) && !scannedLongs.contains(packed)) {
                it.remove();
                removed++;
            }
        }
        Iterator<Map.Entry<Long, Set<Long>>> si = signals.entrySet().iterator();
        while (si.hasNext()) {
            long packed = si.next().getKey();
            BlockPos pos = BlockPos.fromLong(packed);
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            long ck = (((long) cx) << 32) ^ (cz & 0xffffffffL);
            if (scannedChunks.contains(ck) && !seenSignals.contains(packed)) {
                si.remove();
                removed++;
            }
        }

        if (added > 0 || removed > 0) markDirty();
        return added + removed;
    }

    // ── NBT persistence ────────────────────────────────────

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        railsByDimension.clear();
        signalsByDimension.clear();
        NBTTagList dimensions = nbt.getTagList("dimensions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dimensions.tagCount(); i++) {
            NBTTagCompound entry = dimensions.getCompoundTagAt(i);
            int dim = entry.getInteger("dim");

            // Rails
            Set<Long> set = new HashSet<>();
            NBTTagList posList = entry.getTagList("positions", Constants.NBT.TAG_LONG);
            for (int j = 0; j < posList.tagCount(); j++)
                set.add(((NBTTagLong) posList.get(j)).getLong());
            railsByDimension.put(dim, set);

            // Signals
            if (entry.hasKey("signals")) {
                Map<Long, Set<Long>> signals = new HashMap<>();
                NBTTagList sigList = entry.getTagList("signals", Constants.NBT.TAG_COMPOUND);
                for (int j = 0; j < sigList.tagCount(); j++) {
                    NBTTagCompound se = sigList.getCompoundTagAt(j);
                    Set<Long> sr = new HashSet<>();
                    NBTTagList rl = se.getTagList("rails", Constants.NBT.TAG_LONG);
                    for (int k = 0; k < rl.tagCount(); k++)
                        sr.add(((NBTTagLong) rl.get(k)).getLong());
                    signals.put(se.getLong("pos"), sr);
                }
                signalsByDimension.put(dim, signals);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList dims = new NBTTagList();
        Set<Integer> allDims = new HashSet<>(railsByDimension.keySet());
        allDims.addAll(signalsByDimension.keySet());

        for (int dimId : allDims) {
            NBTTagCompound dim = new NBTTagCompound();
            dim.setInteger("dim", dimId);

            // Rails
            Set<Long> rails = railsByDimension.getOrDefault(dimId, Collections.emptySet());
            NBTTagList posList = new NBTTagList();
            for (long v : rails) posList.appendTag(new NBTTagLong(v));
            dim.setTag("positions", posList);

            // Signals
            Map<Long, Set<Long>> signals = signalsByDimension.getOrDefault(dimId, Collections.emptyMap());
            if (!signals.isEmpty()) {
                NBTTagList sigList = new NBTTagList();
                for (Map.Entry<Long, Set<Long>> e : signals.entrySet()) {
                    NBTTagCompound se = new NBTTagCompound();
                    se.setLong("pos", e.getKey());
                    NBTTagList rl = new NBTTagList();
                    for (long r : e.getValue()) rl.appendTag(new NBTTagLong(r));
                    se.setTag("rails", rl);
                    sigList.appendTag(se);
                }
                dim.setTag("signals", sigList);
            }

            dims.appendTag(dim);
        }
        compound.setTag("dimensions", dims);
        return compound;
    }
}

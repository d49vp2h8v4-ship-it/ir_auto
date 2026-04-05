package com.chuanshuoi9.map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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

    public void addRail(int dimension, BlockPos pos) {
        Set<Long> set = railsByDimension.computeIfAbsent(dimension, key -> new HashSet<>());
        if (set.add(pos.toLong())) {
            markDirty();
        }
    }

    public void addRails(int dimension, Collection<BlockPos> positions) {
        Set<Long> set = railsByDimension.computeIfAbsent(dimension, key -> new HashSet<>());
        boolean changed = false;
        for (BlockPos pos : positions) {
            if (set.add(pos.toLong())) {
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    public int reconcileRailsInArea(int dimension, Collection<BlockPos> scanned, Map<Long, Set<Long>> scannedSignals, Set<Long> seenSignals, Set<Long> scannedChunks, int minX, int maxX, int minZ, int maxZ) {
        Set<Long> set = railsByDimension.computeIfAbsent(dimension, key -> new HashSet<>());
        Map<Long, Set<Long>> signals = signalsByDimension.computeIfAbsent(dimension, key -> new HashMap<>());
        
        Set<Long> scannedLongs = new HashSet<>(Math.max(16, scanned.size() * 2));
        int added = 0;
        for (BlockPos pos : scanned) {
            long packed = pos.toLong();
            scannedLongs.add(packed);
            if (set.add(packed)) {
                added++;
            }
        }
        
        for (Map.Entry<Long, Set<Long>> entry : scannedSignals.entrySet()) {
            Set<Long> newPath = entry.getValue();
            if (newPath == null || newPath.isEmpty()) {
                continue;
            }
            Set<Long> existing = signals.get(entry.getKey());
            if (existing == null || !existing.equals(newPath)) {
                signals.put(entry.getKey(), new HashSet<>(newPath));
                added++;
            }
        }

        int removed = 0;
        Iterator<Long> it = set.iterator();
        while (it.hasNext()) {
            long packed = it.next();
            BlockPos pos = BlockPos.fromLong(packed);
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            long chunkKey = (((long) cx) << 32) ^ (cz & 0xffffffffL);
            if (scannedChunks.contains(chunkKey) && !scannedLongs.contains(packed)) {
                it.remove();
                removed++;
            }
        }
        
        Iterator<Map.Entry<Long, Set<Long>>> sigIt = signals.entrySet().iterator();
        while (sigIt.hasNext()) {
            long packed = sigIt.next().getKey();
            BlockPos pos = BlockPos.fromLong(packed);
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            long chunkKey = (((long) cx) << 32) ^ (cz & 0xffffffffL);
            if (scannedChunks.contains(chunkKey) && !seenSignals.contains(packed)) {
                sigIt.remove();
                removed++;
            }
        }

        if (added > 0 || removed > 0) {
            markDirty();
        }
        return added + removed;
    }

    public Set<BlockPos> getRails(int dimension) {
        Set<Long> set = railsByDimension.get(dimension);
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        }
        return set.stream().map(BlockPos::fromLong).collect(Collectors.toSet());
    }

    public Set<Long> getRailLongs(int dimension) {
        Set<Long> set = railsByDimension.get(dimension);
        if (set == null) {
            return Collections.emptySet();
        }
        return set;
    }

    public Set<Long> getSignalRailLongs(int dimension) {
        Map<Long, Set<Long>> signals = signalsByDimension.get(dimension);
        if (signals == null || signals.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> allSignalRails = new HashSet<>();
        for (Set<Long> rails : signals.values()) {
            allSignalRails.addAll(rails);
        }
        return allSignalRails;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        railsByDimension.clear();
        signalsByDimension.clear();
        NBTTagList dimensions = nbt.getTagList("dimensions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dimensions.tagCount(); i++) {
            NBTTagCompound entry = dimensions.getCompoundTagAt(i);
            int dimension = entry.getInteger("dim");
            Set<Long> set = new HashSet<>();
            NBTTagList positions = entry.getTagList("positions", Constants.NBT.TAG_LONG);
            for (int j = 0; j < positions.tagCount(); j++) {
                set.add(((NBTTagLong) positions.get(j)).getLong());
            }
            railsByDimension.put(dimension, set);
            
            if (entry.hasKey("signals")) {
                Map<Long, Set<Long>> signals = new HashMap<>();
                NBTTagList signalsList = entry.getTagList("signals", Constants.NBT.TAG_COMPOUND);
                for (int j = 0; j < signalsList.tagCount(); j++) {
                    NBTTagCompound sigEntry = signalsList.getCompoundTagAt(j);
                    long sigPos = sigEntry.getLong("pos");
                    Set<Long> sigRails = new HashSet<>();
                    NBTTagList sigRailsList = sigEntry.getTagList("rails", Constants.NBT.TAG_LONG);
                    for (int k = 0; k < sigRailsList.tagCount(); k++) {
                        sigRails.add(((NBTTagLong) sigRailsList.get(k)).getLong());
                    }
                    signals.put(sigPos, sigRails);
                }
                signalsByDimension.put(dimension, signals);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList dimensions = new NBTTagList();
        Set<Integer> allDims = new HashSet<>(railsByDimension.keySet());
        allDims.addAll(signalsByDimension.keySet());
        
        for (int dimId : allDims) {
            NBTTagCompound dim = new NBTTagCompound();
            dim.setInteger("dim", dimId);
            
            Set<Long> rails = railsByDimension.getOrDefault(dimId, Collections.emptySet());
            NBTTagList positions = new NBTTagList();
            for (long value : rails) {
                positions.appendTag(new NBTTagLong(value));
            }
            dim.setTag("positions", positions);
            
            Map<Long, Set<Long>> signals = signalsByDimension.getOrDefault(dimId, Collections.emptyMap());
            if (!signals.isEmpty()) {
                NBTTagList signalsList = new NBTTagList();
                for (Map.Entry<Long, Set<Long>> entry : signals.entrySet()) {
                    NBTTagCompound sigEntry = new NBTTagCompound();
                    sigEntry.setLong("pos", entry.getKey());
                    NBTTagList sigRailsList = new NBTTagList();
                    for (long rail : entry.getValue()) {
                        sigRailsList.appendTag(new NBTTagLong(rail));
                    }
                    sigEntry.setTag("rails", sigRailsList);
                    signalsList.appendTag(sigEntry);
                }
                dim.setTag("signals", signalsList);
            }
            
            dimensions.appendTag(dim);
        }
        compound.setTag("dimensions", dimensions);
        return compound;
    }
}

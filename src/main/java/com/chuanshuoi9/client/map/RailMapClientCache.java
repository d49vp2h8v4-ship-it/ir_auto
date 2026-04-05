package com.chuanshuoi9.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.chuanshuoi9.network.StationSyncMessage.StationData;

public class RailMapClientCache {
    private static final Map<Integer, Set<BlockPos>> BY_DIMENSION = new HashMap<>();
    private static final Map<Integer, Set<BlockPos>> SIGNALS_BY_DIMENSION = new HashMap<>();
    private static List<StationData> STATIONS = new ArrayList<>();

    public static void update(int dimension, Set<BlockPos> positions, Set<BlockPos> signalPositions) {
        BY_DIMENSION.put(dimension, new HashSet<>(positions));
        SIGNALS_BY_DIMENSION.put(dimension, new HashSet<>(signalPositions));
    }

    public static void updateStations(List<StationData> stations) {
        STATIONS = new ArrayList<>(stations);
    }

    public static List<StationData> getStations() {
        return STATIONS;
    }

    public static Set<BlockPos> currentDimensionRails() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return Collections.emptySet();
        }
        return BY_DIMENSION.getOrDefault(world.provider.getDimension(), Collections.emptySet());
    }

    public static Set<BlockPos> currentDimensionSignalRails() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return Collections.emptySet();
        }
        return SIGNALS_BY_DIMENSION.getOrDefault(world.provider.getDimension(), Collections.emptySet());
    }
}

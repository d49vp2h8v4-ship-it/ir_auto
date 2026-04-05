package com.chuanshuoi9.map;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.network.RailMapSyncMessage;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import com.chuanshuoi9.tile.TileTrainSignal;
import com.chuanshuoi9.signal.TrainSignalController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RailMapCollector {
    public static boolean collectForDirtyChunks(WorldServer world, Set<Long> dirtyChunkKeys) {
        if (dirtyChunkKeys == null || dirtyChunkKeys.isEmpty()) {
            return false;
        }
        int dimension = world.provider.getDimension();
        ScanResult scan = scanLoadedRailsInChunks(world, dirtyChunkKeys);
        RailMapSavedData data = RailMapSavedData.get(world);
        int changedCount = data.reconcileRailsInArea(dimension, scan.positions, scan.signalPaths, scan.seenSignals, scan.scannedChunks, scan.minX, scan.maxX, scan.minZ, scan.maxZ);
        if (changedCount > 0) {
            Set<BlockPos> persisted = data.getRails(dimension);
            Set<Long> persistedSignals = data.getSignalRailLongs(dimension);
            IrAutoMod.NETWORK.sendToDimension(new RailMapSyncMessage(dimension, persisted, persistedSignals), dimension);
            IrAutoMod.getLogger().info("rail map scan updated: dim={}, changed={}, persistedRails={}, persistedSignals={}", dimension, changedCount, persisted.size(), persistedSignals.size());
            return true;
        }
        return false;
    }

    public static boolean collectForPlayersInDimension(WorldServer world, List<EntityPlayerMP> players) {
        if (players == null || players.isEmpty()) {
            return false;
        }
        int dimension = world.provider.getDimension();
        ScanResult scan = scanLoadedRailsAroundPlayers(world, players);
        RailMapSavedData data = RailMapSavedData.get(world);
        int changedCount = data.reconcileRailsInArea(dimension, scan.positions, scan.signalPaths, scan.seenSignals, scan.scannedChunks, scan.minX, scan.maxX, scan.minZ, scan.maxZ);
        if (changedCount > 0) {
            Set<BlockPos> persisted = data.getRails(dimension);
            Set<Long> persistedSignals = data.getSignalRailLongs(dimension);
            IrAutoMod.NETWORK.sendToDimension(new RailMapSyncMessage(dimension, persisted, persistedSignals), dimension);
            IrAutoMod.getLogger().info("rail map scan updated: dim={}, changed={}, persistedRails={}, persistedSignals={}", dimension, changedCount, persisted.size(), persistedSignals.size());
            return true;
        }
        return false;
    }

    public static boolean collectForPlayer(EntityPlayerMP player) {
        WorldServer world = player.getServerWorld();
        int dimension = world.provider.getDimension();
        ScanResult scan = scanLoadedRailsAroundPlayer(world, player);
        RailMapSavedData data = RailMapSavedData.get(world);
        int changedCount = data.reconcileRailsInArea(dimension, scan.positions, scan.signalPaths, scan.seenSignals, scan.scannedChunks, scan.minX, scan.maxX, scan.minZ, scan.maxZ);
        if (changedCount > 0) {
            Set<BlockPos> persisted = data.getRails(dimension);
            Set<Long> persistedSignals = data.getSignalRailLongs(dimension);
            IrAutoMod.NETWORK.sendToDimension(new RailMapSyncMessage(dimension, persisted, persistedSignals), dimension);
            IrAutoMod.getLogger().info("rail map scan updated: dim={}, changed={}, persistedRails={}, persistedSignals={}", dimension, changedCount, persisted.size(), persistedSignals.size());
            return true;
        }
        return false;
    }

    public static void collectForPlayerManual(EntityPlayerMP player) {
        WorldServer world = player.getServerWorld();
        int dimension = world.provider.getDimension();
        ScanResult scan = scanLoadedRailsAroundPlayer(world, player);
        RailMapSavedData data = RailMapSavedData.get(world);
        data.reconcileRailsInArea(dimension, scan.positions, scan.signalPaths, scan.seenSignals, scan.scannedChunks, scan.minX, scan.maxX, scan.minZ, scan.maxZ);
        Set<BlockPos> persisted = data.getRails(dimension);
        Set<Long> persistedSignals = data.getSignalRailLongs(dimension);
        IrAutoMod.NETWORK.sendTo(new RailMapSyncMessage(dimension, persisted, persistedSignals), player);
        MapSyncTickHandler.syncStationsTo(player);
    }

    private static ScanResult scanLoadedRailsAroundPlayer(WorldServer world, EntityPlayerMP player) {
        List<EntityPlayerMP> players = new ArrayList<>(1);
        players.add(player);
        return scanLoadedRailsAroundPlayers(world, players);
    }

    private static ScanResult scanLoadedRailsAroundPlayers(WorldServer world, List<EntityPlayerMP> players) {
        Set<BlockPos> positions = new HashSet<>();
        Map<Long, Set<Long>> signalPaths = new HashMap<>();
        Set<Long> seenSignals = new HashSet<>();
        Set<Long> scannedChunks = new HashSet<>();

        int viewDistance = world.getMinecraftServer().getPlayerList().getViewDistance() + 1;
        ChunkProviderServer provider = world.getChunkProvider();

        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        Set<Long> candidateChunks = new HashSet<>();
        for (EntityPlayerMP player : players) {
            if (player == null) {
                continue;
            }
            ChunkPos cp = new ChunkPos(player.getPosition());
            int cMinX = cp.x - viewDistance;
            int cMaxX = cp.x + viewDistance;
            int cMinZ = cp.z - viewDistance;
            int cMaxZ = cp.z + viewDistance;

            minChunkX = Math.min(minChunkX, cMinX);
            maxChunkX = Math.max(maxChunkX, cMaxX);
            minChunkZ = Math.min(minChunkZ, cMinZ);
            maxChunkZ = Math.max(maxChunkZ, cMaxZ);

            for (int chunkX = cMinX; chunkX <= cMaxX; chunkX++) {
                for (int chunkZ = cMinZ; chunkZ <= cMaxZ; chunkZ++) {
                    candidateChunks.add((((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL));
                }
            }
        }

        int scannedMinChunkX = Integer.MAX_VALUE;
        int scannedMaxChunkX = Integer.MIN_VALUE;
        int scannedMinChunkZ = Integer.MAX_VALUE;
        int scannedMaxChunkZ = Integer.MIN_VALUE;

        for (long chunkKey : candidateChunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            Chunk chunk = provider.getLoadedChunk(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }
            scannedChunks.add(chunkKey);
            scannedMinChunkX = Math.min(scannedMinChunkX, chunkX);
            scannedMaxChunkX = Math.max(scannedMaxChunkX, chunkX);
            scannedMinChunkZ = Math.min(scannedMinChunkZ, chunkZ);
            scannedMaxChunkZ = Math.max(scannedMaxChunkZ, chunkZ);

            Map<BlockPos, TileEntity> tileEntityMap = chunk.getTileEntityMap();
            for (TileEntity tileEntity : tileEntityMap.values()) {
                if (tileEntity == null) {
                    continue;
                }
                if (isImmersiveRailTile(tileEntity) || isImmersiveRailBlock(tileEntity)) {
                    positions.add(tileEntity.getPos().toImmutable());
                }
                if (tileEntity instanceof TileTrainSignal) {
                    TileTrainSignal signal = (TileTrainSignal) tileEntity;
                    long sigPos = signal.getPos().toLong();
                    seenSignals.add(sigPos);
                    BlockPos a = signal.getRailA();
                    BlockPos b = signal.getRailB();
                    if (a != null && b != null) {
                        Set<Long> path = TrainSignalController.getSignalRailKeys(world, a, b);
                        if (path != null) {
                            signalPaths.put(sigPos, path);
                        }
                    }
                }
            }
        }

        if (scannedMinChunkX == Integer.MAX_VALUE) {
            scannedMinChunkX = minChunkX == Integer.MAX_VALUE ? 0 : minChunkX;
            scannedMaxChunkX = maxChunkX == Integer.MIN_VALUE ? 0 : maxChunkX;
            scannedMinChunkZ = minChunkZ == Integer.MAX_VALUE ? 0 : minChunkZ;
            scannedMaxChunkZ = maxChunkZ == Integer.MIN_VALUE ? 0 : maxChunkZ;
        }

        int minX = (scannedMinChunkX << 4);
        int maxX = (scannedMaxChunkX << 4) + 15;
        int minZ = (scannedMinChunkZ << 4);
        int maxZ = (scannedMaxChunkZ << 4) + 15;
        return new ScanResult(positions, signalPaths, seenSignals, scannedChunks, minX, maxX, minZ, maxZ);
    }

    private static ScanResult scanLoadedRailsInChunks(WorldServer world, Set<Long> chunkKeys) {
        Set<BlockPos> positions = new HashSet<>();
        Map<Long, Set<Long>> signalPaths = new HashMap<>();
        Set<Long> seenSignals = new HashSet<>();
        Set<Long> scannedChunks = new HashSet<>();

        ChunkProviderServer provider = world.getChunkProvider();

        int scannedMinChunkX = Integer.MAX_VALUE;
        int scannedMaxChunkX = Integer.MIN_VALUE;
        int scannedMinChunkZ = Integer.MAX_VALUE;
        int scannedMaxChunkZ = Integer.MIN_VALUE;

        for (long chunkKey : chunkKeys) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            Chunk chunk = provider.getLoadedChunk(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }
            scannedChunks.add(chunkKey);
            scannedMinChunkX = Math.min(scannedMinChunkX, chunkX);
            scannedMaxChunkX = Math.max(scannedMaxChunkX, chunkX);
            scannedMinChunkZ = Math.min(scannedMinChunkZ, chunkZ);
            scannedMaxChunkZ = Math.max(scannedMaxChunkZ, chunkZ);

            for (TileEntity tileEntity : chunk.getTileEntityMap().values()) {
                if (tileEntity == null) {
                    continue;
                }
                if (isImmersiveRailTile(tileEntity) || isImmersiveRailBlock(tileEntity)) {
                    positions.add(tileEntity.getPos().toImmutable());
                }
                if (tileEntity instanceof TileTrainSignal) {
                    TileTrainSignal signal = (TileTrainSignal) tileEntity;
                    long sigPos = signal.getPos().toLong();
                    seenSignals.add(sigPos);
                    BlockPos a = signal.getRailA();
                    BlockPos b = signal.getRailB();
                    if (a != null && b != null) {
                        Set<Long> path = TrainSignalController.getSignalRailKeys(world, a, b);
                        if (path != null) {
                            signalPaths.put(sigPos, path);
                        }
                    }
                }
            }
        }

        if (scannedMinChunkX == Integer.MAX_VALUE) {
            scannedMinChunkX = 0;
            scannedMaxChunkX = 0;
            scannedMinChunkZ = 0;
            scannedMaxChunkZ = 0;
        }

        int minX = (scannedMinChunkX << 4);
        int maxX = (scannedMaxChunkX << 4) + 15;
        int minZ = (scannedMinChunkZ << 4);
        int maxZ = (scannedMaxChunkZ << 4) + 15;
        return new ScanResult(positions, signalPaths, seenSignals, scannedChunks, minX, maxX, minZ, maxZ);
    }

    private static class ScanResult {
        private final Set<BlockPos> positions;
        private final Map<Long, Set<Long>> signalPaths;
        private final Set<Long> seenSignals;
        private final Set<Long> scannedChunks;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private ScanResult(Set<BlockPos> positions, Map<Long, Set<Long>> signalPaths, Set<Long> seenSignals, Set<Long> scannedChunks, int minX, int maxX, int minZ, int maxZ) {
            this.positions = positions;
            this.signalPaths = signalPaths;
            this.seenSignals = seenSignals;
            this.scannedChunks = scannedChunks;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }

    private static boolean isImmersiveRailTile(TileEntity tileEntity) {
        String className = tileEntity.getClass().getName();
        return className.startsWith("cam72cam.immersiverailroading")
            && (className.contains(".tile.TileRail") || className.contains(".tile.track.TileRail"));
    }

    private static boolean isImmersiveRailBlock(TileEntity tileEntity) {
        if (tileEntity.getBlockType() == null || tileEntity.getBlockType().getRegistryName() == null) {
            return false;
        }
        ResourceLocation registryName = tileEntity.getBlockType().getRegistryName();
        String namespace = registryName.getResourceDomain();
        String path = registryName.getResourcePath();
        return "immersiverailroading".equals(namespace) && path.contains("rail");
    }
}

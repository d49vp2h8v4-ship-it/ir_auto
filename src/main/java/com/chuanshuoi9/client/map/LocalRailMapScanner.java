package com.chuanshuoi9.client.map;

import com.chuanshuoi9.map.RailMapSavedData;
import com.chuanshuoi9.map.StationMarkerSavedData;
import com.chuanshuoi9.network.StationSyncMessage.StationData;
import com.chuanshuoi9.signal.TrainSignalController;
import com.chuanshuoi9.tile.TileTrainSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalRailMapScanner {
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    public static void refreshClientCacheFromLocalWorld() {
        refreshClientCacheFromLocalWorld(true);
    }

    public static void refreshClientCacheFromLocalWorld(boolean refreshStations) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.isSingleplayer() || mc.world == null || mc.getIntegratedServer() == null) {
            return;
        }
        EntityPlayer clientPlayer = mc.player;
        if (clientPlayer == null) {
            return;
        }
        int dim = mc.world.provider.getDimension();
        UUID playerId = clientPlayer.getUniqueID();

        if (!IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        mc.getIntegratedServer().addScheduledTask(() -> {
            try {
                WorldServer serverWorld = mc.getIntegratedServer().getWorld(dim);
                if (serverWorld == null) {
                    return;
                }

                EntityPlayerMP serverPlayer = mc.getIntegratedServer().getPlayerList().getPlayerByUUID(playerId);
                BlockPos centerPos = serverPlayer != null ? serverPlayer.getPosition() : clientPlayer.getPosition();

                ScanResult scan = scanLoadedAround(serverWorld, centerPos);
                RailMapSavedData data = RailMapSavedData.get(serverWorld);
                data.reconcileRailsInArea(dim, scan.railPositions, scan.signalPaths, scan.seenSignals, scan.scannedChunks, scan.minX, scan.maxX, scan.minZ, scan.maxZ);

                Set<BlockPos> persisted = data.getRails(dim);
                Set<Long> persistedSignals = data.getSignalRailLongs(dim);
                Set<BlockPos> persistedSignalBlocks = new HashSet<>(persistedSignals.size());
                for (long packed : persistedSignals) {
                    persistedSignalBlocks.add(BlockPos.fromLong(packed));
                }

                List<StationData> stations = refreshStations ? extractAllStations(serverWorld) : null;

                mc.addScheduledTask(() -> {
                    RailMapClientCache.update(dim, persisted, persistedSignalBlocks);
                    if (stations != null) {
                        RailMapClientCache.updateStations(stations);
                    }
                });
            } finally {
                IN_FLIGHT.set(false);
            }
        });
    }

    private static ScanResult scanLoadedAround(WorldServer world, BlockPos centerPos) {
        Set<BlockPos> positions = new HashSet<>();
        Map<Long, Set<Long>> signalPaths = new HashMap<>();
        Set<Long> seenSignals = new HashSet<>();
        Set<Long> scannedChunks = new HashSet<>();

        int centerChunkX = new ChunkPos(centerPos).x;
        int centerChunkZ = new ChunkPos(centerPos).z;
        int viewDistance = world.getMinecraftServer().getPlayerList().getViewDistance() + 1;
        net.minecraft.world.gen.ChunkProviderServer provider = world.getChunkProvider();
        int minChunkX = centerChunkX - viewDistance;
        int maxChunkX = centerChunkX + viewDistance;
        int minChunkZ = centerChunkZ - viewDistance;
        int maxChunkZ = centerChunkZ + viewDistance;
        int minX = (minChunkX << 4);
        int maxX = (maxChunkX << 4) + 15;
        int minZ = (minChunkZ << 4);
        int maxZ = (maxChunkZ << 4) + 15;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                net.minecraft.world.chunk.Chunk chunk = provider.getLoadedChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                scannedChunks.add((((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL));
                for (TileEntity tileEntity : chunk.getTileEntityMap().values()) {
                    if (tileEntity == null) {
                        continue;
                    }
                    if (isImmersiveRailTile(tileEntity) || isImmersiveRailBlock(tileEntity)) {
                        positions.add(tileEntity.getPos().toImmutable());
                    }
                    if (tileEntity instanceof TileTrainSignal) {
                        TileTrainSignal signal = (TileTrainSignal) tileEntity;
                        long sigPosLong = signal.getPos().toLong();
                        seenSignals.add(sigPosLong);
                        BlockPos a = signal.getRailA();
                        BlockPos b = signal.getRailB();
                        if (a != null && b != null) {
                            Set<Long> keys = TrainSignalController.getSignalRailKeys(world, a, b);
                            if (keys != null && !keys.isEmpty()) {
                                signalPaths.put(sigPosLong, keys);
                            }
                        }
                    }
                }
            }
        }
        return new ScanResult(positions, signalPaths, seenSignals, scannedChunks, minX, maxX, minZ, maxZ);
    }

    private static class ScanResult {
        private final Set<BlockPos> railPositions;
        private final Map<Long, Set<Long>> signalPaths;
        private final Set<Long> seenSignals;
        private final Set<Long> scannedChunks;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private ScanResult(Set<BlockPos> railPositions, Map<Long, Set<Long>> signalPaths, Set<Long> seenSignals, Set<Long> scannedChunks, int minX, int maxX, int minZ, int maxZ) {
            this.railPositions = railPositions;
            this.signalPaths = signalPaths;
            this.seenSignals = seenSignals;
            this.scannedChunks = scannedChunks;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }

    private static List<StationData> extractAllStations(WorldServer world) {
        if (world == null || world.isRemote) {
            return new ArrayList<>();
        }
        int dim = world.provider.getDimension();
        StationMarkerSavedData data = StationMarkerSavedData.get(world);
        return new ArrayList<>(data.getStations(dim));
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

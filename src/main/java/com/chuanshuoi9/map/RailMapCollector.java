package com.chuanshuoi9.map;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.network.RailMapSyncMessage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import com.chuanshuoi9.tile.TileTrainSignal;
import com.chuanshuoi9.signal.TrainSignalController;
import com.chuanshuoi9.train.IrTrainReflection;
import trackapi.lib.ITrack;

import java.util.*;

public class RailMapCollector {

    // ── Public scan entry points ────────────────────────────

    /** Scan specific dirty chunks (from block place/break events). */
    public static boolean collectForDirtyChunks(WorldServer world, Set<Long> dirtyChunkKeys) {
        return runScan(world, dirtyChunkKeys, null);
    }

    /** Scan around players (periodic scan). */
    public static boolean collectForPlayersInDimension(WorldServer world, List<EntityPlayerMP> players) {
        return runScan(world, null, players);
    }

    /** Scan for single player. */
    public static boolean collectForPlayer(EntityPlayerMP player) {
        WorldServer world = player.getServerWorld();
        List<EntityPlayerMP> list = new ArrayList<>(1);
        list.add(player);
        return runScan(world, null, list);
    }

    /** Manual rescan for one player. */
    public static void collectForPlayerManual(EntityPlayerMP player) {
        WorldServer world = player.getServerWorld();
        List<EntityPlayerMP> list = new ArrayList<>(1);
        list.add(player);
        runScanAndSendToPlayer(world, null, list, player);
    }

    // ── Core scan logic ─────────────────────────────────────

    private static boolean runScan(WorldServer world, Set<Long> chunkKeys, List<EntityPlayerMP> players) {
        int dimension = world.provider.getDimension();

        Set<Long> chunks;
        if (chunkKeys != null) {
            chunks = chunkKeys;
        } else {
            chunks = buildPlayerChunkSet(world, players);
        }
        if (chunks == null || chunks.isEmpty()) return false;

        DiscoverResult discovered = discoverEntryKeys(world, chunks, dimension);

        RailMapSavedData data = RailMapSavedData.get(world);
        int changedCount = data.reconcileRailsInArea(dimension, discovered.positions,
            discovered.signalPaths, discovered.seenSignals, discovered.scannedChunks,
            discovered.minX, discovered.maxX, discovered.minZ, discovered.maxZ);

        if (changedCount > 0) {
            Set<BlockPos> persisted = data.getRails(dimension);
            Set<Long> persistedSignals = data.getSignalRailLongs(dimension);

            IrAutoMod.NETWORK.sendToDimension(
                new RailMapSyncMessage(dimension, persisted, persistedSignals), dimension);

            IrAutoMod.getLogger().info(
                "rail map scan: dim={}, changed={}, rails={}, signals={}",
                dimension, changedCount, persisted.size(), persistedSignals.size());
            return true;
        }
        return false;
    }

    private static void runScanAndSendToPlayer(WorldServer world, Set<Long> chunkKeys,
                                                List<EntityPlayerMP> players, EntityPlayerMP target) {
        int dimension = world.provider.getDimension();
        Set<Long> chunks = chunkKeys != null ? chunkKeys : buildPlayerChunkSet(world, players);
        if (chunks == null || chunks.isEmpty()) return;

        DiscoverResult discovered = discoverEntryKeys(world, chunks, dimension);

        RailMapSavedData data = RailMapSavedData.get(world);
        data.reconcileRailsInArea(dimension, discovered.positions,
            discovered.signalPaths, discovered.seenSignals, discovered.scannedChunks,
            discovered.minX, discovered.maxX, discovered.minZ, discovered.maxZ);

        Set<BlockPos> persisted = data.getRails(dimension);
        Set<Long> persistedSignals = data.getSignalRailLongs(dimension);
        IrAutoMod.NETWORK.sendTo(new RailMapSyncMessage(dimension, persisted, persistedSignals), target);
        MapSyncTickHandler.syncStationsTo(target);
    }

    // ── Entry key discovery ─────────────────────────────────

    private static DiscoverResult discoverEntryKeys(WorldServer world, Set<Long> chunkKeys, int dimension) {
        DiscoverResult result = new DiscoverResult();
        ChunkProviderServer provider = world.getChunkProvider();
        TrackGraphSavedData graph = TrackGraphSavedData.get(world);

        int sMinX = Integer.MAX_VALUE, sMaxX = Integer.MIN_VALUE;
        int sMinZ = Integer.MAX_VALUE, sMaxZ = Integer.MIN_VALUE;

        for (long chunkKey : chunkKeys) {
            int cx = (int)(chunkKey >> 32);
            int cz = (int)chunkKey;
            Chunk chunk = provider.getLoadedChunk(cx, cz);
            if (chunk == null) continue;

            result.scannedChunks.add(chunkKey);
            sMinX = Math.min(sMinX, cx);
            sMaxX = Math.max(sMaxX, cx);
            sMinZ = Math.min(sMinZ, cz);
            sMaxZ = Math.max(sMaxZ, cz);

            for (TileEntity te : chunk.getTileEntityMap().values()) {
                if (te == null) continue;

                if (te instanceof ITrack) {
                    Long k = IrTrainReflection.getLogicalRailKey(te);
                    BlockPos pos = k == null ? te.getPos() : BlockPos.fromLong(k);
                    result.positions.add(pos.toImmutable());
                    if (k != null) {
                        result.entryKeys.add(k);
                    }
                }
                else if (isImmersiveRailTile(te)) {
                    Long pk = tryGetParentLong(te);
                    BlockPos pos = pk == null ? te.getPos() : BlockPos.fromLong(pk);
                    result.positions.add(pos.toImmutable());
                    if (pk != null) {
                        result.entryKeys.add(pk);
                    }
                }
                if (te instanceof TileTrainSignal) {
                    TileTrainSignal sig = (TileTrainSignal) te;
                    long sigPos = sig.getPos().toLong();
                    result.seenSignals.add(sigPos);
                    BlockPos a = sig.getRailA();
                    BlockPos b = sig.getRailB();
                    if (a != null && b != null) {
                        Set<Long> path = TrainSignalController.getSignalRailKeys(world, a, b);
                        if (path != null) {
                            result.signalPaths.put(sigPos, path);
                        }
                    }
                }
            }
        }

        result.minX = sMinX == Integer.MAX_VALUE ? 0 : (sMinX << 4);
        result.maxX = sMaxX == Integer.MIN_VALUE ? 0 : (sMaxX << 4) + 15;
        result.minZ = sMinZ == Integer.MAX_VALUE ? 0 : (sMinZ << 4);
        result.maxZ = sMaxZ == Integer.MIN_VALUE ? 0 : (sMaxZ << 4) + 15;
        return result;
    }

    private static Set<Long> buildPlayerChunkSet(WorldServer world, List<EntityPlayerMP> players) {
        Set<Long> chunks = new HashSet<>();
        if (players == null) return chunks;
        int viewDist = world.getMinecraftServer().getPlayerList().getViewDistance() + 1;
        for (EntityPlayerMP p : players) {
            if (p == null) continue;
            ChunkPos cp = new ChunkPos(p.getPosition());
            int x0 = cp.x - viewDist, x1 = cp.x + viewDist;
            int z0 = cp.z - viewDist, z1 = cp.z + viewDist;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    chunks.add((((long)x) << 32) ^ (z & 0xffffffffL));
                }
            }
        }
        return chunks;
    }

    // ── IR tile detection ───────────────────────────────────

    private static boolean isImmersiveRailTile(TileEntity te) {
        String cn = te.getClass().getName();
        return cn.startsWith("cam72cam.immersiverailroading")
            && (cn.contains(".tile.TileRail") || cn.contains(".tile.track.TileRail"));
    }

    private static Long tryGetParentLong(TileEntity te) {
        if (te == null) return null;
        try {
            Object parent = te.getClass().getMethod("getParent").invoke(te);
            return IrTrainReflection.vec3iToBlockPosLong(parent);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Data holders ────────────────────────────────────────

    private static class DiscoverResult {
        final Set<BlockPos> positions = new HashSet<>();
        final Set<Long> entryKeys = new HashSet<>();
        final Map<Long, Set<Long>> signalPaths = new HashMap<>();
        final Set<Long> seenSignals = new HashSet<>();
        final Set<Long> scannedChunks = new HashSet<>();
        int minX, maxX, minZ, maxZ;
    }
}

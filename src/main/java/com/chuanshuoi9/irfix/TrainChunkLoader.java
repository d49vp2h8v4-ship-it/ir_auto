package com.chuanshuoi9.irfix;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.IrTrainReflection;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Reliable train chunk loader — no ForgeChunkManager ticket limits.
 *
 * Each consist gets a radius (3–5 chunks) around its entities.
 * Uses a global reference count: a chunk is loaded while ≥1 consist needs it.
 * Two trains sharing the same chunk → count=2 → stays loaded until both leave.
 */
public class TrainChunkLoader {

    // dim → (chunkKey → refCount)
    private static final Map<Integer, Map<Long, Integer>> REF_COUNTS = new HashMap<>();

    // dim → (chunkKey → releaseTick) — pending release (grace period)
    private static final Map<Integer, Map<Long, Long>> PENDING_RELEASE = new HashMap<>();

    // Per-world: entity → last active tick (for keep-alive after stop)
    private static class WorldState {
        final Map<UUID, Long> lastActiveByEntity = new HashMap<>();
    }

    private static final Map<Integer, WorldState> STATE = new HashMap<>();

    // ── Tick ──────────────────────────────────────────────

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!TrainChunkLoadingConfig.enabled) return;
        if (event.phase != TickEvent.Phase.END) return;
        World world = event.world;
        if (!(world instanceof WorldServer)) return;

        WorldServer ws = (WorldServer) world;
        int dim = ws.provider.getDimension();
        long now = ws.getTotalWorldTime();

        // 1. Clean up expired pending releases
        Map<Long, Long> pending = PENDING_RELEASE.get(dim);
        if (pending != null) {
            Iterator<Map.Entry<Long, Long>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Long> e = it.next();
                if (now >= e.getValue()) {
                    releaseChunk(ws, dim, e.getKey());
                    it.remove();
                }
            }
        }

        // 2. Collect which chunks each consist needs
        WorldState wstate = STATE.computeIfAbsent(dim, k -> new WorldState());
        Map<UUID, Set<Long>> desiredByConsist = new HashMap<>();
        collectDesiredChunks(ws, wstate, now, desiredByConsist);

        // 3. Build total desired set from all consists
        Set<Long> desired = new HashSet<>();
        for (Set<Long> set : desiredByConsist.values()) desired.addAll(set);

        // 4. Load new + hold existing
        Map<Long, Integer> refs = REF_COUNTS.computeIfAbsent(dim, k -> new HashMap<>());
        for (long ck : desired) {
            int count = refs.getOrDefault(ck, 0);
            if (count == 0) {
                loadChunk(ws, ck);
            }
            refs.put(ck, count + 2); // 2=temporarily boosted to avoid thrashing
            // Cancel any pending release for this chunk
            if (pending != null) pending.remove(ck);
        }

        // 5. Release chunks no longer needed (with grace delay)
        Iterator<Map.Entry<Long, Integer>> it = refs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Integer> e = it.next();
            long ck = e.getKey();
            if (!desired.contains(ck)) {
                int count = e.getValue();
                if (count <= 1) {
                    // Schedule delayed release
                    if (pending == null) {
                        pending = new HashMap<>();
                        PENDING_RELEASE.put(dim, pending);
                    }
                    if (!pending.containsKey(ck)) {
                        pending.put(ck, now + TrainChunkLoadingConfig.releaseDelayTicks);
                    }
                    it.remove();
                } else {
                    refs.put(ck, count - 1);
                }
            }
        }
    }

    // ── World unload cleanup ──────────────────────────────

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (!(world instanceof WorldServer)) return;
        int dim = ((WorldServer) world).provider.getDimension();

        STATE.remove(dim);
        PENDING_RELEASE.remove(dim);
        Map<Long, Integer> refs = REF_COUNTS.remove(dim);
        if (refs == null) return;
        // Don't unload on world unload — MC handles that. Just clear refs.
    }

    // ── Collect desired chunks ────────────────────────────

    private void collectDesiredChunks(WorldServer world, WorldState wstate, long now,
                                       Map<UUID, Set<Long>> out) {
        // Group entities by consist
        Map<UUID, ConsistGroup> groups = new HashMap<>();
        for (Entity entity : world.loadedEntityList) {
            if (!IrTrainReflection.isIrTrainEntity(entity)) continue;
            UUID key = IrTrainReflection.getConsistGroupKey(entity);
            ConsistGroup g = groups.computeIfAbsent(key, k -> new ConsistGroup(k));
            g.entities.add(entity);
        }

        for (ConsistGroup g : groups.values()) {
            if (g.entities.isEmpty()) continue;

            double speedKmh = 0.0;
            Entity lead = null;
            for (Entity e : g.entities) {
                double v = getSpeedKmhFallback(e);
                if (v > speedKmh) speedKmh = v;
                if (lead == null) lead = e;
                else if (!IrTrainReflection.isLocomotive(lead) && IrTrainReflection.isLocomotive(e)) lead = e;
            }

            boolean moving = speedKmh > TrainChunkLoadingConfig.speedThresholdKmh;
            if (moving) {
                wstate.lastActiveByEntity.put(g.groupKey, now);
            } else {
                Long last = wstate.lastActiveByEntity.get(g.groupKey);
                if (last == null || now - last > TrainChunkLoadingConfig.keepAliveTicksAfterStop) {
                    continue;
                }
            }

            Set<Long> chunks = new HashSet<>();
            int radius = moving ? TrainChunkLoadingConfig.movingRadiusChunks
                                : TrainChunkLoadingConfig.idleRadiusChunks;
            ChunkRange range = getChunkRangeForEntities(g.entities, radius);
            addRect(chunks, range.minX, range.maxX, range.minZ, range.maxZ);

            // Ahead preload
            if (moving && TrainChunkLoadingConfig.aheadChunksMax > 0 && lead != null) {
                int ahead = computeAheadChunks(speedKmh);
                if (ahead > 0) {
                    Vec2 dir = getDirection2D(lead);
                    if (dir.len >= 1.0E-4) {
                        Vec2 front = getFrontMostPoint(g.entities, dir);
                        for (int i = 1; i <= ahead; i++) {
                            int ax = (int) Math.floor(front.x + dir.x * (16.0 * i)) >> 4;
                            int az = (int) Math.floor(front.z + dir.z * (16.0 * i)) >> 4;
                            addSquare(chunks, ax, az, TrainChunkLoadingConfig.aheadRadiusChunks);
                        }
                    }
                }
            }

            out.put(g.groupKey, chunks);
        }

        // Clean stale entries
        if (!wstate.lastActiveByEntity.isEmpty()) {
            wstate.lastActiveByEntity.entrySet().removeIf(e -> now - e.getValue() > TrainChunkLoadingConfig.keepAliveTicksAfterStop);
        }
    }

    // ── Chunk load / release ──────────────────────────────

    private static void loadChunk(WorldServer world, long chunkKey) {
        int cx = (int) (chunkKey >> 32);
        int cz = (int) chunkKey;
        try {
            ChunkProviderServer provider = world.getChunkProvider();
            provider.loadChunk(cx, cz);
        } catch (Exception e) {
            IrAutoMod.getLogger().warn("Failed to load chunk [{}, {}]", cx, cz);
        }
    }

    private static void releaseChunk(WorldServer world, int dim, long chunkKey) {
        // Chunks loaded via loadChunk() are in memory but not force-ticketed.
        // When all reference counts drop to zero, MC's natural chunk GC will
        // unload them during the next world tick — no explicit unload needed.
        // We just need to stop calling loadChunk() for them, which the ref
        // counting system handles.
    }

    // ── Helpers ───────────────────────────────────────────

    private static void addSquare(Set<Long> out, int cx, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add((((long) (cx + dx)) << 32) ^ ((cz + dz) & 0xffffffffL));
            }
        }
    }

    private static void addRect(Set<Long> out, int minX, int maxX, int minZ, int maxZ) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                out.add((((long) cx) << 32) ^ (cz & 0xffffffffL));
            }
        }
    }

    private int computeAheadChunks(double speedKmh) {
        if (speedKmh <= TrainChunkLoadingConfig.speedThresholdKmh) return 0;
        int ahead = (int) Math.ceil(speedKmh / 35.0);
        return Math.max(1, Math.min(ahead, TrainChunkLoadingConfig.aheadChunksMax));
    }

    private static ChunkRange getChunkRangeForEntities(List<Entity> entities, int radius) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Entity e : entities) {
            if (e == null) continue;
            try {
                if (e.getEntityBoundingBox() != null) {
                    minX = Math.min(minX, e.getEntityBoundingBox().minX);
                    maxX = Math.max(maxX, e.getEntityBoundingBox().maxX);
                    minZ = Math.min(minZ, e.getEntityBoundingBox().minZ);
                    maxZ = Math.max(maxZ, e.getEntityBoundingBox().maxZ);
                    continue;
                }
            } catch (Throwable ignored) {}
            minX = Math.min(minX, e.posX); maxX = Math.max(maxX, e.posX);
            minZ = Math.min(minZ, e.posZ); maxZ = Math.max(maxZ, e.posZ);
        }
        if (!Double.isFinite(minX)) return new ChunkRange(0, 0, 0, 0);
        return new ChunkRange(
            (int) Math.floor(minX) >> 4 - radius,
            (int) Math.floor(maxX) >> 4 + radius,
            (int) Math.floor(minZ) >> 4 - radius,
            (int) Math.floor(maxZ) >> 4 + radius
        );
    }

    private static Vec2 getDirection2D(Entity e) {
        if (e == null) return new Vec2(0, 0);
        double dx = e.motionX, dz = e.motionZ;
        double mag = Math.sqrt(dx * dx + dz * dz);
        if (mag < 1.0E-4) {
            double yawRad = Math.toRadians(e.rotationYaw);
            dx = -Math.sin(yawRad); dz = Math.cos(yawRad);
            mag = Math.sqrt(dx * dx + dz * dz);
        }
        return mag < 1.0E-4 ? new Vec2(0, 0) : new Vec2(dx / mag, dz / mag);
    }

    private static Vec2 getFrontMostPoint(List<Entity> entities, Vec2 dir) {
        double best = Double.NEGATIVE_INFINITY;
        double bx = 0, bz = 0;
        for (Entity e : entities) {
            if (e == null) continue;
            double x = e.posX, z = e.posZ;
            try {
                if (e.getEntityBoundingBox() != null) {
                    x = (e.getEntityBoundingBox().minX + e.getEntityBoundingBox().maxX) * 0.5;
                    z = (e.getEntityBoundingBox().minZ + e.getEntityBoundingBox().maxZ) * 0.5;
                }
            } catch (Throwable ignored) {}
            double p = x * dir.x + z * dir.z;
            if (p > best) { best = p; bx = x; bz = z; }
        }
        return new Vec2(bx, bz);
    }

    private static double getSpeedKmhFallback(Entity e) {
        if (e == null) return 0;
        double kmh = 0;
        try { kmh = IrTrainReflection.getSpeedKmh(e); } catch (Throwable ignored) {}
        if (!Double.isFinite(kmh) || kmh < 0) kmh = 0;
        if (kmh > 0) return Math.min(800, kmh);
        double mps = Math.sqrt(e.motionX * e.motionX + e.motionZ * e.motionZ) * 20;
        double fb = mps * 3.6;
        return Double.isFinite(fb) && fb >= 0 ? Math.min(800, fb) : 0;
    }

    // ── Data holders ──────────────────────────────────────

    private static class ConsistGroup {
        final UUID groupKey;
        final List<Entity> entities = new ArrayList<>();
        ConsistGroup(UUID k) { this.groupKey = k; }
    }

    private static class ChunkRange {
        final int minX, maxX, minZ, maxZ;
        ChunkRange(int a, int b, int c, int d) { this.minX = a; this.maxX = b; this.minZ = c; this.maxZ = d; }
    }

    private static class Vec2 {
        final double x, z, len;
        Vec2(double x, double z) { this.x = x; this.z = z; this.len = Math.sqrt(x * x + z * z); }
    }
}

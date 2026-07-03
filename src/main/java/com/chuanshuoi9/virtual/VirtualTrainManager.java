package com.chuanshuoi9.virtual;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.map.RailMapSavedData;
import com.chuanshuoi9.map.TrackGraphSavedData;
import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.train.TrainTimetableStorage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.util.Constants;
import trackapi.lib.ITrack;
import trackapi.lib.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public class VirtualTrainManager {
    private final Map<Integer, Map<UUID, Long>> lastNearPlayerTick = new HashMap<>();
    private final Map<UUID, Route> routes = new HashMap<>();
    private final Map<Integer, SignalIndexCache> signalIndexCacheByDim = new HashMap<>();
    private final Map<Integer, LoadedKeysCache> loadedKeysCacheByDim = new HashMap<>();
    private final Map<Integer, SegmentOccCache> segmentOccCacheByDim = new HashMap<>();
    private static final double VIRTUAL_ACCEL_MPS2 = 0.35;
    private static final double VIRTUAL_FULL_BRAKE_DECEL_MPS2 = 1.25;
    private static final int RECENT_KEYS_MAX = 64;
    private static final double ROUTELESS_AUTOPILOT_FALLBACK_SPEED_KMH = 15.0;

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.getWorld() instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) event.getWorld();
        int dim = ws.provider.getDimension();
        lastNearPlayerTick.remove(dim);
        VirtualTrainSavedData data = VirtualTrainSavedData.get(ws);
        if (data != null) {
            for (VirtualTrainSavedData.VirtualTrainState st : data.byDimension(dim)) {
                if (st != null && st.id != null) {
                    routes.remove(st.id);
                }
            }
        }
        signalIndexCacheByDim.remove(dim);
        loadedKeysCacheByDim.remove(dim);
        segmentOccCacheByDim.remove(dim);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!VirtualTrainConfig.enabled) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        World world = event.world;
        if (!(world instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) world;
        if (ws.isRemote) {
            return;
        }
        int dim = ws.provider.getDimension();
        long now = ws.getTotalWorldTime();

        VirtualTrainSavedData data = VirtualTrainSavedData.get(ws);
        TrackGraphSavedData graph = TrackGraphSavedData.get(ws);

        int simInterval = Math.max(1, VirtualTrainConfig.simTickInterval);
        int virtInterval = Math.max(1, VirtualTrainConfig.virtualizeScanInterval);
        if (now % simInterval == 0L) {
            tickVirtualTrains(ws, data, graph, dim, now, simInterval);
        }
        if (now % virtInterval == 0L) {
            maybeVirtualizeLoadedConsists(ws, data, graph, dim, now);
        }
    }

    private void tickVirtualTrains(WorldServer world, VirtualTrainSavedData data, TrackGraphSavedData graph, int dim, long now, int tickDelta) {
        List<VirtualTrainSavedData.VirtualTrainState> states = data.byDimension(dim);
        if (states.isEmpty()) {
            return;
        }
        RailMapSavedData railMap = RailMapSavedData.get(world);
        Map<Long, Set<Long>> segments = railMap.getSignalSegments(dim);
        boolean hasSignals = segments != null && !segments.isEmpty() && VirtualTrainConfig.maxSignalsToCheck != 0;
        SignalIndex signalIndex = null;
        Map<Long, Integer> segmentOccupancy = java.util.Collections.emptyMap();
        if (hasSignals) {
            int sigInterval = Math.max(1, VirtualTrainConfig.signalIndexRebuildInterval);
            SignalIndexCache sc = signalIndexCacheByDim.computeIfAbsent(dim, __ -> new SignalIndexCache());
            if (sc.index == null || now - sc.lastTick >= sigInterval) {
                sc.index = buildSignalIndex(segments);
                sc.lastTick = now;
            }
            signalIndex = sc.index;
            if (signalIndex != null && !signalIndex.keyToSegments.isEmpty()) {
                int keysInterval = Math.max(1, VirtualTrainConfig.loadedTrainKeysRecalcInterval);
                LoadedKeysCache kc = loadedKeysCacheByDim.computeIfAbsent(dim, __ -> new LoadedKeysCache());
                if (kc.keys == null || now - kc.lastTick >= keysInterval) {
                    kc.keys = collectLoadedTrainKeys(world, signalIndex);
                    kc.lastTick = now;
                }
                int occInterval = Math.max(1, VirtualTrainConfig.segmentOccupancyRecalcInterval);
                SegmentOccCache oc = segmentOccCacheByDim.computeIfAbsent(dim, __ -> new SegmentOccCache());
                if (oc.occ == null || now - oc.lastTick >= occInterval) {
                    oc.occ = computeSegmentOccupancy(data, dim, signalIndex, kc.keys);
                    oc.lastTick = now;
                }
                segmentOccupancy = oc.occ == null ? java.util.Collections.emptyMap() : oc.occ;
            }
        }
        List<UUID> toRemove = new ArrayList<>();
        boolean dirty = false;
        for (VirtualTrainSavedData.VirtualTrainState st : states) {
            if (st == null) continue;
            if (isAnyPlayerNear(world, st.pos(), VirtualTrainConfig.activateRangeBlocks)) {
                if (tryRespawn(world, st)) {
                    toRemove.add(st.id);
                } else {
                    st.lastActiveTick = now;
                    dirty = true;
                }
                continue;
            }
            Route route = null;
            double desiredSpeedKmh = Math.max(0.0, st.speedKmh);
            if (st.controlRoot != null && st.controlRoot.getBoolean(TrainAutoPilotData.ENABLED)) {
                StopPlan plan = updateVirtualTimetable(st, graph, dim, now);
                if (plan != null && plan.active) {
                    route = getOrBuildRoute(st, graph, dim, plan.targetKey);
                    double distToStop = route == null ? Double.NaN : distanceAlongRouteToKey(st, route, graph, dim, plan.targetKey);
                    if (Double.isFinite(distToStop) && distToStop <= plan.stopDistance) {
                        enterVirtualDwell(st, now, plan.waitTicks);
                        desiredSpeedKmh = 0.0;
                    } else if (Double.isFinite(distToStop)) {
                        desiredSpeedKmh = Math.min(desiredSpeedKmh, computeApproachSpeedLimitKmh(distToStop, plan.stopDistance, plan.maxSpeedKmh));
                    } else {
                        desiredSpeedKmh = Math.min(desiredSpeedKmh, ROUTELESS_AUTOPILOT_FALLBACK_SPEED_KMH);
                    }
                } else {
                    desiredSpeedKmh = 0.0;
                }
            }

            if (signalIndex != null && !signalIndex.keyToSegments.isEmpty() && segmentOccupancy != null && !segmentOccupancy.isEmpty()) {
                double signalLimit = computeSignalSpeedLimitKmh(st, route, graph, dim, segments, signalIndex, segmentOccupancy);
                if (Double.isFinite(signalLimit)) {
                    desiredSpeedKmh = Math.min(desiredSpeedKmh, signalLimit);
                }
            }

            int dt = Math.max(1, tickDelta);
            st.speedKmh = moveSpeedTowards(st.speedKmh, desiredSpeedKmh, dt);
            advanceAlongGraph(world, st, graph, dim, route, dt);
            st.lastActiveTick = now;
            st.lastSimTick = now;
            dirty = true;
        }
        for (UUID id : toRemove) {
            data.remove(id);
            dirty = true;
        }
        if (dirty) {
            data.markDirty();
        }
    }

    private boolean tryRespawn(WorldServer world, VirtualTrainSavedData.VirtualTrainState st) {
        if (st == null || st.cars == null || st.cars.isEmpty()) {
            return true;
        }
        BlockPos p = new BlockPos(st.x, st.y, st.z);
        if (!world.isBlockLoaded(p)) {
            return false;
        }
        List<Entity> spawned = new ArrayList<>();
        for (VirtualTrainSavedData.CarSnapshot c : st.cars) {
            if (c == null || c.entityTag == null) continue;
            NBTTagCompound tag = c.entityTag.copy();
            Entity e = EntityList.createEntityFromNBT(tag, world);
            if (e == null) {
                continue;
            }
            e.setPosition(st.x + c.relX, st.y + c.relY, st.z + c.relZ);
            world.spawnEntity(e);
            if (st.controlTrainId != null && st.controlRoot != null && st.controlTrainId.equals(e.getUniqueID())) {
                NBTTagCompound root = st.controlRoot.copy();
                TrainAutoPilotData.ensureDefaults(root);
                e.getEntityData().setTag(TrainAutoPilotData.ROOT, root);
                TrainTimetableStorage.saveTimetable(e, root);
            }
            spawned.add(e);
        }
        return !spawned.isEmpty();
    }

    private void maybeVirtualizeLoadedConsists(WorldServer world, VirtualTrainSavedData data, TrackGraphSavedData graph, int dim, long now) {
        Set<UUID> alreadyVirtual = new HashSet<>();
        for (VirtualTrainSavedData.VirtualTrainState st : data.byDimension(dim)) {
            if (st != null && st.id != null) alreadyVirtual.add(st.id);
        }

        Map<UUID, ConsistGroup> groups = new HashMap<>();
        for (Entity e : snapshotLoadedEntities(world)) {
            if (!IrTrainReflection.isIrTrainEntity(e)) {
                continue;
            }
            UUID gk = IrTrainReflection.getConsistGroupKey(e);
            if (gk == null) {
                continue;
            }
            ConsistGroup g = groups.get(gk);
            if (g == null) {
                g = new ConsistGroup(gk);
                groups.put(gk, g);
            }
            g.entities.add(e);
        }

        Map<UUID, Long> lastNear = lastNearPlayerTick.computeIfAbsent(dim, k -> new HashMap<>());
        Set<UUID> seen = new HashSet<>();
        for (ConsistGroup g : groups.values()) {
            if (g == null || g.entities.isEmpty()) continue;
            UUID id = g.groupKey;
            if (id == null) continue;
            seen.add(id);
            if (alreadyVirtual.contains(id)) continue;
            ConsistInfo info = buildConsistInfo(world, g.entities);
            if (info == null) continue;
            boolean anyNear = isAnyPlayerNear(world, info.center, VirtualTrainConfig.activateRangeBlocks);
            if (anyNear) {
                lastNear.put(id, now);
                continue;
            }
            Long last = lastNear.get(id);
            if (last != null && now - last < VirtualTrainConfig.idleTicksBeforeVirtualize) {
                continue;
            }
            if (info.speedKmh < VirtualTrainConfig.minSpeedKmhToVirtualize) {
                continue;
            }
            VirtualTrainSavedData.VirtualTrainState st = snapshotAndRemove(world, info, dim, now, graph);
            if (st != null) {
                data.put(st);
            }
        }
        if (!lastNear.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = lastNear.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                if (e == null) {
                    it.remove();
                    continue;
                }
                UUID id = e.getKey();
                if (id == null || !seen.contains(id) || now - e.getValue() > 20L * 60L * 60L) {
                    it.remove();
                }
            }
        }
    }

    private VirtualTrainSavedData.VirtualTrainState snapshotAndRemove(WorldServer world, ConsistInfo info, int dim, long now, TrackGraphSavedData graph) {
        VirtualTrainSavedData.VirtualTrainState st = new VirtualTrainSavedData.VirtualTrainState();
        st.id = info.groupId;
        st.dimension = dim;
        st.x = info.center.x;
        st.y = info.center.y;
        st.z = info.center.z;
        st.dirX = info.dirX;
        st.dirZ = info.dirZ;
        st.speedKmh = info.speedKmh;
        st.lastActiveTick = now;
        st.lastSimTick = now;
        Long key = getLogicalRailKeyAt(world, info.center);
        st.currentKey = key == null ? 0L : key;
        st.prevKey = 0L;
        st.nextKey = chooseNextKey(world, graph, dim, st.currentKey, st.prevKey, st.dirX, st.dirZ, st.pos());
        st.edgeProgress = 0.0;
        st.edgeLength = getEdgeLength(graph, dim, st.currentKey, st.nextKey);

        Entity control = null;
        for (Entity e : info.entities) {
            if (e == null) continue;
            if (IrTrainReflection.isIrControllableTrain(e)) {
                control = e;
                break;
            }
        }
        if (control == null) {
            for (Entity e : info.entities) {
                if (e != null) {
                    control = e;
                    break;
                }
            }
        }
        if (control != null) {
            st.controlTrainId = control.getUniqueID();
            NBTTagCompound root = null;
            try {
                if (control.getEntityData().hasKey(TrainAutoPilotData.ROOT, Constants.NBT.TAG_COMPOUND)) {
                    root = control.getEntityData().getCompoundTag(TrainAutoPilotData.ROOT).copy();
                }
            } catch (Throwable ignored) {
                root = null;
            }
            if (root == null && st.controlTrainId != null) {
                root = TrainTimetableStorage.getTimetable(world, st.controlTrainId);
            }
            if (root != null) {
                TrainAutoPilotData.ensureDefaults(root);
                st.controlRoot = root;
            }
        }

        for (Entity e : info.entities) {
            if (e == null || e.isDead) continue;
            NBTTagCompound tag = new NBTTagCompound();
            try {
                e.writeToNBT(tag);
            } catch (Throwable ignored) {
                continue;
            }
            VirtualTrainSavedData.CarSnapshot car = new VirtualTrainSavedData.CarSnapshot();
            car.entityTag = tag;
            car.relX = e.posX - st.x;
            car.relY = e.posY - st.y;
            car.relZ = e.posZ - st.z;
            st.cars.add(car);
        }
        if (st.controlRoot == null) {
            for (VirtualTrainSavedData.CarSnapshot c : st.cars) {
                if (c == null || c.entityTag == null) continue;
                NBTTagCompound root = extractControlRootFromEntityTag(c.entityTag);
                if (root != null) {
                    TrainAutoPilotData.ensureDefaults(root);
                    st.controlRoot = root;
                }
                if (st.controlTrainId == null) {
                    UUID u = extractEntityUuid(c.entityTag);
                    if (u != null) {
                        st.controlTrainId = u;
                    }
                }
                if (st.controlRoot != null) {
                    break;
                }
            }
        }

        for (Entity e : info.entities) {
            if (e == null) continue;
            // Mark dead first — if this fails the entity stays alive, which is safe.
            try {
                e.setDead();
            } catch (Throwable ignored) {
                continue; // can't even mark dead, skip entirely
            }
            // Atomic removal: try removeEntity first (safer, calls setDead internally
            // but we already called it above). Fall back to removeEntityDangerously.
            boolean removed = false;
            try {
                world.removeEntity(e);
                removed = true;
            } catch (Throwable ignored) {
            }
            if (!removed) {
                try {
                    world.removeEntityDangerously(e);
                    removed = true;
                } catch (Throwable ignored) {
                }
            }
            if (!removed) {
                // Entity is marked dead but still in loadedEntityList. This is
                // self-healing: Minecraft removes dead entities during its update loop.
            }
        }

        return st.cars.isEmpty() ? null : st;
    }

    private void advanceAlongGraph(WorldServer world, VirtualTrainSavedData.VirtualTrainState st, TrackGraphSavedData graph, int dim, Route route, int tickDelta) {
        if (st == null) return;
        double speedMps = Math.max(0.0, st.speedKmh / 3.6);
        double dist = (speedMps / 20.0) * Math.max(1, tickDelta);
        if (dist <= 1e-6) return;
        if (st.currentKey == 0L) return;

        if (st.nextKey == 0L || st.edgeLength <= 1e-6) {
            long nk = chooseNextKeyForRoute(world, route, graph, dim, st.currentKey, st.prevKey, st.dirX, st.dirZ, st.pos());
            st.nextKey = nk;
            st.edgeLength = getEdgeLength(graph, dim, st.currentKey, st.nextKey);
            st.edgeProgress = 0.0;
            if (st.nextKey == 0L || st.edgeLength <= 1e-6) {
                st.speedKmh = 0.0;
                return;
            }
        }
        st.edgeProgress += dist;
        while (st.edgeProgress >= st.edgeLength - 1e-6 && st.edgeLength > 1e-6) {
            st.edgeProgress -= st.edgeLength;
            addRecentKey(st, st.currentKey);
            st.prevKey = st.currentKey;
            st.currentKey = st.nextKey;
            long nk = chooseNextKeyForRoute(world, route, graph, dim, st.currentKey, st.prevKey, st.dirX, st.dirZ, st.pos());
            st.nextKey = nk;
            st.edgeLength = getEdgeLength(graph, dim, st.currentKey, st.nextKey);
            if (st.nextKey == 0L || st.edgeLength <= 1e-6) {
                st.edgeProgress = 0.0;
                st.speedKmh = 0.0;
                break;
            }
        }

        // Use TrackAPI to get the correct curve position instead of linear interpolation
        BlockPos curBp = TrackGraphSavedData.toPos(st.currentKey);
        Vec3d origin = new Vec3d(curBp.getX() + 0.5, curBp.getY() + 0.5, curBp.getZ() + 0.5);

        // Step forward along the curve from current key
        double moveDist = st.edgeProgress;
        double moved = 0.0;
        Vec3d p = origin;
        int maxSteps = 200;
        for (int i = 0; i < maxSteps && moved < moveDist; i++) {
            ITrack track = null;
            try { track = Util.getTileEntity(world, p, false); } catch (Throwable ignored) {}
            if (track == null) break;

            double stepSize = Math.min(0.25, moveDist - moved);
            Vec3d motion = new Vec3d(st.dirX, 0.0, st.dirZ);
            double mLen = Math.sqrt(st.dirX * st.dirX + st.dirZ * st.dirZ);
            if (mLen >= 1e-6) motion = new Vec3d(st.dirX / mLen, 0.0, st.dirZ / mLen);

            Vec3d next = null;
            try { next = track.getNextPosition(p, motion); } catch (Throwable ignored) {}
            if (next == null || next.squareDistanceTo(p) < 1e-10) break;

            double stepDist = next.distanceTo(p);
            if (!Double.isFinite(stepDist) || stepDist < 1e-6) break;

            moved += stepDist;
            p = next;
            // Movement updates direction for next iteration via motion derived from edge graph
        }

        // Fall back to linear interpolation if track trace fails
        if (moved < moveDist) {
            BlockPos bp = TrackGraphSavedData.toPos(st.nextKey == 0L ? st.currentKey : st.nextKey);
            Vec3d pb = new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
            Vec3d d = pb.subtract(origin);
            double len = d.lengthVector();
            if (len >= 1e-6 && Double.isFinite(len)) {
                double t = st.edgeLength <= 1e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, st.edgeProgress / st.edgeLength));
                st.x = origin.x + d.x * t;
                st.y = origin.y + d.y * t;
                st.z = origin.z + d.z * t;
                st.dirX = d.x / len;
                st.dirZ = d.z / len;
            }
        } else {
            st.x = p.x;
            st.y = p.y;
            st.z = p.z;
        }
    }

    private double getEdgeLength(TrackGraphSavedData graph, int dim, long fromKey, long toKey) {
        if (graph == null || fromKey == 0L || toKey == 0L) {
            return 0.0;
        }
        Double len = graph.getOutgoing(dim, fromKey).get(toKey);
        if (len == null) {
            return 0.0;
        }
        return Double.isFinite(len) ? len : 0.0;
    }

    private long chooseNextKey(WorldServer world, TrackGraphSavedData graph, int dim, long currentKey, long prevKey, double dirX, double dirZ, Vec3d pos) {
        if (graph == null || currentKey == 0L) {
            return 0L;
        }
        Map<Long, Double> outs = graph.getOutgoing(dim, currentKey);
        if (outs.isEmpty()) {
            return 0L;
        }
        int candidates = 0;
        for (Long to : outs.keySet()) {
            if (to == null) continue;
            long tk = to;
            if (tk == 0L || tk == prevKey) continue;
            candidates++;
            if (candidates > 1) break;
        }
        if (candidates > 1) {
            long physical = choosePhysicalNextKey(world, currentKey, prevKey, dirX, dirZ, pos);
            if (physical != 0L && outs.containsKey(physical)) {
                return physical;
            }
        }
        BlockPos cur = TrackGraphSavedData.toPos(currentKey);
        double bestScore = Double.NEGATIVE_INFINITY;
        long best = 0L;
        for (Long to : outs.keySet()) {
            if (to == null) continue;
            long tk = to;
            if (tk == 0L) continue;
            if (tk == prevKey) continue;
            BlockPos tp = TrackGraphSavedData.toPos(tk);
            double vx = (tp.getX() + 0.5) - (cur.getX() + 0.5);
            double vz = (tp.getZ() + 0.5) - (cur.getZ() + 0.5);
            double vLen = Math.sqrt(vx * vx + vz * vz);
            if (vLen < 1e-6) continue;
            double nx = vx / vLen;
            double nz = vz / vLen;
            double score = nx * dirX + nz * dirZ;
            if (score > bestScore) {
                bestScore = score;
                best = tk;
            }
        }
        if (best != 0L) {
            return best;
        }
        for (Long to : outs.keySet()) {
            if (to == null) continue;
            long tk = to;
            if (tk == 0L) continue;
            if (tk == prevKey) continue;
            return tk;
        }
        return 0L;
    }

    private long choosePhysicalNextKey(WorldServer world, long currentKey, long prevKey, double dirX, double dirZ, Vec3d pos) {
        if (world == null || world.isRemote || currentKey == 0L) {
            return 0L;
        }
        BlockPos cur = TrackGraphSavedData.toPos(currentKey);
        if (!world.isBlockLoaded(cur)) {
            return 0L;
        }
        Vec3d p = pos == null ? new Vec3d(cur.getX() + 0.5, cur.getY() + 0.5, cur.getZ() + 0.5) : pos;
        double mag = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (!Double.isFinite(mag) || mag < 1e-6) {
            return 0L;
        }
        Vec3d motion = new Vec3d(dirX / mag, 0.0, dirZ / mag);
        ITrack track;
        try {
            track = Util.getTileEntity(world, p, false);
        } catch (Throwable ignored) {
            track = null;
        }
        if (track == null) {
            return 0L;
        }
        Vec3d next;
        try {
            next = track.getNextPosition(p, motion);
        } catch (Throwable ignored) {
            next = null;
        }
        if (next == null || !Double.isFinite(next.x) || !Double.isFinite(next.y) || !Double.isFinite(next.z)) {
            return 0L;
        }
        Long k = getLogicalRailKeyAt(world, next);
        if (k == null) {
            return 0L;
        }
        long nk = k;
        if (nk == 0L || nk == currentKey || nk == prevKey) {
            return 0L;
        }
        return nk;
    }

    private boolean isAnyPlayerNear(WorldServer world, Vec3d pos, int rangeBlocks) {
        if (world == null || pos == null) {
            return false;
        }
        double r = Math.max(1.0, rangeBlocks);
        double r2 = r * r;
        for (EntityPlayer p : world.playerEntities) {
            if (p == null || p.isDead) continue;
            double dx = p.posX - pos.x;
            double dy = p.posY - pos.y;
            double dz = p.posZ - pos.z;
            if ((dx * dx + dy * dy + dz * dz) <= r2) {
                return true;
            }
        }
        return false;
    }

    private ConsistInfo buildConsistInfo(WorldServer world, List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        Entity lead = null;
        double bestSpeed = 0.0;
        for (Entity e : entities) {
            if (e == null) continue;
            if (lead == null) {
                lead = e;
            } else if (!IrTrainReflection.isLocomotive(lead) && IrTrainReflection.isLocomotive(e)) {
                lead = e;
            }
            double v = 0.0;
            try {
                v = IrTrainReflection.getSpeedKmh(e);
            } catch (Throwable ignored) {
                v = 0.0;
            }
            if (Double.isFinite(v) && v > bestSpeed) {
                bestSpeed = v;
            }
        }
        Vec3d center = averagePosition(entities);
        if (center == null) {
            return null;
        }
        Vec3d dir = estimateDirection(lead);
        ConsistInfo info = new ConsistInfo();
        info.groupId = IrTrainReflection.getConsistGroupKey(lead);
        info.entities = entities;
        info.center = center;
        info.dirX = dir.x;
        info.dirZ = dir.z;
        info.speedKmh = bestSpeed;
        return info;
    }

    private Vec3d averagePosition(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        int c = 0;
        for (Entity e : entities) {
            if (e == null) continue;
            sx += e.posX;
            sy += e.posY;
            sz += e.posZ;
            c++;
        }
        if (c <= 0) {
            return null;
        }
        return new Vec3d(sx / c, sy / c, sz / c);
    }

    private Vec3d estimateDirection(Entity lead) {
        if (lead == null) {
            return new Vec3d(1.0, 0.0, 0.0);
        }
        double dx = lead.motionX;
        double dz = lead.motionZ;
        double mag = Math.sqrt(dx * dx + dz * dz);
        if (mag < 1e-6) {
            dx = lead.posX - lead.prevPosX;
            dz = lead.posZ - lead.prevPosZ;
            mag = Math.sqrt(dx * dx + dz * dz);
        }
        if (mag < 1e-6) {
            double yawRad = Math.toRadians(lead.rotationYaw);
            dx = -Math.sin(yawRad);
            dz = Math.cos(yawRad);
            mag = Math.sqrt(dx * dx + dz * dz);
        }
        if (mag < 1e-6 || !Double.isFinite(mag)) {
            return new Vec3d(1.0, 0.0, 0.0);
        }
        return new Vec3d(dx / mag, 0.0, dz / mag);
    }

    private static class ConsistGroup {
        final UUID groupKey;
        final List<Entity> entities = new ArrayList<>();

        ConsistGroup(UUID groupKey) {
            this.groupKey = groupKey;
        }
    }

    private static class ConsistInfo {
        UUID groupId;
        List<Entity> entities;
        Vec3d center;
        double dirX;
        double dirZ;
        double speedKmh;
    }

    private static List<Entity> snapshotLoadedEntities(World world) {
        if (world == null || world.loadedEntityList == null || world.loadedEntityList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Object[] snapshot;
        try {
            snapshot = world.loadedEntityList.toArray();
        } catch (Throwable ignored) {
            return java.util.Collections.emptyList();
        }
        List<Entity> out = new ArrayList<>(snapshot.length);
        for (Object o : snapshot) {
            if (o instanceof Entity) {
                out.add((Entity) o);
            }
        }
        return out;
    }


    private static Set<UUID> getConsistIds(Entity entity) {
        if (entity == null) {
            return null;
        }
        Object self = IrTrainReflection.invoke(entity, "getSelf");
        if (self == null) {
            return null;
        }
        Object consist = IrTrainReflection.getFieldValue(self, "consist");
        if (consist == null) {
            return null;
        }
        Object ids = IrTrainReflection.getFieldValue(consist, "ids");
        if (!(ids instanceof List)) {
            return null;
        }
        Set<UUID> out = new HashSet<>();
        for (Object o : (List<?>) ids) {
            if (o instanceof UUID) {
                out.add((UUID) o);
            }
        }
        return out.isEmpty() ? null : out;
    }


    private static Long getLogicalRailKeyAt(World world, Vec3d p) {
        if (world == null || p == null) {
            return null;
        }
        trackapi.lib.ITrack track;
        try {
            track = trackapi.lib.Util.getTileEntity(world, p, false);
        } catch (Throwable ignored) {
            track = null;
        }
        return IrTrainReflection.getLogicalRailKey(track, world, p);
    }


    private static class StopPlan {
        boolean active;
        long targetKey;
        double maxSpeedKmh;
        double stopDistance;
        int waitTicks;
    }

    private static class Route {
        final long goalKey;
        final List<Long> nodes;
        final Map<Long, Integer> index;

        Route(long goalKey, List<Long> nodes) {
            this.goalKey = goalKey;
            this.nodes = nodes == null ? new ArrayList<>() : nodes;
            this.index = new HashMap<>();
            for (int i = 0; i < this.nodes.size(); i++) {
                Long v = this.nodes.get(i);
                if (v == null || v == 0L) continue;
                this.index.putIfAbsent(v, i);
            }
        }

        int indexOf(long key) {
            Integer i = index.get(key);
            return i == null ? -1 : i;
        }
    }

    private static class SignalIndex {
        final Map<Long, Set<Long>> keyToSegments = new HashMap<>();
    }

    private static class SignalIndexCache {
        long lastTick = Long.MIN_VALUE;
        SignalIndex index;
    }

    private static class LoadedKeysCache {
        long lastTick = Long.MIN_VALUE;
        Set<Long> keys;
    }

    private static class SegmentOccCache {
        long lastTick = Long.MIN_VALUE;
        Map<Long, Integer> occ;
    }

    private SignalIndex buildSignalIndex(Map<Long, Set<Long>> segments) {
        SignalIndex idx = new SignalIndex();
        if (segments == null || segments.isEmpty()) {
            return idx;
        }
        if (VirtualTrainConfig.maxSignalsToCheck == 0) {
            return idx;
        }
        int count = 0;
        for (Map.Entry<Long, Set<Long>> e : segments.entrySet()) {
            if (e == null) continue;
            Long segId = e.getKey();
            Set<Long> keys = e.getValue();
            if (segId == null || keys == null || keys.isEmpty()) continue;
            count++;
            if (VirtualTrainConfig.maxSignalsToCheck > 0 && count > VirtualTrainConfig.maxSignalsToCheck) {
                break;
            }
            for (Long k : keys) {
                if (k == null || k == 0L) continue;
                idx.keyToSegments.computeIfAbsent(k, __ -> new HashSet<>()).add(segId);
            }
        }
        return idx;
    }

    private Map<Long, Integer> computeSegmentOccupancy(VirtualTrainSavedData data, int dim, SignalIndex index, Set<Long> loadedTrainKeys) {
        Map<Long, Integer> out = new HashMap<>();
        if (index == null || index.keyToSegments.isEmpty()) {
            return out;
        }
        if (data != null) {
            for (VirtualTrainSavedData.VirtualTrainState st : data.byDimension(dim)) {
                if (st == null) continue;
                long k = st.currentKey;
                if (k == 0L) continue;
                Set<Long> segs = index.keyToSegments.get(k);
                if (segs == null || segs.isEmpty()) continue;
                for (Long seg : segs) {
                    if (seg == null) continue;
                    out.put(seg, out.getOrDefault(seg, 0) + 1);
                }
            }
        }
        if (loadedTrainKeys != null && !loadedTrainKeys.isEmpty()) {
            for (Long k : loadedTrainKeys) {
                if (k == null || k == 0L) continue;
                Set<Long> segs = index.keyToSegments.get(k);
                if (segs == null || segs.isEmpty()) continue;
                for (Long seg : segs) {
                    if (seg == null) continue;
                    out.put(seg, out.getOrDefault(seg, 0) + 1);
                }
            }
        }
        return out;
    }

    private Set<Long> collectLoadedTrainKeys(WorldServer world, SignalIndex index) {
        Set<Long> out = new HashSet<>();
        if (world == null || index == null || index.keyToSegments.isEmpty()) {
            return out;
        }
        for (int i = 0; i < world.loadedEntityList.size(); i++) {
            Entity e = world.loadedEntityList.get(i);
            if (e == null) continue;
            boolean isTrain;
            try {
                isTrain = IrTrainReflection.isIrTrainEntity(e);
            } catch (Throwable ignored) {
                isTrain = false;
            }
            if (!isTrain) continue;
            Long k0 = getLogicalRailKeyAt(world, new Vec3d(e.posX, e.posY, e.posZ));
            if (k0 != null && index.keyToSegments.containsKey(k0)) out.add(k0);
            Long k1 = getLogicalRailKeyAt(world, new Vec3d(e.posX, e.posY - 1.0, e.posZ));
            if (k1 != null && index.keyToSegments.containsKey(k1)) out.add(k1);
        }
        return out;
    }

    private StopPlan updateVirtualTimetable(VirtualTrainSavedData.VirtualTrainState st, TrackGraphSavedData graph, int dim, long now) {
        if (st == null || st.controlRoot == null) {
            return null;
        }
        TrainAutoPilotData.ensureDefaults(st.controlRoot);
        if (!st.controlRoot.getBoolean(TrainAutoPilotData.ENABLED)) {
            return null;
        }
        NBTTagList stops = st.controlRoot.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            st.controlRoot.setBoolean(TrainAutoPilotData.ENABLED, false);
            return null;
        }
        int index = Math.max(0, Math.min(stops.tagCount() - 1, st.controlRoot.getInteger(TrainAutoPilotData.CURRENT_INDEX)));
        st.controlRoot.setInteger(TrainAutoPilotData.CURRENT_INDEX, index);
        int trainState = st.controlRoot.getInteger(TrainAutoPilotData.TRAIN_STATE);
        if (trainState != 0) {
            long dep = st.controlRoot.getLong(TrainAutoPilotData.NEXT_DEPARTURE_TICK);
            if (now >= dep) {
                int next = (index + 1) % stops.tagCount();
                st.controlRoot.setInteger(TrainAutoPilotData.CURRENT_INDEX, next);
                st.controlRoot.setInteger(TrainAutoPilotData.TRAIN_STATE, 0);
                st.controlRoot.setInteger(TrainAutoPilotData.DWELL, 0);
                st.controlRoot.setLong(TrainAutoPilotData.NEXT_DEPARTURE_TICK, 0L);
            } else {
                StopPlan p = new StopPlan();
                p.active = false;
                return p;
            }
        }
        NBTTagCompound stop = stops.getCompoundTagAt(st.controlRoot.getInteger(TrainAutoPilotData.CURRENT_INDEX));
        BlockPos target = new BlockPos(stop.getInteger("x"), stop.getInteger("y"), stop.getInteger("z"));
        double maxSpeed = stop.hasKey("limit", Constants.NBT.TAG_FLOAT) ? stop.getFloat("limit") : 45.0f;
        maxSpeed = Math.max(1.0, maxSpeed);
        int waitSec = stop.hasKey("wait", Constants.NBT.TAG_INT) ? stop.getInteger("wait") : 0;
        waitSec = Math.max(0, Math.min(3600, waitSec));
        long targetKey = target.toLong();
        StopPlan plan = new StopPlan();
        plan.active = true;
        plan.targetKey = targetKey;
        plan.maxSpeedKmh = maxSpeed;
        plan.stopDistance = 20.0;
        plan.waitTicks = waitSec * 20;
        return plan;
    }

    private void enterVirtualDwell(VirtualTrainSavedData.VirtualTrainState st, long now, int waitTicks) {
        if (st == null || st.controlRoot == null) {
            return;
        }
        int wt = Math.max(0, waitTicks);
        st.controlRoot.setInteger(TrainAutoPilotData.TRAIN_STATE, 1);
        st.controlRoot.setInteger(TrainAutoPilotData.DWELL, wt);
        st.controlRoot.setLong(TrainAutoPilotData.NEXT_DEPARTURE_TICK, now + wt);
        st.speedKmh = 0.0;
    }

    private Route getOrBuildRoute(VirtualTrainSavedData.VirtualTrainState st, TrackGraphSavedData graph, int dim, long goalKey) {
        if (st == null || st.id == null || graph == null) {
            return null;
        }
        if (st.currentKey == 0L || goalKey == 0L) {
            return null;
        }
        Route existing = routes.get(st.id);
        if (existing != null && existing.goalKey == goalKey) {
            int idx = existing.indexOf(st.currentKey);
            if (idx >= 0) {
                return existing;
            }
        }
        List<Long> nodes = findRouteDijkstra(graph, dim, st.currentKey, goalKey, VirtualTrainConfig.maxRouteNodes);
        if (nodes == null || nodes.isEmpty()) {
            routes.remove(st.id);
            return null;
        }
        Route r = new Route(goalKey, nodes);
        routes.put(st.id, r);
        return r;
    }

    private List<Long> findRouteDijkstra(TrackGraphSavedData graph, int dim, long startKey, long goalKey, int maxNodes) {
        if (graph == null || startKey == 0L || goalKey == 0L) {
            return null;
        }
        if (startKey == goalKey) {
            List<Long> out = new ArrayList<>();
            out.add(startKey);
            return out;
        }
        int limit = Math.max(500, maxNodes);
        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Long> prev = new HashMap<>();
        PriorityQueue<NodeCost> pq = new PriorityQueue<>();
        dist.put(startKey, 0.0);
        pq.add(new NodeCost(startKey, 0.0));
        int expanded = 0;
        while (!pq.isEmpty() && expanded < limit) {
            NodeCost nc = pq.poll();
            if (nc == null) continue;
            long cur = nc.key;
            double d = nc.cost;
            Double best = dist.get(cur);
            if (best == null || d > best + 1e-9) {
                continue;
            }
            expanded++;
            if (cur == goalKey) {
                break;
            }
            Map<Long, Double> outs = graph.getOutgoing(dim, cur);
            if (outs == null || outs.isEmpty()) {
                continue;
            }
            for (Map.Entry<Long, Double> e : outs.entrySet()) {
                if (e == null) continue;
                Long toObj = e.getKey();
                Double lenObj = e.getValue();
                if (toObj == null || lenObj == null) continue;
                long to = toObj;
                double len = lenObj;
                if (to == 0L || !Double.isFinite(len) || len <= 0.0) continue;
                double nd = d + len;
                Double curBest = dist.get(to);
                if (curBest == null || nd + 1e-9 < curBest) {
                    dist.put(to, nd);
                    prev.put(to, cur);
                    pq.add(new NodeCost(to, nd));
                }
            }
        }
        if (!dist.containsKey(goalKey)) {
            return null;
        }
        List<Long> rev = new ArrayList<>();
        long cur = goalKey;
        int guard = 0;
        while (cur != startKey && guard < limit) {
            rev.add(cur);
            Long p = prev.get(cur);
            if (p == null) {
                return null;
            }
            cur = p;
            guard++;
        }
        rev.add(startKey);
        List<Long> out = new ArrayList<>(rev.size());
        for (int i = rev.size() - 1; i >= 0; i--) {
            out.add(rev.get(i));
        }
        return out;
    }

    private static class NodeCost implements Comparable<NodeCost> {
        final long key;
        final double cost;

        NodeCost(long key, double cost) {
            this.key = key;
            this.cost = cost;
        }

        @Override
        public int compareTo(NodeCost o) {
            if (o == null) return -1;
            return Double.compare(this.cost, o.cost);
        }
    }

    private long chooseNextKeyForRoute(WorldServer world, Route route, TrackGraphSavedData graph, int dim, long currentKey, long prevKey, double dirX, double dirZ, Vec3d pos) {
        if (route != null && route.nodes != null && !route.nodes.isEmpty()) {
            int idx = route.indexOf(currentKey);
            if (idx >= 0) {
                for (int i = idx + 1; i < route.nodes.size(); i++) {
                    Long nk = route.nodes.get(i);
                    if (nk == null || nk == 0L) continue;
                    if (nk == prevKey) continue;
                    return nk;
                }
            }
        }
        return chooseNextKey(world, graph, dim, currentKey, prevKey, dirX, dirZ, pos);
    }

    private double distanceAlongRouteToKey(VirtualTrainSavedData.VirtualTrainState st, Route route, TrackGraphSavedData graph, int dim, long targetKey) {
        if (st == null || route == null || graph == null || targetKey == 0L) {
            return Double.NaN;
        }
        int idx = route.indexOf(st.currentKey);
        if (idx < 0) {
            return Double.NaN;
        }
        if (st.currentKey == targetKey) {
            return 0.0;
        }
        int targetIdx = route.indexOf(targetKey);
        if (targetIdx < 0 || targetIdx <= idx) {
            return Double.NaN;
        }
        double dist = 0.0;

        if (idx + 1 >= route.nodes.size()) {
            return Double.NaN;
        }
        long firstNext = route.nodes.get(idx + 1);
        double firstLen = getEdgeLength(graph, dim, st.currentKey, firstNext);
        if (!Double.isFinite(firstLen) || firstLen <= 0.0) {
            return Double.NaN;
        }
        if (st.nextKey == firstNext && st.edgeLength > 1e-6) {
            dist += Math.max(0.0, st.edgeLength - st.edgeProgress);
        } else {
            dist += firstLen;
        }

        for (int i = idx + 2; i <= targetIdx; i++) {
            long from = route.nodes.get(i - 1);
            long to = route.nodes.get(i);
            double len = getEdgeLength(graph, dim, from, to);
            if (!Double.isFinite(len) || len <= 0.0) {
                return Double.NaN;
            }
            dist += len;
        }
        return Math.max(0.0, dist);
    }

    private double computeApproachSpeedLimitKmh(double distToTarget, double stopDistance, double maxSpeedKmh) {
        double d = Math.max(0.0, distToTarget - stopDistance);
        double a = Math.max(0.1, VIRTUAL_FULL_BRAKE_DECEL_MPS2);
        double vMps = Math.sqrt(2.0 * a * d);
        double vKmh = vMps * 3.6;
        return Math.max(0.0, Math.min(maxSpeedKmh, vKmh));
    }

    private double computeSignalSpeedLimitKmh(VirtualTrainSavedData.VirtualTrainState st, Route route, TrackGraphSavedData graph, int dim, Map<Long, Set<Long>> segments, SignalIndex index, Map<Long, Integer> occupancy) {
        if (st == null || index == null || index.keyToSegments.isEmpty() || occupancy == null || occupancy.isEmpty()) {
            return Double.NaN;
        }
        long entryKey = 0L;
        long entrySeg = 0L;
        double distToEntry = Double.NaN;
        if (route != null && route.nodes != null && !route.nodes.isEmpty()) {
            int idx = route.indexOf(st.currentKey);
            if (idx >= 0) {
                double dist = 0.0;
                dist += Math.max(0.0, st.edgeLength - st.edgeProgress);
                for (int i = idx + 1; i < route.nodes.size() && i < idx + 256; i++) {
                    long k = route.nodes.get(i);
                    Set<Long> segs = index.keyToSegments.get(k);
                    if (segs != null && !segs.isEmpty()) {
                        entryKey = k;
                        entrySeg = segs.iterator().next();
                        distToEntry = dist;
                        break;
                    }
                    long prev = route.nodes.get(i - 1);
                    double len = getEdgeLength(graph, dim, prev, k);
                    if (!Double.isFinite(len) || len <= 0.0) {
                        break;
                    }
                    dist += len;
                }
            }
        } else {
            long k = st.nextKey == 0L ? st.currentKey : st.nextKey;
            Set<Long> segs = index.keyToSegments.get(k);
            if (segs != null && !segs.isEmpty()) {
                entryKey = k;
                entrySeg = segs.iterator().next();
                distToEntry = Math.max(0.0, st.edgeLength - st.edgeProgress);
            }
        }
        if (entrySeg == 0L || !Double.isFinite(distToEntry)) {
            return Double.NaN;
        }
        int occ = occupancy.getOrDefault(entrySeg, 0);
        boolean selfInside = false;
        Set<Long> segKeys = segments == null ? null : segments.get(entrySeg);
        if (segKeys != null && !segKeys.isEmpty() && segKeys.contains(st.currentKey)) {
            selfInside = true;
        }
        if (selfInside) {
            occ = Math.max(0, occ - 1);
        }
        if (occ <= 0) {
            return Double.NaN;
        }
        if (distToEntry <= 20.0) {
            return 0.0;
        }
        return computeApproachSpeedLimitKmh(distToEntry, 20.0, 800.0);
    }

    private double moveSpeedTowards(double currentKmh, double desiredKmh, int tickDelta) {
        double cur = Math.max(0.0, currentKmh);
        double des = Math.max(0.0, desiredKmh);
        int dt = Math.max(1, tickDelta);
        double accelKmhPerTick = (VIRTUAL_ACCEL_MPS2 * 3.6) / 20.0;
        double decelKmhPerTick = (VIRTUAL_FULL_BRAKE_DECEL_MPS2 * 3.6) / 20.0;
        double accel = accelKmhPerTick * dt;
        double decel = decelKmhPerTick * dt;
        if (des > cur + 1e-6) {
            return Math.min(des, cur + accel);
        }
        if (des < cur - 1e-6) {
            return Math.max(des, cur - decel);
        }
        return des;
    }

    private static void addRecentKey(VirtualTrainSavedData.VirtualTrainState st, long key) {
        if (st == null || key == 0L) {
            return;
        }
        if (st.recentKeys == null) {
            st.recentKeys = new ArrayList<>();
        }
        if (!st.recentKeys.isEmpty()) {
            Long last = st.recentKeys.get(st.recentKeys.size() - 1);
            if (last != null && last == key) {
                return;
            }
        }
        st.recentKeys.add(key);
        while (st.recentKeys.size() > RECENT_KEYS_MAX) {
            st.recentKeys.remove(0);
        }
    }

    private static NBTTagCompound extractControlRootFromEntityTag(NBTTagCompound entityTag) {
        if (entityTag == null) {
            return null;
        }
        if (entityTag.hasKey(TrainAutoPilotData.ROOT, Constants.NBT.TAG_COMPOUND)) {
            return entityTag.getCompoundTag(TrainAutoPilotData.ROOT).copy();
        }
        if (entityTag.hasKey("ForgeData", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound fd = entityTag.getCompoundTag("ForgeData");
            if (fd.hasKey(TrainAutoPilotData.ROOT, Constants.NBT.TAG_COMPOUND)) {
                return fd.getCompoundTag(TrainAutoPilotData.ROOT).copy();
            }
        }
        return null;
    }

    private static UUID extractEntityUuid(NBTTagCompound entityTag) {
        if (entityTag == null) {
            return null;
        }
        if (entityTag.hasKey("UUIDMost") && entityTag.hasKey("UUIDLeast")) {
            long msb = entityTag.getLong("UUIDMost");
            long lsb = entityTag.getLong("UUIDLeast");
            if (msb != 0L || lsb != 0L) {
                return new UUID(msb, lsb);
            }
        }
        return null;
    }
}

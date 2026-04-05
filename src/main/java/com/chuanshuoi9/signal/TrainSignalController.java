package com.chuanshuoi9.signal;

import com.chuanshuoi9.tile.TileTrainSignal;
import com.chuanshuoi9.train.IrTrainReflection;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import trackapi.lib.ITrack;
import trackapi.lib.Util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TrainSignalController {
    private static final double SIGNAL_RANGE = 1000.0;
    private static final double STOP_DISTANCE = 20.0;
    private static final double SLOW_SPEED_KMH = 15.0;
    private static final double APPROACH_ALIGN_DOT = 0.2;
    private static final double ALLOW_ALIGN_DOT = 0.5;
    private static final double CLEAR_TIME_BUFFER_SEC = 2.0;
    private static final double MIN_SPEED_MPS = 0.1;
    private static final double APPROACH_TRAIN_RANGE = 800.0;

    private static final int SEGMENT_CACHE_MAX = 512;
    private static final LinkedHashMap<SegmentCacheKey, SegmentCacheValue> SEGMENT_CACHE =
        new LinkedHashMap<SegmentCacheKey, SegmentCacheValue>(SEGMENT_CACHE_MAX, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<SegmentCacheKey, SegmentCacheValue> eldest) {
                return size() > SEGMENT_CACHE_MAX;
            }
        };

    public static SignalCommand computeOverride(Entity train, BlockPos target, double speedKmh, double estFullBrakeDecelMps2, double maxSpeedKmh) {
        try {
            if (train == null || train.world == null || train.world.isRemote || target == null) {
                return null;
            }
            if (!isFinite(train.posX) || !isFinite(train.posY) || !isFinite(train.posZ)) {
                return null;
            }
            World world = train.world;

            double moveX = target.getX() + 0.5 - train.posX;
            double moveZ = target.getZ() + 0.5 - train.posZ;
            double moveLen = Math.sqrt(moveX * moveX + moveZ * moveZ);
            if (moveLen < 1e-6 || !isFinite(moveLen)) {
                return null;
            }
            double dirX = moveX / moveLen;
            double dirZ = moveZ / moveLen;

            TileTrainSignal best = null;
            BlockPos bestStop = null;
            double bestDist = Double.POSITIVE_INFINITY;
            boolean bestAllowed = true;
            Map<Long, RailTraceInfo> forwardRailTrace = traceForwardRailTrace(world, new Vec3d(train.posX, train.posY, train.posZ), dirX, dirZ, SIGNAL_RANGE);
            List<SignalCandidate> candidates = new ArrayList<>();
            Map<SegmentSideKey, Double> segmentMaxDot = new HashMap<>();
            int dim = world.provider == null ? 0 : world.provider.getDimension();

            for (TileEntity te : snapshotLoadedTileEntities(world)) {
                if (!(te instanceof TileTrainSignal)) {
                    continue;
                }
                TileTrainSignal signal = (TileTrainSignal) te;
                BlockPos a = signal.getRailA();
                BlockPos b = signal.getRailB();
                if (a == null || b == null) {
                    continue;
                }
                Long keyA = getLogicalRailKeyAt(world, a);
                Long keyB = getLogicalRailKeyAt(world, b);
                if (keyA == null || keyB == null) {
                    continue;
                }
                RailTraceInfo ta = forwardRailTrace.get(keyA);
                RailTraceInfo tb = forwardRailTrace.get(keyB);
                Double da = ta == null ? null : ta.dist;
                Double db = tb == null ? null : tb.dist;
                if (da == null && db == null) {
                    continue;
                }
                BlockPos stop;
                double dist;
                long entryKey;
                if (da != null && (db == null || da <= db)) {
                    stop = a;
                    dist = da;
                    entryKey = keyA;
                } else {
                    stop = b;
                    dist = db;
                    entryKey = keyB;
                }
                Vec3d motion = null;
                RailTraceInfo entryTrace = forwardRailTrace.get(entryKey);
                if (entryTrace != null) {
                    motion = entryTrace.dir;
                }
                double localDirX = dirX;
                double localDirZ = dirZ;
                if (motion != null) {
                    double mLen = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
                    if (mLen >= 1e-6) {
                        localDirX = motion.x / mLen;
                        localDirZ = motion.z / mLen;
                    }
                }
                double allowDot = dotFacing(signal.getFacing(), localDirX, localDirZ);
                SegmentSideKey segKey = new SegmentSideKey(dim, keyA, keyB, entryKey);
                Double prev = segmentMaxDot.get(segKey);
                if (prev == null || allowDot > prev) {
                    segmentMaxDot.put(segKey, allowDot);
                }
                candidates.add(new SignalCandidate(signal, a, b, stop, dist, segKey));
            }

            for (SignalCandidate c : candidates) {
                Double maxDot = segmentMaxDot.get(c.segmentSideKey);
                boolean allowed = maxDot != null && maxDot >= ALLOW_ALIGN_DOT;
                if (c.dist < bestDist - 1e-6 || (Math.abs(c.dist - bestDist) <= 1e-6 && allowed && !bestAllowed)) {
                    bestDist = c.dist;
                    best = c.signal;
                    bestStop = c.stop;
                    bestAllowed = allowed;
                }
            }

            if (best == null || bestStop == null) {
                return null;
            }

            BlockPos a = best.getRailA();
            BlockPos b = best.getRailB();
            if (a == null || b == null) {
                return null;
            }

            boolean blockedByDirection = !bestAllowed;
            BlockPos entry = bestStop;
            BlockPos exit = entry.equals(a) ? b : a;
            SegmentPath segmentPath = computeSegmentPath(world, entry, exit);
            if (!blockedByDirection && segmentPath.hasTurnouts() && !segmentPath.areTurnoutsAligned(world)) {
                return null;
            }
            SegmentOccupancyInfo occ = !blockedByDirection ? getSegmentOccupancyInfo(world, segmentPath, entry, exit, train) : new SegmentOccupancyInfo(0, 0.0, 0.0, false);
            boolean blockedByOccupancy = !blockedByDirection && occ.occupied;
            if (!blockedByDirection && !blockedByOccupancy) {
                return null;
            }

            if (bestDist <= STOP_DISTANCE) {
                return new SignalCommand(0.0f, 1.0f);
            }
            double maxKmh = Math.max(1.0, maxSpeedKmh);
            if (blockedByOccupancy) {
                double vSelfMps = Math.max(MIN_SPEED_MPS, speedKmh / 3.6);
                double timeToEntry = bestDist / vSelfMps;
                if (Double.isFinite(occ.clearTimeSec) && occ.clearTimeSec + CLEAR_TIME_BUFFER_SEC < timeToEntry) {
                    double desiredArrival = occ.clearTimeSec + CLEAR_TIME_BUFFER_SEC;
                    double limitKmh = (bestDist / Math.max(0.1, desiredArrival)) * 3.6;
                    limitKmh = Math.max(1.0, Math.min(maxKmh, limitKmh));
                    return new SignalCommand(limitKmh);
                }
                if (shouldForceBrakeNow(bestDist, speedKmh, estFullBrakeDecelMps2)) {
                    return new SignalCommand(0.0f, 1.0f);
                }
            }
            double speedLimit = computeApproachSpeedLimitKmh(bestDist, estFullBrakeDecelMps2, maxKmh);
            return new SignalCommand(speedLimit);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BlockPos chooseStopPoint(Entity train, double dirX, double dirZ, BlockPos a, BlockPos b) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos p : new BlockPos[] { a, b }) {
            double toX = p.getX() + 0.5 - train.posX;
            double toZ = p.getZ() + 0.5 - train.posZ;
            double len = Math.sqrt(toX * toX + toZ * toZ);
            if (len < 1e-6) continue;
            double aheadDot = (toX / len) * dirX + (toZ / len) * dirZ;
            if (aheadDot < APPROACH_ALIGN_DOT) continue;
            double distSq = p.distanceSq(train.posX, train.posY, train.posZ);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = p;
            }
        }
        return best;
    }

    private static boolean sameSegment(BlockPos a1, BlockPos b1, BlockPos a2, BlockPos b2) {
        long x1 = a1.toLong();
        long y1 = b1.toLong();
        long x2 = a2.toLong();
        long y2 = b2.toLong();
        return (x1 == x2 && y1 == y2) || (x1 == y2 && y1 == x2);
    }

    private static boolean isSegmentOccupied(World world, BlockPos entry, BlockPos exit, Entity self) {
        return isSegmentOccupied(world, computeSegmentPath(world, entry, exit), self);
    }

    private static boolean isSegmentOccupied(World world, SegmentPath segment, Entity self) {
        if (segment == null || segment.railKeys.isEmpty()) {
            return false;
        }
        Set<UUID> selfConsist = getConsistIds(self);
        for (Entity e : snapshotLoadedEntities(world)) {
            if (e == null || e == self) continue;
            if (!IrTrainReflection.isIrTrainEntity(e)) continue;
            if (isSameConsist(self, selfConsist, e)) {
                continue;
            }
            Long k = getLogicalRailKeyForEntity(world, e);
            if (k != null && segment.railKeys.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Long> getSignalRailKeys(World world, BlockPos start, BlockPos end) {
        SegmentPath p = computeSegmentPath(world, start, end);
        return p == null ? Collections.emptySet() : p.railKeys;
    }

    public static SignalSegmentStatus getSignalSegmentStatus(World world, BlockPos entry, BlockPos exit) {
        try {
            if (world == null || entry == null || exit == null) {
                return null;
            }
            SegmentPath segment = computeSegmentPath(world, entry, exit);
            if (segment == null) {
                return null;
            }
            boolean hasTurnout = segment.hasTurnouts();
            boolean aligned = !hasTurnout || segment.areTurnoutsAligned(world);
            boolean occupied = aligned && isSegmentOccupied(world, segment, null);
            return new SignalSegmentStatus(segment.railKeys.size(), hasTurnout, aligned, occupied);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static SignalSegmentReport getSignalSegmentReport(World world, BlockPos entry, BlockPos exit) {
        try {
            if (world == null || entry == null || exit == null) {
                return null;
            }
            SegmentPath segment = computeSegmentPath(world, entry, exit);
            if (segment == null) {
                return null;
            }
            boolean hasTurnout = segment.hasTurnouts();
            boolean aligned = !hasTurnout || segment.areTurnoutsAligned(world);
            SegmentOccupancyInfo occ = aligned ? getSegmentOccupancyInfo(world, segment, entry, exit, null) : new SegmentOccupancyInfo(0, 0.0, Double.POSITIVE_INFINITY, false);
            ApproachTrainInfo approach = aligned ? findApproachingTrain(world, segment, entry) : null;
            double followLimitKmh = Double.NaN;
            double followSlowToKmh = Double.NaN;
            if (aligned && occ.occupied && approach != null) {
                if (!Double.isFinite(occ.clearTimeSec) || occ.clearTimeSec < 0.0) {
                    followLimitKmh = 0.0;
                    followSlowToKmh = 0.0;
                } else {
                    double desiredArrival = occ.clearTimeSec + CLEAR_TIME_BUFFER_SEC;
                    if (desiredArrival > 0.0 && Double.isFinite(approach.distToEntry) && approach.distToEntry > 0.0) {
                        followLimitKmh = (approach.distToEntry / Math.max(0.1, desiredArrival)) * 3.6;
                        followLimitKmh = Math.max(0.0, Math.min(800.0, followLimitKmh));
                        if (Double.isFinite(approach.speedKmh) && followLimitKmh < approach.speedKmh - 0.1) {
                            followSlowToKmh = followLimitKmh;
                        }
                    }
                }
            }
            String strategy;
            if (!aligned) {
                strategy = "忽略(道岔不通)";
            } else if (!occ.occupied) {
                strategy = "通过";
            } else if (approach == null) {
                strategy = "无后车";
            } else if (Double.isFinite(occ.clearTimeSec) && occ.clearTimeSec + CLEAR_TIME_BUFFER_SEC < approach.etaSec) {
                strategy = "减速";
            } else {
                strategy = "制动";
            }
            return new SignalSegmentReport(
                segment.railKeys.size(),
                hasTurnout,
                aligned,
                occ.trainCount,
                occ.maxTrainSpeedKmh,
                occ.clearTimeSec,
                approach == null ? Double.NaN : approach.speedKmh,
                approach == null ? Double.NaN : approach.etaSec,
                followSlowToKmh,
                followLimitKmh,
                strategy,
                aligned && occ.occupied
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SegmentPath computeSegmentPath(World world, BlockPos start, BlockPos end) {
        SegmentCacheValue cached = getCachedSegmentValue(world, start, end);
        if (cached != null) {
            return new SegmentPath(new ArrayList<>(), new ArrayList<>(), cached.totalLength, cached.railKeys, cached.turnouts, cached.railKeyS, cached.entryDirX, cached.entryDirZ);
        }
        return traceSegmentPathViaTrackApi(world, start, end);
    }

    private static SegmentPath traceSegmentPathViaTrackApi(World world, BlockPos start, BlockPos end) {
        if (world == null || start == null || end == null) {
            return new SegmentPath(new ArrayList<>(), new ArrayList<>(), 0.0, new HashSet<>());
        }
        Vec3d from = new Vec3d(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
        Vec3d to = new Vec3d(end.getX() + 0.5, end.getY() + 0.5, end.getZ() + 0.5);
        SegmentPath forward = traceOneDirection(world, from, to);
        if (!forward.points.isEmpty()) {
            putCachedSegmentValue(world, start, end, forward);
            return forward;
        }
        SegmentPath backward = traceOneDirection(world, to, from);
        if (backward.points.isEmpty()) {
            return backward;
        }
        List<Vec3d> rev = new ArrayList<>(backward.points);
        Collections.reverse(rev);
        Map<Long, Double> reversedS = reverseRailKeyS(backward.railKeyS, backward.totalLength);
        SegmentPath out = buildSegmentPathFromPoints(rev, backward.railKeys, backward.turnouts, reversedS);
        putCachedSegmentValue(world, start, end, out);
        return out;
    }

    private static SegmentPath traceOneDirection(World world, Vec3d from, Vec3d to) {
        Vec3d dir = to.subtract(from);
        double dLen = dir.lengthVector();
        if (dLen < 1e-6) {
            return new SegmentPath(new ArrayList<>(), new ArrayList<>(), 0.0, new HashSet<>());
        }
        Vec3d motion = dir.scale(1.0 / dLen);

        List<Vec3d> points = new ArrayList<>();
        List<Double> s = new ArrayList<>();
        Set<Long> railKeys = new HashSet<>();
        Set<SwitchCheck> turnouts = new HashSet<>();
        Map<Long, Double> railKeyS = new HashMap<>();
        boolean entryDirSet = false;
        double entryDirX = 0.0;
        double entryDirZ = 0.0;
        points.add(from);
        s.add(0.0);
        Vec3d lastAdded = from;
        double total = 0.0;

        Vec3d p = from;
        int maxSteps = 20000;
        double stopDistSq = 1.0;
        for (int i = 0; i < maxSteps; i++) {
            ITrack track;
            try {
                track = Util.getTileEntity(world, p, false);
            } catch (Throwable ignored) {
                track = null;
            }
            if (track == null) {
                return new SegmentPath(new ArrayList<>(), new ArrayList<>(), 0.0, new HashSet<>());
            }
            Long k = getLogicalRailKey(track, world, p);
            if (k != null) {
                railKeys.add(k);
                railKeyS.putIfAbsent(k, total);
            }
            SwitchCheck sc = getTurnoutSwitchCheck(track);
            if (sc != null) {
                turnouts.add(sc);
            }
            Vec3d next;
            try {
                next = track.getNextPosition(p, motion);
            } catch (Throwable ignored) {
                next = null;
            }
            if (next == null || next.squareDistanceTo(p) < 1e-10) {
                return new SegmentPath(new ArrayList<>(), new ArrayList<>(), 0.0, new HashSet<>());
            }
            double stepDist = next.distanceTo(p);
            total += stepDist;
            if (next.squareDistanceTo(lastAdded) >= 0.25) {
                points.add(next);
                s.add(total);
                lastAdded = next;
            }
            if (next.squareDistanceTo(to) <= stopDistSq) {
                if (!entryDirSet) {
                    Vec3d step = next.subtract(p);
                    double sLen = Math.sqrt(step.x * step.x + step.z * step.z);
                    if (sLen >= 1e-6) {
                        entryDirSet = true;
                        entryDirX = step.x / sLen;
                        entryDirZ = step.z / sLen;
                    }
                }
                return new SegmentPath(points, s, total, railKeys, turnouts, railKeyS, entryDirX, entryDirZ);
            }
            Vec3d step = next.subtract(p);
            double sLen = step.lengthVector();
            if (sLen < 1e-6) {
                return new SegmentPath(new ArrayList<>(), new ArrayList<>(), 0.0, new HashSet<>());
            }
            if (!entryDirSet) {
                double hLen = Math.sqrt(step.x * step.x + step.z * step.z);
                if (hLen >= 1e-6) {
                    entryDirSet = true;
                    entryDirX = step.x / hLen;
                    entryDirZ = step.z / hLen;
                }
            }
            motion = step.scale(1.0 / sLen);
            p = next;
        }
        return new SegmentPath(new ArrayList<>(), new ArrayList<>(), 0.0, new HashSet<>());
    }

    private static double dotFacing(EnumFacing facing, double dirX, double dirZ) {
        switch (facing) {
            case NORTH:
                return (0.0 * dirX) + (-1.0 * dirZ);
            case SOUTH:
                return (0.0 * dirX) + (1.0 * dirZ);
            case WEST:
                return (-1.0 * dirX) + (0.0 * dirZ);
            case EAST:
                return (1.0 * dirX) + (0.0 * dirZ);
            default:
                return 0.0;
        }
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static double computeApproachSpeedLimitKmh(double distToEntry, double estFullBrakeDecelMps2, double maxKmh) {
        double d = Math.max(0.0, distToEntry - STOP_DISTANCE);
        double a = estFullBrakeDecelMps2;
        if (!Double.isFinite(a) || a <= 1e-3) {
            a = 0.8;
        } else {
            a = Math.abs(a);
        }
        double vMps = Math.sqrt(2.0 * a * d);
        double vKmh = vMps * 3.6;
        return Math.max(0.0, Math.min(maxKmh, vKmh));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static List<TileEntity> snapshotLoadedTileEntities(World world) {
        if (world == null || world.loadedTileEntityList == null || world.loadedTileEntityList.isEmpty()) {
            return Collections.emptyList();
        }
        Object[] snapshot;
        try {
            snapshot = world.loadedTileEntityList.toArray();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        List<TileEntity> out = new ArrayList<>(snapshot.length);
        for (Object o : snapshot) {
            if (o instanceof TileEntity) {
                out.add((TileEntity) o);
            }
        }
        return out;
    }

    private static List<Entity> snapshotLoadedEntities(World world) {
        if (world == null || world.loadedEntityList == null || world.loadedEntityList.isEmpty()) {
            return Collections.emptyList();
        }
        Object[] snapshot;
        try {
            snapshot = world.loadedEntityList.toArray();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
        List<Entity> out = new ArrayList<>(snapshot.length);
        for (Object o : snapshot) {
            if (o instanceof Entity) {
                out.add((Entity) o);
            }
        }
        return out;
    }

    private static Map<Long, RailTraceInfo> traceForwardRailTrace(World world, Vec3d from, double dirX, double dirZ, double maxDist) {
        Map<Long, RailTraceInfo> out = new HashMap<>();
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1e-6) {
            return out;
        }
        Vec3d motion = new Vec3d(dirX / len, 0.0, dirZ / len);
        Vec3d p = from;
        double total = 0.0;
        int maxSteps = 20000;
        for (int i = 0; i < maxSteps && total <= maxDist; i++) {
            ITrack track;
            try {
                track = Util.getTileEntity(world, p, false);
            } catch (Throwable ignored) {
                track = null;
            }
            if (track == null) {
                break;
            }
            Long k = getLogicalRailKey(track, world, p);
            if (k != null) {
                out.putIfAbsent(k, new RailTraceInfo(total, motion));
            }
            Vec3d next;
            try {
                next = track.getNextPosition(p, motion);
            } catch (Throwable ignored) {
                next = null;
            }
            if (next == null || next.squareDistanceTo(p) < 1e-10) {
                break;
            }
            double stepDist = next.distanceTo(p);
            total += stepDist;
            Vec3d step = next.subtract(p);
            double sLen = step.lengthVector();
            if (sLen < 1e-6) {
                break;
            }
            motion = step.scale(1.0 / sLen);
            p = next;
        }
        return out;
    }

    private static Long getLogicalRailKeyAt(World world, BlockPos railPos) {
        if (world == null || railPos == null) {
            return null;
        }
        Vec3d p = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.5, railPos.getZ() + 0.5);
        ITrack track;
        try {
            track = Util.getTileEntity(world, p, false);
        } catch (Throwable ignored) {
            track = null;
        }
        return getLogicalRailKey(track, world, p);
    }

    private static Long getLogicalRailKeyAt(World world, Vec3d p) {
        if (world == null || p == null) {
            return null;
        }
        ITrack track;
        try {
            track = Util.getTileEntity(world, p, false);
        } catch (Throwable ignored) {
            track = null;
        }
        return getLogicalRailKey(track, world, p);
    }

    private static boolean isSameConsist(Entity self, Set<UUID> selfConsist, Entity other) {
        UUID selfId = getUmcUUID(self);
        UUID otherId = getUmcUUID(other);
        if (selfId == null || otherId == null) {
            return false;
        }
        if (selfConsist != null && selfConsist.contains(otherId)) {
            return true;
        }
        Set<UUID> otherConsist = getConsistIds(other);
        return otherConsist != null && otherConsist.contains(selfId);
    }

    private static Set<UUID> getConsistIds(Entity entity) {
        if (entity == null) {
            return null;
        }
        Object self = invoke(entity, "getSelf");
        if (self == null) {
            return null;
        }
        Object consist = getFieldValue(self, "consist");
        if (consist == null) {
            return null;
        }
        Object ids = getFieldValue(consist, "ids");
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

    private static UUID getUmcUUID(Entity entity) {
        if (entity == null) {
            return null;
        }
        Object self = invoke(entity, "getSelf");
        if (self == null) {
            return null;
        }
        Object uuid = invoke(self, "getUUID");
        if (uuid instanceof UUID) {
            return (UUID) uuid;
        }
        return null;
    }

    private static Object invoke(Object target, String method, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target, args);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object getFieldValue(Object target, String name) {
        if (target == null) return null;
        try {
            Field f = findField(target.getClass(), name);
            if (f != null) {
                f.setAccessible(true);
                return f.get(target);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> search = type;
        while (search != null) {
            try {
                return search.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                search = search.getSuperclass();
            }
        }
        return null;
    }

    private static Long getLogicalRailKey(Object track) {
        return getLogicalRailKey(track, null, null);
    }

    private static Long getLogicalRailKey(Object track, World world, Vec3d p) {
        if (track == null) {
            return null;
        }
        try {
            Method m = track.getClass().getMethod("getParent");
            Object parent = m.invoke(track);
            Long key = vec3iToBlockPosLong(parent);
            if (key != null) {
                return key;
            }
        } catch (Exception ignored) {
        }
        if (track instanceof TileEntity) {
            return ((TileEntity) track).getPos().toLong();
        }
        if (world != null && p != null) {
            BlockPos bp = new BlockPos(p);
            if (!world.isBlockLoaded(bp)) {
                return null;
            }
            TileEntity te = world.getTileEntity(bp);
            if (te != null) {
                try {
                    Method m = te.getClass().getMethod("getParent");
                    Object parent = m.invoke(te);
                    Long key = vec3iToBlockPosLong(parent);
                    if (key != null) {
                        return key;
                    }
                } catch (Exception ignored) {
                }
                return te.getPos().toLong();
            }
        }
        return null;
    }

    private static Long vec3iToBlockPosLong(Object vec3i) {
        if (vec3i == null) {
            return null;
        }
        try {
            Method internal = vec3i.getClass().getMethod("internal");
            Object bp = internal.invoke(vec3i);
            if (bp instanceof BlockPos) {
                return ((BlockPos) bp).toLong();
            }
        } catch (Exception ignored) {
        }
        try {
            Method toLong = vec3i.getClass().getMethod("toLong");
            Object v = toLong.invoke(vec3i);
            if (v instanceof Long) {
                return (Long) v;
            }
        } catch (Exception ignored) {
        }
        try {
            Field fx = vec3i.getClass().getField("x");
            Field fy = vec3i.getClass().getField("y");
            Field fz = vec3i.getClass().getField("z");
            Object ox = fx.get(vec3i);
            Object oy = fy.get(vec3i);
            Object oz = fz.get(vec3i);
            if (ox instanceof Integer && oy instanceof Integer && oz instanceof Integer) {
                return new BlockPos((Integer) ox, (Integer) oy, (Integer) oz).toLong();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static SegmentPath buildSegmentPathFromPoints(List<Vec3d> points) {
        return buildSegmentPathFromPoints(points, new HashSet<>(), new HashSet<>());
    }

    private static SegmentPath buildSegmentPathFromPoints(List<Vec3d> points, Set<Long> railKeys, Set<SwitchCheck> turnouts) {
        return buildSegmentPathFromPoints(points, railKeys, turnouts, new HashMap<>());
    }

    private static SegmentPath buildSegmentPathFromPoints(List<Vec3d> points, Set<Long> railKeys, Set<SwitchCheck> turnouts, Map<Long, Double> railKeyS) {
        if (points == null || points.isEmpty()) {
            return new SegmentPath(new ArrayList<>(), new ArrayList<>(), 0.0, new HashSet<>());
        }
        List<Double> s = new ArrayList<>(points.size());
        double total = 0.0;
        s.add(0.0);
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i).distanceTo(points.get(i - 1));
            s.add(total);
        }
        double entryDirX = 0.0;
        double entryDirZ = 0.0;
        if (points.size() >= 2) {
            Vec3d p0 = points.get(0);
            Vec3d p1 = points.get(1);
            if (p0 != null && p1 != null) {
                Vec3d d = p1.subtract(p0);
                double len = Math.sqrt(d.x * d.x + d.z * d.z);
                if (len >= 1e-6) {
                    entryDirX = d.x / len;
                    entryDirZ = d.z / len;
                }
            }
        }
        return new SegmentPath(points, s, total, railKeys == null ? new HashSet<>() : railKeys, turnouts, railKeyS, entryDirX, entryDirZ);
    }

    private static SegmentCacheValue getCachedSegmentValue(World world, BlockPos start, BlockPos end) {
        if (world == null || start == null || end == null) {
            return null;
        }
        int dim = world.provider == null ? 0 : world.provider.getDimension();
        SegmentCacheKey key = new SegmentCacheKey(dim, start.toLong(), end.toLong());
        synchronized (SEGMENT_CACHE) {
            SegmentCacheValue v = SEGMENT_CACHE.get(key);
            return v == null ? null : v.copy();
        }
    }

    private static void putCachedSegmentValue(World world, BlockPos start, BlockPos end, SegmentPath segment) {
        if (world == null || start == null || end == null || segment == null || segment.railKeys == null || segment.railKeys.isEmpty()) {
            return;
        }
        int dim = world.provider == null ? 0 : world.provider.getDimension();
        SegmentCacheKey key = new SegmentCacheKey(dim, start.toLong(), end.toLong());
        synchronized (SEGMENT_CACHE) {
            SEGMENT_CACHE.put(key, new SegmentCacheValue(segment));
        }
    }

    private static SwitchCheck getTurnoutSwitchCheck(Object track) {
        if (!(track instanceof TileEntity)) {
            return null;
        }
        TileEntity te = (TileEntity) track;
        BlockPos childPos = te.getPos();
        Long parentLong = null;
        try {
            Method m = track.getClass().getMethod("getParent");
            Object parent = m.invoke(track);
            parentLong = vec3iToBlockPosLong(parent);
        } catch (Exception ignored) {
        }
        if (parentLong == null) {
            return null;
        }
        long childLong = childPos.toLong();
        if (parentLong == childLong) {
            return null;
        }
        String childType = getRailTypeName(track);
        boolean isTurn = childType != null && (childType.contains("TURN") || childType.contains("CURVE"));
        if (!isTurn) {
            String cls = track.getClass().getName();
            cls = cls == null ? "" : cls.toLowerCase();
            isTurn = cls.contains("turn") || cls.contains("switch");
        }
        if (!isTurn) {
            return null;
        }
        return new SwitchCheck(BlockPos.fromLong(parentLong), childPos);
    }

    private static String getRailTypeName(Object railTe) {
        if (railTe == null) {
            return null;
        }
        Object info = getFieldValue(railTe, "info");
        if (info == null) {
            return null;
        }
        Object settings = getFieldValue(info, "settings");
        if (settings == null) {
            return null;
        }
        Object type = getFieldValue(settings, "type");
        if (type == null) {
            return null;
        }
        String s = type.toString();
        return s == null ? null : s.toUpperCase();
    }

    private static boolean isTurnoutAligned(World world, SwitchCheck check) {
        if (world == null || check == null) {
            return true;
        }
        TileEntity parentTe = world.isBlockLoaded(check.parentPos) ? world.getTileEntity(check.parentPos) : null;
        if (parentTe != null) {
            Object forced = invoke(parentTe, "isSwitchForced");
            if (forced instanceof Boolean && (Boolean) forced) {
                Object info = getFieldValue(parentTe, "info");
                Object forcedState = info == null ? null : getFieldValue(info, "switchForced");
                if (forcedState == null) {
                    forcedState = getFieldValue(parentTe, "switchForced");
                }
                if (forcedState != null) {
                    String s = forcedState.toString();
                    s = s == null ? "" : s.toUpperCase();
                    if (s.contains("TURN")) {
                        return true;
                    }
                    if (s.contains("STRAIGHT")) {
                        return false;
                    }
                }
            }
        }
        return isPowered(world, check.childPos) || isPowered(world, check.parentPos);
    }

    private static boolean isPowered(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (!world.isBlockLoaded(pos)) {
            return false;
        }
        return world.isBlockPowered(pos);
    }

    private static class RailTraceInfo {
        public final double dist;
        public final Vec3d dir;

        public RailTraceInfo(double dist, Vec3d dir) {
            this.dist = dist;
            this.dir = dir;
        }
    }

    private static class SignalCandidate {
        public final TileTrainSignal signal;
        public final BlockPos a;
        public final BlockPos b;
        public final BlockPos stop;
        public final double dist;
        public final SegmentSideKey segmentSideKey;

        public SignalCandidate(TileTrainSignal signal, BlockPos a, BlockPos b, BlockPos stop, double dist, SegmentSideKey segmentSideKey) {
            this.signal = signal;
            this.a = a;
            this.b = b;
            this.stop = stop;
            this.dist = dist;
            this.segmentSideKey = segmentSideKey;
        }
    }

    private static class SegmentSideKey {
        public final int dim;
        public final long segMin;
        public final long segMax;
        public final long entryKey;

        public SegmentSideKey(int dim, long keyA, long keyB, long entryKey) {
            this.dim = dim;
            this.segMin = Math.min(keyA, keyB);
            this.segMax = Math.max(keyA, keyB);
            this.entryKey = entryKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SegmentSideKey)) return false;
            SegmentSideKey that = (SegmentSideKey) o;
            return dim == that.dim && segMin == that.segMin && segMax == that.segMax && entryKey == that.entryKey;
        }

        @Override
        public int hashCode() {
            int h = dim;
            h = 31 * h + (int) (segMin ^ (segMin >>> 32));
            h = 31 * h + (int) (segMax ^ (segMax >>> 32));
            h = 31 * h + (int) (entryKey ^ (entryKey >>> 32));
            return h;
        }
    }

    private static class SegmentCacheKey {
        public final int dim;
        public final long a;
        public final long b;

        public SegmentCacheKey(int dim, long p1, long p2) {
            this.dim = dim;
            this.a = Math.min(p1, p2);
            this.b = Math.max(p1, p2);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SegmentCacheKey)) return false;
            SegmentCacheKey that = (SegmentCacheKey) o;
            return dim == that.dim && a == that.a && b == that.b;
        }

        @Override
        public int hashCode() {
            int h = dim;
            h = 31 * h + (int) (a ^ (a >>> 32));
            h = 31 * h + (int) (b ^ (b >>> 32));
            return h;
        }
    }

    private static class SegmentCacheValue {
        public final Set<Long> railKeys;
        public final Set<SwitchCheck> turnouts;
        public final Map<Long, Double> railKeyS;
        public final double totalLength;
        public final double entryDirX;
        public final double entryDirZ;

        public SegmentCacheValue(SegmentPath segment) {
            this.railKeys = segment == null || segment.railKeys == null ? new HashSet<>() : new HashSet<>(segment.railKeys);
            this.turnouts = segment == null || segment.turnouts == null ? new HashSet<>() : new HashSet<>(segment.turnouts);
            this.railKeyS = segment == null || segment.railKeyS == null ? new HashMap<>() : new HashMap<>(segment.railKeyS);
            this.totalLength = segment == null ? 0.0 : segment.totalLength;
            this.entryDirX = segment == null ? 0.0 : segment.entryDirX;
            this.entryDirZ = segment == null ? 0.0 : segment.entryDirZ;
        }

        public SegmentCacheValue copy() {
            SegmentPath dummy = new SegmentPath(new ArrayList<>(), new ArrayList<>(), totalLength, railKeys, turnouts, railKeyS, entryDirX, entryDirZ);
            return new SegmentCacheValue(dummy);
        }
    }

    private static class SwitchCheck {
        public final BlockPos parentPos;
        public final BlockPos childPos;

        public SwitchCheck(BlockPos parentPos, BlockPos childPos) {
            this.parentPos = parentPos;
            this.childPos = childPos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SwitchCheck)) return false;
            SwitchCheck that = (SwitchCheck) o;
            return parentPos.equals(that.parentPos) && childPos.equals(that.childPos);
        }

        @Override
        public int hashCode() {
            int h = parentPos.hashCode();
            h = 31 * h + childPos.hashCode();
            return h;
        }
    }

    public static class SignalSegmentStatus {
        public final int railKeyCount;
        public final boolean hasTurnout;
        public final boolean turnoutAligned;
        public final boolean occupied;

        public SignalSegmentStatus(int railKeyCount, boolean hasTurnout, boolean turnoutAligned, boolean occupied) {
            this.railKeyCount = railKeyCount;
            this.hasTurnout = hasTurnout;
            this.turnoutAligned = turnoutAligned;
            this.occupied = occupied;
        }
    }

    public static class SignalSegmentReport {
        public final int railKeyCount;
        public final boolean hasTurnout;
        public final boolean turnoutAligned;
        public final int trainCount;
        public final double maxTrainSpeedKmh;
        public final double clearTimeSec;
        public final double approachingTrainSpeedKmh;
        public final double approachingEtaSec;
        public final double followingSlowToKmh;
        public final double followingSpeedLimitKmh;
        public final String strategy;
        public final boolean occupied;

        public SignalSegmentReport(int railKeyCount, boolean hasTurnout, boolean turnoutAligned, int trainCount, double maxTrainSpeedKmh, double clearTimeSec, double approachingTrainSpeedKmh, double approachingEtaSec, double followingSlowToKmh, double followingSpeedLimitKmh, String strategy, boolean occupied) {
            this.railKeyCount = railKeyCount;
            this.hasTurnout = hasTurnout;
            this.turnoutAligned = turnoutAligned;
            this.trainCount = trainCount;
            this.maxTrainSpeedKmh = maxTrainSpeedKmh;
            this.clearTimeSec = clearTimeSec;
            this.approachingTrainSpeedKmh = approachingTrainSpeedKmh;
            this.approachingEtaSec = approachingEtaSec;
            this.followingSlowToKmh = followingSlowToKmh;
            this.followingSpeedLimitKmh = followingSpeedLimitKmh;
            this.strategy = strategy == null ? "" : strategy;
            this.occupied = occupied;
        }

        public NBTTagCompound toTag(String prefix) {
            String p = prefix == null ? "" : prefix;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger(p + "keys", railKeyCount);
            tag.setBoolean(p + "turnout", hasTurnout);
            tag.setBoolean(p + "aligned", turnoutAligned);
            tag.setBoolean(p + "occupied", occupied);
            tag.setInteger(p + "trainCount", trainCount);
            tag.setDouble(p + "maxSpeedKmh", maxTrainSpeedKmh);
            tag.setDouble(p + "clearTimeSec", clearTimeSec);
            tag.setDouble(p + "approachSpeedKmh", approachingTrainSpeedKmh);
            tag.setDouble(p + "approachEtaSec", approachingEtaSec);
            tag.setDouble(p + "followSlowToKmh", followingSlowToKmh);
            tag.setDouble(p + "followLimitKmh", followingSpeedLimitKmh);
            tag.setString(p + "strategy", strategy);
            return tag;
        }
    }

    public static class SignalCommand {
        public final float throttle;
        public final float brake;
        public final Double speedLimitKmh;
        public final boolean forceCommand;

        public SignalCommand(float throttle, float brake) {
            this.throttle = throttle;
            this.brake = brake;
            this.speedLimitKmh = null;
            this.forceCommand = true;
        }

        public SignalCommand(double speedLimitKmh) {
            this.throttle = 0.0f;
            this.brake = 0.0f;
            this.speedLimitKmh = speedLimitKmh;
            this.forceCommand = false;
        }
    }

    private static class SegmentPath {
        public final List<Vec3d> points;
        public final List<Double> s;
        public final double totalLength;
        public final Set<Long> railKeys;
        public final Set<SwitchCheck> turnouts;
        public final Map<Long, Double> railKeyS;
        public final double entryDirX;
        public final double entryDirZ;

        public SegmentPath(List<Vec3d> points, List<Double> s, double totalLength, Set<Long> railKeys) {
            this(points, s, totalLength, railKeys, null);
        }

        public SegmentPath(List<Vec3d> points, List<Double> s, double totalLength, Set<Long> railKeys, Set<SwitchCheck> turnouts) {
            this(points, s, totalLength, railKeys, turnouts, null);
        }

        public SegmentPath(List<Vec3d> points, List<Double> s, double totalLength, Set<Long> railKeys, Set<SwitchCheck> turnouts, Map<Long, Double> railKeyS) {
            this(points, s, totalLength, railKeys, turnouts, railKeyS, 0.0, 0.0);
        }

        public SegmentPath(List<Vec3d> points, List<Double> s, double totalLength, Set<Long> railKeys, Set<SwitchCheck> turnouts, Map<Long, Double> railKeyS, double entryDirX, double entryDirZ) {
            this.points = points;
            this.s = s;
            this.totalLength = totalLength;
            this.railKeys = railKeys == null ? new HashSet<>() : railKeys;
            this.turnouts = turnouts == null ? new HashSet<>() : new HashSet<>(turnouts);
            this.railKeyS = railKeyS == null ? new HashMap<>() : new HashMap<>(railKeyS);
            this.entryDirX = entryDirX;
            this.entryDirZ = entryDirZ;
        }

        public boolean hasTurnouts() {
            return turnouts != null && !turnouts.isEmpty();
        }

        public boolean areTurnoutsAligned(World world) {
            if (turnouts == null || turnouts.isEmpty()) {
                return true;
            }
            for (SwitchCheck sc : turnouts) {
                if (!isTurnoutAligned(world, sc)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static Map<Long, Double> reverseRailKeyS(Map<Long, Double> in, double totalLength) {
        Map<Long, Double> out = new HashMap<>();
        if (in == null || in.isEmpty() || totalLength <= 1e-6) {
            return out;
        }
        for (Map.Entry<Long, Double> e : in.entrySet()) {
            if (e == null) continue;
            Long k = e.getKey();
            Double s = e.getValue();
            if (k == null || s == null) continue;
            out.put(k, Math.max(0.0, totalLength - s));
        }
        return out;
    }

    private static double getTrainSpeedKmh(Entity e) {
        if (e == null) return 0.0;
        double kmh = 0.0;
        try {
            kmh = IrTrainReflection.getSpeedKmh(e);
        } catch (Exception ignored) {
        }
        if (!Double.isFinite(kmh) || kmh < 0.0) {
            kmh = 0.0;
        }
        if (kmh > 800.0) {
            kmh = 800.0;
        }
        if (kmh > 0.0) {
            return kmh;
        }
        double mx = e.motionX;
        double mz = e.motionZ;
        double mps = Math.sqrt(mx * mx + mz * mz) * 20.0;
        kmh = mps * 3.6;
        if (!Double.isFinite(kmh) || kmh < 0.0) {
            return 0.0;
        }
        return Math.min(800.0, kmh);
    }

    private static SegmentOccupancyInfo getSegmentOccupancyInfo(World world, SegmentPath segment, BlockPos entry, BlockPos exit, Entity self) {
        if (world == null || segment == null || segment.railKeys == null || segment.railKeys.isEmpty() || entry == null || exit == null) {
            return new SegmentOccupancyInfo(0, 0.0, 0.0, false);
        }
        Vec3d segDir = new Vec3d(exit.getX() + 0.5 - (entry.getX() + 0.5), 0.0, exit.getZ() + 0.5 - (entry.getZ() + 0.5));
        double segLen = Math.sqrt(segDir.x * segDir.x + segDir.z * segDir.z);
        if (segLen >= 1e-6) {
            segDir = new Vec3d(segDir.x / segLen, 0.0, segDir.z / segLen);
        }
        int count = 0;
        double maxSpeed = 0.0;
        double clearTime = 0.0;
        boolean blocked = false;
        Set<UUID> selfConsist = getConsistIds(self);
        for (Entity e : snapshotLoadedEntities(world)) {
            if (e == null || e == self) continue;
            if (!IrTrainReflection.isIrTrainEntity(e)) continue;
            if (isSameConsist(self, selfConsist, e)) continue;
            Long k = getLogicalRailKeyForEntity(world, e);
            if (k == null || !segment.railKeys.contains(k)) continue;
            count++;
            double speedKmh = getTrainSpeedKmh(e);
            maxSpeed = Math.max(maxSpeed, speedKmh);
            double speedMps = Math.max(0.0, speedKmh / 3.6);
            if (speedMps < 0.2) {
                blocked = true;
                continue;
            }
            Vec3d v = new Vec3d(e.motionX, 0.0, e.motionZ);
            double vLen = Math.sqrt(v.x * v.x + v.z * v.z);
            if (vLen < 1e-6) {
                v = new Vec3d(e.posX - e.prevPosX, 0.0, e.posZ - e.prevPosZ);
                vLen = Math.sqrt(v.x * v.x + v.z * v.z);
                if (vLen < 1e-6) {
                    blocked = true;
                    continue;
                }
            }
            v = new Vec3d(v.x / vLen, 0.0, v.z / vLen);
            double dot = v.x * segDir.x + v.z * segDir.z;
            if (dot < 0.2) {
                blocked = true;
                continue;
            }
            Double s = segment.railKeyS == null ? null : segment.railKeyS.get(k);
            double posS = s == null ? segment.totalLength * 0.5 : s;
            double remaining = Math.max(0.0, segment.totalLength - posS);
            double eta = remaining / Math.max(MIN_SPEED_MPS, speedMps);
            clearTime = Math.max(clearTime, eta);
        }
        if (count == 0) {
            return new SegmentOccupancyInfo(0, 0.0, 0.0, false);
        }
        if (blocked) {
            return new SegmentOccupancyInfo(count, maxSpeed, Double.POSITIVE_INFINITY, true);
        }
        return new SegmentOccupancyInfo(count, maxSpeed, clearTime, true);
    }

    private static class SegmentOccupancyInfo {
        public final int trainCount;
        public final double maxTrainSpeedKmh;
        public final double clearTimeSec;
        public final boolean occupied;

        public SegmentOccupancyInfo(int trainCount, double maxTrainSpeedKmh, double clearTimeSec, boolean occupied) {
            this.trainCount = trainCount;
            this.maxTrainSpeedKmh = maxTrainSpeedKmh;
            this.clearTimeSec = clearTimeSec;
            this.occupied = occupied;
        }
    }

    private static class ApproachTrainInfo {
        public final double speedKmh;
        public final double etaSec;
        public final double distToEntry;

        public ApproachTrainInfo(double speedKmh, double etaSec, double distToEntry) {
            this.speedKmh = speedKmh;
            this.etaSec = etaSec;
            this.distToEntry = distToEntry;
        }
    }

    private static ApproachTrainInfo findApproachingTrain(World world, SegmentPath segment, BlockPos entry) {
        if (world == null || segment == null || entry == null || segment.railKeys == null || segment.railKeys.isEmpty()) {
            return null;
        }
        Long entryKey = getLogicalRailKeyAt(world, entry);
        if (entryKey == null) {
            return null;
        }
        Vec3d entryCenter = new Vec3d(entry.getX() + 0.5, entry.getY() + 0.5, entry.getZ() + 0.5);
        double fLen = Math.sqrt(segment.entryDirX * segment.entryDirX + segment.entryDirZ * segment.entryDirZ);
        if (fLen < 1e-6) {
            return null;
        }
        Map<Long, RailTraceInfo> behind = traceForwardRailTrace(world, entryCenter, -segment.entryDirX, -segment.entryDirZ, APPROACH_TRAIN_RANGE);
        if (behind.isEmpty()) {
            return null;
        }

        double bestEta = Double.POSITIVE_INFINITY;
        double bestSpeed = 0.0;
        double bestDist = Double.NaN;
        boolean foundAny = false;
        for (Entity e : snapshotLoadedEntities(world)) {
            if (e == null) continue;
            if (!IrTrainReflection.isIrTrainEntity(e)) continue;
            Long k = getLogicalRailKeyForEntity(world, e);
            if (k == null) continue;
            if (segment.railKeys.contains(k)) continue;
            RailTraceInfo backInfo = behind.get(k);
            if (backInfo == null) {
                continue;
            }
            double distToEntry = backInfo.dist;
            if (!Double.isFinite(distToEntry) || distToEntry <= 0.0 || distToEntry > APPROACH_TRAIN_RANGE) continue;
            double speedKmh = getTrainSpeedKmh(e);
            double speedMps = speedKmh / 3.6;
            foundAny = true;
            double dx = entryCenter.x - e.posX;
            double dz = entryCenter.z - e.posZ;
            double distPlanar = Math.sqrt(dx * dx + dz * dz);
            if (distPlanar < 1e-6) continue;
            Vec3d toEntry = new Vec3d(dx / distPlanar, 0.0, dz / distPlanar);
            Vec3d v = new Vec3d(e.motionX, 0.0, e.motionZ);
            double vLen = Math.sqrt(v.x * v.x + v.z * v.z);
            if (vLen >= 1e-6) {
                v = new Vec3d(v.x / vLen, 0.0, v.z / vLen);
                double dot = v.x * toEntry.x + v.z * toEntry.z;
                if (dot < 0.2) continue;
            }
            if (speedMps < 0.2) {
                if (!Double.isFinite(bestEta)) {
                    bestEta = Double.POSITIVE_INFINITY;
                    bestSpeed = speedKmh;
                    bestDist = distToEntry;
                }
            } else {
                double eta = distToEntry / Math.max(MIN_SPEED_MPS, speedMps);
                if (eta < bestEta) {
                    bestEta = eta;
                    bestSpeed = speedKmh;
                    bestDist = distToEntry;
                }
            }
        }
        if (!foundAny) {
            return null;
        }
        return new ApproachTrainInfo(bestSpeed, bestEta, bestDist);
    }

    private static Long getLogicalRailKeyForEntity(World world, Entity e) {
        if (world == null || e == null) {
            return null;
        }
        double x = e.posX;
        double y = e.posY;
        double z = e.posZ;
        double[] dy = new double[] { 0.0, -0.5, -1.0, -2.0, 0.5, 1.0 };
        for (double o : dy) {
            Long k = getLogicalRailKeyAt(world, new Vec3d(x, y + o, z));
            if (k != null) {
                return k;
            }
        }
        return null;
    }

    private static boolean shouldForceBrakeNow(double distToEntry, double speedKmh, double estFullBrakeDecelMps2) {
        double a = estFullBrakeDecelMps2;
        if (!Double.isFinite(a) || a <= 1e-3) {
            a = 0.8;
        } else {
            a = Math.abs(a);
        }
        double v = Math.max(0.0, speedKmh / 3.6);
        double stopping = (v * v) / (2.0 * a);
        double need = STOP_DISTANCE + stopping + 2.0;
        return distToEntry <= need;
    }
}

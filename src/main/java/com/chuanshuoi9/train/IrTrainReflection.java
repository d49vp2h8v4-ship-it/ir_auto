package com.chuanshuoi9.train;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import trackapi.lib.ITrack;
import trackapi.lib.Util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Centralised reflection hub for Immersive Railroading internals.
 * All reflection-based calls across the mod go through this class so
 * the rest of the code stays clean and IR API changes only need a fix
 * in one place.
 */
public class IrTrainReflection {

    // ──────────────────────────────────────────────
    //  Public identifiers
    // ──────────────────────────────────────────────

    public static boolean isIrTrainEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        Object irObj = getIrObject(entity);
        if (irObj == null) {
            return false;
        }
        String name = irObj.getClass().getName();
        return name.contains("immersiverailroading");
    }

    public static boolean isLocomotive(Entity entity) {
        if (entity == null) return false;
        Object irObj = getIrObject(entity);
        if (irObj == null) return false;
        if (irObj.getClass().getName().contains("Locomotive")) return true;
        Object definition = invoke(irObj, "getDefinition");
        if (definition != null && definition.getClass().getName().contains("Locomotive")) return true;
        Object defIdObj = invoke(irObj, "getDefinitionID");
        if (defIdObj instanceof String && ((String) defIdObj).contains("locomotives")) return true;
        return hasMethod(irObj, "setThrottle");
    }

    public static boolean isIrControllableTrain(Entity entity) {
        if (entity == null) return false;
        Object irObj = getIrObject(entity);
        if (irObj == null) return false;
        if (!hasMethod(irObj, "setThrottle")) return false;
        return hasMethod(irObj, "setTrainBrake") || hasMethod(irObj, "setAirBrake") || hasMethod(irObj, "setIndependentBrake");
    }

    // ──────────────────────────────────────────────
    //  Entity resolution / binding
    // ──────────────────────────────────────────────

    public static Entity resolveTrainForBinding(EntityPlayer player, Entity clicked) {
        if (isIrTrainEntity(clicked)) return clicked;
        if (player == null || player.world == null) return null;
        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLookVec();
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : player.world.loadedEntityList) {
            if (!isIrTrainEntity(entity)) continue;
            double dist = entity.getDistance(player);
            if (dist > 12.0) continue;
            Vec3d to = new Vec3d(entity.posX - eye.x, entity.posY + entity.height * 0.5 - eye.y, entity.posZ - eye.z);
            double forward = to.dotProduct(look);
            if (forward < 0.0) continue;
            double lateral = to.subtract(look.scale(forward)).lengthVector();
            double score = lateral * 3.0 + dist;
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    public static Entity resolveControllableTrainForBinding(EntityPlayer player, Entity clicked) {
        if (isIrControllableTrain(clicked)) return clicked;
        if (player == null || player.world == null) return null;
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : player.world.loadedEntityList) {
            if (!isIrControllableTrain(entity)) continue;
            double distToPlayer = entity.getDistance(player);
            if (distToPlayer > 16.0) continue;
            double distToClickedSq = clicked == null ? 64.0 : entity.getDistanceSq(clicked);
            double score = distToClickedSq + distToPlayer * distToPlayer * 0.25;
            if (score < bestScore) { bestScore = score; best = entity; }
        }
        if (best != null) return best;
        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLookVec();
        for (Entity entity : player.world.loadedEntityList) {
            if (!isIrControllableTrain(entity)) continue;
            double dist = entity.getDistance(player);
            if (dist > 20.0) continue;
            Vec3d to = new Vec3d(entity.posX - eye.x, entity.posY + entity.height * 0.5 - eye.y, entity.posZ - eye.z);
            double forward = to.dotProduct(look);
            if (forward < 0.0) continue;
            double lateral = to.subtract(look.scale(forward)).lengthVector();
            double score = lateral * 3.0 + dist;
            if (score < bestScore) { bestScore = score; best = entity; }
        }
        return best;
    }

    public static Entity findBoundTrain(Entity requester, String uuidString) {
        if (requester == null || requester.getEntityWorld() == null || uuidString == null || uuidString.isEmpty()) return null;
        UUID uuid;
        try { uuid = UUID.fromString(uuidString); } catch (IllegalArgumentException ex) { return null; }
        if (requester.getEntityWorld() instanceof WorldServer) {
            Entity inSameWorld = ((WorldServer) requester.getEntityWorld()).getEntityFromUuid(uuid);
            if (inSameWorld != null) return inSameWorld;
        }
        if (requester.getServer() == null || requester.getServer().worlds == null) return null;
        for (WorldServer world : requester.getServer().worlds) {
            if (world == null) continue;
            Entity found = world.getEntityFromUuid(uuid);
            if (found != null) return found;
        }
        return null;
    }

    // ──────────────────────────────────────────────
    //  Speed / throttle
    // ──────────────────────────────────────────────

    public static double getSpeedKmh(Entity entity) {
        Object irObj = getIrObject(entity);
        if (irObj == null) return 0.0;
        Object speedObj = invoke(irObj, "getCurrentSpeed");
        if (speedObj == null) return 0.0;
        Object metric = invoke(speedObj, "metric");
        if (metric instanceof Number) return ((Number) metric).doubleValue();
        Object mps = invoke(speedObj, "metersPerSecond");
        if (mps instanceof Number) return ((Number) mps).doubleValue() * 3.6;
        return 0.0;
    }

    public static void applyThrottleAndBrake(Entity entity, float throttle, float brake) {
        Object irObj = getIrObject(entity);
        if (irObj == null) return;
        invoke(irObj, "setThrottle", throttle);
        boolean setTrainBrake = invoke(irObj, "setTrainBrake", brake) != null;
        if (!setTrainBrake) {
            invoke(irObj, "setAirBrake", brake);
            invoke(irObj, "setIndependentBrake", brake);
        }
    }

    // ──────────────────────────────────────────────
    //  Public reflection utilities (single source of truth)
    // ──────────────────────────────────────────────

    /** Generic reflection invoke — walks class hierarchy, matches by name + compatible args. */
    public static Object invoke(Object target, String method, Object... args) {
        if (target == null) return null;
        Method m = findMethod(target.getClass(), method, args);
        if (m == null) return null;
        try {
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Read a (possibly private) field by name, walking the class hierarchy. */
    public static Object getFieldValue(Object target, String name) {
        if (target == null) return null;
        try {
            Field f = findField(target.getClass(), name);
            if (f != null) { f.setAccessible(true); return f.get(target); }
        } catch (Exception ignored) { }
        return null;
    }

    /** Find a declared field by name in the class hierarchy. */
    public static Field findField(Class<?> type, String name) {
        Class<?> search = type;
        while (search != null) {
            try { return search.getDeclaredField(name); } catch (NoSuchFieldException e) { search = search.getSuperclass(); }
        }
        return null;
    }

    /** Check whether a method (any signature) exists on the target. */
    public static boolean hasMethod(Object target, String method) {
        if (target == null) return false;
        Class<?> search = target.getClass();
        while (search != null) {
            for (Method m : search.getDeclaredMethods()) {
                if (m.getName().equals(method)) return true;
            }
            search = search.getSuperclass();
        }
        return false;
    }

    // ──────────────────────────────────────────────
    //  Rail-key helpers (TrackAPI bridging)
    // ──────────────────────────────────────────────

    /** Convert a TrackAPI tile entity to a canonical rail key (BlockPos as long). */
    public static Long getLogicalRailKey(Object track) {
        return getLogicalRailKey(track, null, null);
    }

    public static Long getLogicalRailKey(Object track, World world, Vec3d p) {
        if (track == null) return null;
        try {
            Method m = track.getClass().getMethod("getParent");
            Object parent = m.invoke(track);
            Long key = vec3iToBlockPosLong(parent);
            if (key != null) return key;
        } catch (Exception ignored) { }
        if (track instanceof TileEntity) return ((TileEntity) track).getPos().toLong();
        if (world != null && p != null) {
            BlockPos bp = new BlockPos(p);
            if (!world.isBlockLoaded(bp)) return null;
            TileEntity te = world.getTileEntity(bp);
            if (te != null) {
                try {
                    Method m = te.getClass().getMethod("getParent");
                    Object parent = m.invoke(te);
                    Long key = vec3iToBlockPosLong(parent);
                    if (key != null) return key;
                } catch (Exception ignored) { }
                return te.getPos().toLong();
            }
        }
        return null;
    }

    /** Get rail key at a world-space position. */
    public static Long getLogicalRailKeyAt(World world, BlockPos railPos) {
        if (world == null || railPos == null) return null;
        Vec3d p = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.5, railPos.getZ() + 0.5);
        ITrack track = null;
        try { track = Util.getTileEntity(world, p, false); } catch (Throwable ignored) { }
        return getLogicalRailKey(track, world, p);
    }

    public static Long getLogicalRailKeyAt(World world, Vec3d p) {
        if (world == null || p == null) return null;
        ITrack track = null;
        try { track = Util.getTileEntity(world, p, false); } catch (Throwable ignored) { }
        return getLogicalRailKey(track, world, p);
    }

    /** Convert a TrackAPI Vec3i (or similar) to a BlockPos long key. */
    public static Long vec3iToBlockPosLong(Object vec3i) {
        if (vec3i == null) return null;
        try {
            Method internal = vec3i.getClass().getMethod("internal");
            Object bp = internal.invoke(vec3i);
            if (bp instanceof BlockPos) return ((BlockPos) bp).toLong();
        } catch (Exception ignored) { }
        try {
            Method toLong = vec3i.getClass().getMethod("toLong");
            Object v = toLong.invoke(vec3i);
            if (v instanceof Long) return (Long) v;
        } catch (Exception ignored) { }
        try {
            Field fx = vec3i.getClass().getField("x");
            Field fy = vec3i.getClass().getField("y");
            Field fz = vec3i.getClass().getField("z");
            Object ox = fx.get(vec3i), oy = fy.get(vec3i), oz = fz.get(vec3i);
            if (ox instanceof Integer && oy instanceof Integer && oz instanceof Integer)
                return new BlockPos((Integer) ox, (Integer) oy, (Integer) oz).toLong();
        } catch (Exception ignored) { }
        return null;
    }

    // ──────────────────────────────────────────────
    //  Entity-consist helpers (UMC reflection)
    // ──────────────────────────────────────────────

    /** Get the UMC-side UUID for an entity. */
    public static UUID getUmcUUID(Entity entity) {
        if (entity == null) return null;
        Object self = invoke(entity, "getSelf");
        if (self == null) return null;
        Object uuid = invoke(self, "getUUID");
        return uuid instanceof UUID ? (UUID) uuid : null;
    }

    /** Get all UMC UUIDs that belong to the same consist as the given entity. */
    public static Set<UUID> getConsistIds(Entity entity) {
        if (entity == null) return null;
        Object self = invoke(entity, "getSelf");
        if (self == null) return null;
        Object consist = getFieldValue(self, "consist");
        if (consist == null) return null;
        Object ids = getFieldValue(consist, "ids");
        if (!(ids instanceof List)) return null;
        Set<UUID> out = new HashSet<>();
        for (Object o : (List<?>) ids) {
            if (o instanceof UUID) out.add((UUID) o);
        }
        return out.isEmpty() ? null : out;
    }

    /** Get a stable group-key for a train consist (lowest UUID in the consist). */
    public static UUID getConsistGroupKey(Entity entity) {
        if (entity == null) return null;
        Set<UUID> ids = getConsistIds(entity);
        if (ids == null || ids.isEmpty()) {
            UUID self = getUmcUUID(entity);
            return self == null ? entity.getUniqueID() : self;
        }
        UUID best = null;
        for (UUID id : ids) {
            if (id == null) continue;
            if (best == null) { best = id; continue; }
            if (id.getMostSignificantBits() < best.getMostSignificantBits()
                || (id.getMostSignificantBits() == best.getMostSignificantBits()
                    && id.getLeastSignificantBits() < best.getLeastSignificantBits())) {
                best = id;
            }
        }
        return best == null ? entity.getUniqueID() : best;
    }

    // ──────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────

    private static Object getIrObject(Entity mcEntity) {
        if (mcEntity == null) return null;
        Object self = invoke(mcEntity, "getSelf");
        if (self != null) return self;
        String name = mcEntity.getClass().getName();
        if (name.contains("cam72cam") || name.contains("immersiverailroading")) return mcEntity;
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Object... args) {
        Class<?> search = type;
        while (search != null) {
            for (Method method : search.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) continue;
                if (isCompatible(parameterTypes, args)) return method;
            }
            search = search.getSuperclass();
        }
        return null;
    }

    private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> p = wrap(parameterTypes[i]);
            if (args[i] == null) continue;
            Class<?> a = wrap(args[i].getClass());
            if (!p.isAssignableFrom(a) && !(Number.class.isAssignableFrom(a) && Number.class.isAssignableFrom(p)))
                return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == long.class) return Long.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }
}

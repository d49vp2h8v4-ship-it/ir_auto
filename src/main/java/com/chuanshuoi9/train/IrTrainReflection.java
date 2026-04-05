package com.chuanshuoi9.train;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import java.lang.reflect.Method;
import java.util.UUID;

public class IrTrainReflection {
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

        // Check if the IR object itself is a locomotive
        if (irObj.getClass().getName().contains("Locomotive")) return true;

        // Check the definition
        Object definition = invoke(irObj, "getDefinition");
        if (definition != null) {
            String defName = definition.getClass().getName();
            if (defName.contains("Locomotive")) return true;
        }

        Object defIdObj = invoke(irObj, "getDefinitionID");
        if (defIdObj instanceof String) {
            String defId = (String) defIdObj;
            if (defId.contains("locomotives")) {
                return true;
            }
        }

        // Fallback to method check
        return hasMethod(irObj, "setThrottle");
    }

    public static boolean isIrControllableTrain(Entity entity) {
        if (entity == null) {
            return false;
        }
        Object irObj = getIrObject(entity);
        if (irObj == null) {
            return false;
        }
        if (!hasMethod(irObj, "setThrottle")) {
            return false;
        }
        return hasMethod(irObj, "setTrainBrake") || hasMethod(irObj, "setAirBrake") || hasMethod(irObj, "setIndependentBrake");
    }

    private static Object getIrObject(Entity mcEntity) {
        if (mcEntity == null) return null;
        
        // IR/UMC entities in-world are typically cam72cam.mod.entity.ModdedEntity (MC-side wrapper).
        // The actual UMC-side entity instance is available via getSelf().
        Object self = invoke(mcEntity, "getSelf");
        if (self != null) {
            return self;
        }

        // Fallback: sometimes the UMC-side entity may already be the MC entity (unlikely, but safe).
        String name = mcEntity.getClass().getName();
        if (name.contains("cam72cam") || name.contains("immersiverailroading")) {
            return mcEntity;
        }

        return null;
    }

    private static Object getFieldValue(Object target, String name) {
        if (target == null) return null;
        try {
            java.lang.reflect.Field f = findField(target.getClass(), name);
            if (f != null) {
                f.setAccessible(true);
                return f.get(target);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
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

    public static Entity resolveTrainForBinding(EntityPlayer player, Entity clicked) {
        if (isIrTrainEntity(clicked)) {
            return clicked;
        }
        if (player == null || player.world == null) {
            return null;
        }
        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLookVec();
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : player.world.loadedEntityList) {
            if (!isIrTrainEntity(entity)) {
                continue;
            }
            double dist = entity.getDistance(player);
            if (dist > 12.0) {
                continue;
            }
            Vec3d to = new Vec3d(entity.posX - eye.x, entity.posY + entity.height * 0.5 - eye.y, entity.posZ - eye.z);
            double forward = to.dotProduct(look);
            if (forward < 0.0) {
                continue;
            }
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
        if (isIrControllableTrain(clicked)) {
            return clicked;
        }
        if (player == null || player.world == null) {
            return null;
        }
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : player.world.loadedEntityList) {
            if (!isIrControllableTrain(entity)) {
                continue;
            }
            double distToPlayer = entity.getDistance(player);
            if (distToPlayer > 16.0) {
                continue;
            }
            double distToClickedSq = clicked == null ? 64.0 : entity.getDistanceSq(clicked);
            double score = distToClickedSq + distToPlayer * distToPlayer * 0.25;
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        if (best != null) {
            return best;
        }
        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLookVec();
        for (Entity entity : player.world.loadedEntityList) {
            if (!isIrControllableTrain(entity)) {
                continue;
            }
            double dist = entity.getDistance(player);
            if (dist > 20.0) {
                continue;
            }
            Vec3d to = new Vec3d(entity.posX - eye.x, entity.posY + entity.height * 0.5 - eye.y, entity.posZ - eye.z);
            double forward = to.dotProduct(look);
            if (forward < 0.0) {
                continue;
            }
            double lateral = to.subtract(look.scale(forward)).lengthVector();
            double score = lateral * 3.0 + dist;
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    public static Entity findBoundTrain(Entity requester, String uuidString) {
        if (requester == null || requester.getEntityWorld() == null || uuidString == null || uuidString.isEmpty()) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (requester.getEntityWorld() instanceof WorldServer) {
            WorldServer world = (WorldServer) requester.getEntityWorld();
            Entity inSameWorld = world.getEntityFromUuid(uuid);
            if (inSameWorld != null) {
                return inSameWorld;
            }
        }
        if (requester.getServer() == null || requester.getServer().worlds == null) {
            return null;
        }
        for (WorldServer world : requester.getServer().worlds) {
            if (world == null) {
                continue;
            }
            Entity found = world.getEntityFromUuid(uuid);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static double getSpeedKmh(Entity entity) {
        Object irObj = getIrObject(entity);
        if (irObj == null) return 0.0;
        
        Object speedObj = invoke(irObj, "getCurrentSpeed");
        if (speedObj == null) {
            return 0.0;
        }
        Object metric = invoke(speedObj, "metric");
        if (metric instanceof Number) {
            return ((Number) metric).doubleValue();
        }
        Object mps = invoke(speedObj, "metersPerSecond");
        if (mps instanceof Number) {
            return ((Number) mps).doubleValue() * 3.6;
        }
        return 0.0;
    }

    public static void applyThrottleAndBrake(Entity entity, float throttle, float brake) {
        Object irObj = getIrObject(entity);
        if (irObj == null) {
            return;
        }
        invoke(irObj, "setThrottle", throttle);
        boolean setTrainBrake = invoke(irObj, "setTrainBrake", brake) != null;
        if (!setTrainBrake) {
            invoke(irObj, "setAirBrake", brake);
            invoke(irObj, "setIndependentBrake", brake);
        }
    }

    private static Object invoke(Object target, String method, Object... args) {
        if (target == null) {
            return null;
        }
        Method m = findMethod(target.getClass(), method, args);
        if (m == null) {
            return null;
        }
        try {
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean hasMethod(Object target, String method) {
        if (target == null) {
            return false;
        }
        Class<?> search = target.getClass();
        while (search != null) {
            Method[] methods = search.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getName().equals(method)) {
                    return true;
                }
            }
            search = search.getSuperclass();
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String name, Object... args) {
        Class<?> search = type;
        while (search != null) {
            Method[] methods = search.getDeclaredMethods();
            for (Method method : methods) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) {
                    continue;
                }
                if (isCompatible(parameterTypes, args)) {
                    return method;
                }
            }
            search = search.getSuperclass();
        }
        return null;
    }

    private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> p = wrap(parameterTypes[i]);
            if (args[i] == null) {
                continue;
            }
            Class<?> a = wrap(args[i].getClass());
            if (!p.isAssignableFrom(a)) {
                if (!(Number.class.isAssignableFrom(a) && Number.class.isAssignableFrom(p))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}

package com.chuanshuoi9.irfix;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.IrTrainReflection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Active consist anchor — detects coupling breaks and force-restores them
 * by patching BOTH the entity couplers AND the SimulationState internals.
 */
public class IrKernelFixer {

    // entity UUID → [frontUUID, rearUUID]
    private final Map<UUID, UUID[]> anchors = new HashMap<>();
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    // Cached reflection
    private static Field simulationStatesField;
    private static Field simInteractingFront;
    private static Field simInteractingRear;
    private static Field simDirty;

    @SubscribeEvent
    public void onWorldTickPost(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        World world = event.world;
        if (!(world instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) world;
        long now = world.getTotalWorldTime();

        // Fast guard: NaN/void/speed (every 1s)
        if (now % 20 == 0) fastGuard(ws, now);

        // Anchor check: verify and restore (every 1s)
        if (now % 20 == 0) anchorTick(ws, now);
    }

    // ── Fast guard ───────────────────────────────────────────────

    private void fastGuard(WorldServer ws, long now) {
        for (Entity e : ws.loadedEntityList) {
            if (e == null || e.isDead) continue;
            if (!IrTrainReflection.isIrTrainEntity(e)) continue;
            UUID id = e.getUniqueID();

            if (!Double.isFinite(e.posX) || !Double.isFinite(e.posY) || !Double.isFinite(e.posZ)) {
                if (canWarn(id, now))
                    IrAutoMod.getLogger().error("NaN on {}, removing", id);
                try { e.setDead(); ws.removeEntity(e); } catch (Exception ex) {}
                continue;
            }
            if (e.posY < -64) {
                if (canWarn(id, now))
                    try { e.setPositionAndUpdate(ws.getSpawnPoint().getX()+0.5,80,ws.getSpawnPoint().getZ()+0.5); } catch (Exception ex) {}
                continue;
            }
            double s;
            try { s = IrTrainReflection.getSpeedKmh(e); } catch (Exception ex) { s = 0; }
            if (s > 1000) {
                if (canWarn(id, now)) IrAutoMod.getLogger().warn("speed {} zeroing", s);
                try { e.motionX=0; e.motionY=0; e.motionZ=0; } catch (Exception ex) {}
            }
        }
    }

    // ── Anchor tick ──────────────────────────────────────────────

    private void anchorTick(WorldServer ws, long now) {
        Set<UUID> alive = new HashSet<>();

        for (Entity e : ws.loadedEntityList) {
            if (e == null || e.isDead) continue;
            if (!IrTrainReflection.isIrTrainEntity(e)) continue;

            UUID id = e.getUniqueID();
            alive.add(id);

            UUID[] cur = readCouplers(e);
            UUID[] saved = anchors.get(id);

            if (saved == null) {
                if (cur[0] != null || cur[1] != null) {
                    anchors.put(id, new UUID[]{cur[0], cur[1]});
                }
                continue;
            }

            // Check front
            if (!Objects.equals(saved[0], cur[0]) && saved[0] != null) {
                boolean player = isPlayerNear(ws, e, now);
                if (!player) {
                    if (canWarn(id, now))
                        IrAutoMod.getLogger().warn("Restoring front coupler {}→{} on {}", cur[0], saved[0], id);
                    restoreCoupling(e, true, saved[0]);
                }
                anchors.get(id)[0] = cur[0] != null ? cur[0] : saved[0];
            }

            // Check rear
            if (!Objects.equals(saved[1], cur[1]) && saved[1] != null) {
                boolean player = isPlayerNear(ws, e, now);
                if (!player) {
                    if (canWarn(id, now))
                        IrAutoMod.getLogger().warn("Restoring rear coupler {}→{} on {}", cur[1], saved[1], id);
                    restoreCoupling(e, false, saved[1]);
                }
                anchors.get(id)[1] = cur[1] != null ? cur[1] : saved[1];
            }
        }

        anchors.keySet().retainAll(alive);
    }

    // ── Restore coupling — entity + all SimulationState objects ──

    private void restoreCoupling(Entity e, boolean isFront, UUID targetId) {
        try {
            // 1. Restore entity-level coupler UUID
            Object self = IrTrainReflection.invoke(e, "getSelf");
            if (self == null) return;

            Object couplerType = getCouplerEnum(isFront);
            if (couplerType == null) return;

            IrTrainReflection.invoke(self, "setCoupledUUID", couplerType, targetId);

            // 2. Patch ALL SimulationState objects to match
            List<?> states = getSimulationStates(e);
            if (states == null) return;

            for (Object state : states) {
                if (state == null) continue;
                try {
                    // Set interactingFront or interactingRear
                    Field field = isFront ? getSimInteractingFront(state) : getSimInteractingRear(state);
                    if (field == null) continue;
                    field.set(state, targetId);
                    // Mark dirty
                    Field dirtyField = getSimDirty(state);
                    if (dirtyField != null) dirtyField.setBoolean(state, true);
                } catch (Exception ex) {
                    // individual state may be in bad shape, keep going
                }
            }
        } catch (Exception ex) {
            IrAutoMod.getLogger().warn("Failed to restore coupling on {}: {}", e.getUniqueID(), ex.getMessage());
        }
    }

    // ── Player detection ─────────────────────────────────────────

    private boolean isPlayerNear(WorldServer ws, Entity e, long now) {
        for (EntityPlayer p : ws.playerEntities) {
            if (p.isRiding()) {
                Entity ridden = p.getRidingEntity();
                if (ridden != null && IrTrainReflection.isIrTrainEntity(ridden)) {
                    UUID myCk = IrTrainReflection.getConsistGroupKey(e);
                    UUID rideCk = IrTrainReflection.getConsistGroupKey(ridden);
                    if (Objects.equals(myCk, rideCk)) {
                        return false; // player riding same train → definitely BUG
                    }
                }
            }
            if (p.getDistanceSq(e) < 25) return true; // within 5 blocks
        }
        return false;
    }

    // ── Reflection helpers ───────────────────────────────────────

    private UUID[] readCouplers(Entity e) {
        try {
            Object self = IrTrainReflection.invoke(e, "getSelf");
            if (self == null) return new UUID[]{null, null};
            return new UUID[]{
                (UUID) IrTrainReflection.getFieldValue(self, "coupledFront"),
                (UUID) IrTrainReflection.getFieldValue(self, "coupledBack")
            };
        } catch (Exception ex) {
            return new UUID[]{null, null};
        }
    }

    private static Object getCouplerEnum(boolean front) {
        try {
            Class<?> clz = Class.forName(
                "cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock$CouplerType");
            return Enum.valueOf((Class<Enum>) clz, front ? "FRONT" : "BACK");
        } catch (Exception e) { return null; }
    }

    private static List<?> getSimulationStates(Entity e) {
        try {
            Object self = IrTrainReflection.invoke(e, "getSelf");
            if (self == null) return null;
            Object field = IrTrainReflection.getFieldValue(self, "states");
            if (field instanceof List) return (List<?>) field;
        } catch (Exception ex) {}
        return null;
    }

    private static Field getSimInteractingFront(Object state) {
        if (simInteractingFront == null) {
            simInteractingFront = IrTrainReflection.findField(state.getClass(), "interactingFront");
        }
        return simInteractingFront;
    }

    private static Field getSimInteractingRear(Object state) {
        if (simInteractingRear == null) {
            simInteractingRear = IrTrainReflection.findField(state.getClass(), "interactingRear");
        }
        return simInteractingRear;
    }

    private static Field getSimDirty(Object state) {
        if (simDirty == null) {
            simDirty = IrTrainReflection.findField(state.getClass(), "dirty");
        }
        return simDirty;
    }

    // ── Throttle ─────────────────────────────────────────────────

    private boolean canWarn(UUID id, long now) {
        Long last = lastWarn.get(id);
        if (last != null && now - last < 100) return false;
        lastWarn.put(id, now);
        return true;
    }
}

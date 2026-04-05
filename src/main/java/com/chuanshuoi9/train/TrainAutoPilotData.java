package com.chuanshuoi9.train;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import com.chuanshuoi9.signal.TrainSignalController;

import java.util.Set;
import java.util.UUID;

public class TrainAutoPilotData {
    public static final String ROOT = "ir_auto_train_control";
    public static final String STOPS = "stops";
    public static final String CURRENT_INDEX = "currentIndex";
    public static final String THROTTLE = "throttle";
    public static final String BRAKE = "brake";
    public static final String ENABLED = "enabled";
    public static final String DWELL = "dwellTicks";
    public static final String TRAIN_STATE = "trainState";
    public static final String NEXT_DEPARTURE_TICK = "nextDepartureTick";
    public static final String OVERSPEED_COUNT = "overspeedCount";
    public static final String TRAIN_NUMBER = "trainNumber";

    private static final double APPROACH_DISTANCE = 1500.0;
    private static final double APPROACH_CAP_DISTANCE = 1000.0;
    private static final double SLOW_APPROACH_DISTANCE = 600.0;
    private static final double STOP_DISTANCE = 20.0;
    private static final double POSITION_TOLERANCE = 20.0;
    private static final float APPROACH_CAP_SPEED = 45.0f;
    private static final float SLOW_SPEED = 15.0f;
    private static final float SPEED_TOLERANCE = 0.1f;
    private static final double DEFAULT_FULL_BRAKE_DECEL_MPS2 = 1.25;
    private static final double OVERSPEED_RANGE_KMH = 25.0;
    private static final float CMD_SMOOTH_ALPHA = 0.35f;
    private static final double DECEL_EST_ALPHA = 0.15;
    private static final float THROTTLE_RISE_PER_S = 0.10f;
    private static final float THROTTLE_FALL_PER_S = 0.15f;
    private static final float THROTTLE_LIMIT_FALL_PER_S = 0.60f;
    private static final double THROTTLE_RESUME_BAND_KMH = 1.0;
    private static final double COAST_ZONE_RESUME_KMH = 35.0;
    private static final int COAST_ZONE_PULSE_TICKS = 20;
    private static final float COAST_ZONE_PULSE_THROTTLE = 0.3f;

    public static NBTTagCompound ensureAndGet(Entity train) {
        NBTTagCompound data = train.getEntityData();
        NBTTagCompound root;
        if (!data.hasKey(ROOT, Constants.NBT.TAG_COMPOUND)) {
            // Try loading from file first
            root = TrainTimetableStorage.loadTimetable(train);
            if (root == null) {
                root = new NBTTagCompound();
            }
            data.setTag(ROOT, root);
        } else {
            root = data.getCompoundTag(ROOT);
        }
        ensureDefaults(root);
        return root;
    }

    public static void ensureDefaults(NBTTagCompound root) {
        if (!root.hasKey(TRAIN_NUMBER, Constants.NBT.TAG_STRING)) {
            root.setString(TRAIN_NUMBER, "");
        }
        if (!root.hasKey(STOPS, Constants.NBT.TAG_LIST)) {
            root.setTag(STOPS, new NBTTagList());
        }
        if (!root.hasKey(CURRENT_INDEX, Constants.NBT.TAG_INT)) {
            root.setInteger(CURRENT_INDEX, 0);
        }
        if (!root.hasKey(THROTTLE, Constants.NBT.TAG_FLOAT)) {
            root.setFloat(THROTTLE, 0.6f);
        }
        if (!root.hasKey(BRAKE, Constants.NBT.TAG_FLOAT)) {
            root.setFloat(BRAKE, 0.0f);
        }
        if (!root.hasKey(ENABLED, Constants.NBT.TAG_BYTE)) {
            root.setBoolean(ENABLED, false);
        }
        if (!root.hasKey(DWELL, Constants.NBT.TAG_INT)) {
            root.setInteger(DWELL, 0);
        }
        if (!root.hasKey(TRAIN_STATE, Constants.NBT.TAG_INT)) {
            root.setInteger(TRAIN_STATE, 0);
        }
        if (!root.hasKey(NEXT_DEPARTURE_TICK, Constants.NBT.TAG_LONG)) {
            root.setLong(NEXT_DEPARTURE_TICK, 0L);
        }
        if (!root.hasKey(OVERSPEED_COUNT, Constants.NBT.TAG_INT)) {
            root.setInteger(OVERSPEED_COUNT, 0);
        }
        if (!root.hasKey("ap_estDecel", Constants.NBT.TAG_DOUBLE)) {
            root.setDouble("ap_estDecel", DEFAULT_FULL_BRAKE_DECEL_MPS2);
        }
        if (!root.hasKey("ap_prevTick", Constants.NBT.TAG_LONG)) {
            root.setLong("ap_prevTick", 0L);
        }
        if (!root.hasKey("ap_prevV", Constants.NBT.TAG_DOUBLE)) {
            root.setDouble("ap_prevV", 0.0);
        }
        if (!root.hasKey("ap_lastThrottle", Constants.NBT.TAG_FLOAT)) {
            root.setFloat("ap_lastThrottle", 0.0f);
        }
        if (!root.hasKey("ap_lastBrake", Constants.NBT.TAG_FLOAT)) {
            root.setFloat("ap_lastBrake", 0.0f);
        }
        if (!root.hasKey("ap_coastPulseTicks", Constants.NBT.TAG_INT)) {
            root.setInteger("ap_coastPulseTicks", 0);
        }
    }

    public static NBTTagCompound snapshotForClient(Entity train) {
        NBTTagCompound root = ensureAndGet(train).copy();
        root.setString("trainUuid", train.getUniqueID().toString());
        root.setString("trainName", train.getName());
        root.setDouble("speedKmh", Math.abs(IrTrainReflection.getSpeedKmh(train)));
        return root;
    }

    public static void applyClientConfig(Entity train, NBTTagCompound incoming) {
        NBTTagCompound root = ensureAndGet(train);
        if (incoming.hasKey(TRAIN_NUMBER, Constants.NBT.TAG_STRING)) {
            root.setString(TRAIN_NUMBER, normalizeTrainNumber(incoming.getString(TRAIN_NUMBER)));
        }
        NBTTagList stops = incoming.getTagList(STOPS, Constants.NBT.TAG_COMPOUND);
        NBTTagList copiedStops = new NBTTagList();
        for (int i = 0; i < stops.tagCount(); i++) {
            copiedStops.appendTag(stops.getCompoundTagAt(i).copy());
        }
        root.setTag(STOPS, copiedStops);
        int maxIndex = Math.max(0, copiedStops.tagCount() - 1);
        int index = Math.max(0, Math.min(maxIndex, incoming.getInteger(CURRENT_INDEX)));
        root.setInteger(CURRENT_INDEX, index);
        root.setFloat(THROTTLE, clamp01(incoming.getFloat(THROTTLE)));
        root.setFloat(BRAKE, clamp01(incoming.getFloat(BRAKE)));
        root.setBoolean(ENABLED, incoming.getBoolean(ENABLED));
        root.setInteger(DWELL, 0);
        root.setInteger(TRAIN_STATE, 0);
        root.setLong(NEXT_DEPARTURE_TICK, 0L);
        root.setInteger(OVERSPEED_COUNT, 0);
        train.getEntityData().setTag(ROOT, root);
        TrainTimetableStorage.saveTimetable(train, root);
    }

    public static String normalizeTrainNumber(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return "";
        }
        s = s.replace(" ", "");
        s = s.toUpperCase(java.util.Locale.ROOT);
        if (s.length() > 24) {
            s = s.substring(0, 24);
        }
        return s;
    }

    public static boolean isTrainNumberFormatValid(String normalized) {
        if (normalized == null) {
            return false;
        }
        String s = normalized.trim();
        if (s.isEmpty() || s.length() > 24) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || c == '-' || c == '_' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static UUID findTrainIdByNumber(World world, String normalizedTrainNumber, UUID exceptTrainId) {
        if (world == null || world.isRemote) {
            return null;
        }
        String n = normalizeTrainNumber(normalizedTrainNumber);
        if (n.isEmpty()) {
            return null;
        }
        Set<UUID> ids = TrainTimetableStorage.getAllTimetables(world);
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            if (exceptTrainId != null && exceptTrainId.equals(id)) {
                continue;
            }
            NBTTagCompound data = TrainTimetableStorage.getTimetable(world, id);
            if (data == null) {
                continue;
            }
            String other = data.getString(TRAIN_NUMBER);
            if (n.equals(normalizeTrainNumber(other))) {
                return id;
            }
        }
        return null;
    }

    public static void tickAutoDrive(Entity train) {
        NBTTagCompound root = ensureAndGet(train);
        if (!root.getBoolean(ENABLED)) {
            return;
        }
        NBTTagList stops = root.getTagList(STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            root.setBoolean(ENABLED, false);
            IrTrainReflection.applyThrottleAndBrake(train, 0.0f, 1.0f);
            return;
        }
        int index = Math.max(0, Math.min(stops.tagCount() - 1, root.getInteger(CURRENT_INDEX)));
        NBTTagCompound stop = stops.getCompoundTagAt(index);
        BlockPos target = new BlockPos(stop.getInteger("x"), stop.getInteger("y"), stop.getInteger("z"));
        float maxSpeed = stop.hasKey("limit", Constants.NBT.TAG_FLOAT) ? stop.getFloat("limit") : 45.0f;
        maxSpeed = Math.max(1.0f, maxSpeed);
        float baseThrottle = clamp01(root.getFloat(THROTTLE));
        float baseBrake = clamp01(root.getFloat(BRAKE));
        double speedKmh = Math.abs(IrTrainReflection.getSpeedKmh(train));
        double dx = train.posX - target.getX();
        double dz = train.posZ - target.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        int trainState = root.getInteger(TRAIN_STATE);
        long worldTick = train.world == null ? 0L : train.world.getTotalWorldTime();
        double vMps = speedKmh / 3.6;
        long prevTick = root.getLong("ap_prevTick");
        double prevVMps = root.getDouble("ap_prevV");
        double dtS = prevTick > 0L ? Math.max(0.05, (worldTick - prevTick) / 20.0) : 0.05;
        root.setLong("ap_prevTick", worldTick);
        root.setDouble("ap_prevV", vMps);
        double accelRaw = (vMps - prevVMps) / dtS;
        if (trainState != 0) {
            float throttle = 0.0f;
            float brake = 1.0f;
            if (worldTick >= root.getLong(NEXT_DEPARTURE_TICK)) {
                int next = (index + 1) % stops.tagCount();
                root.setInteger(CURRENT_INDEX, next);
                root.setInteger(TRAIN_STATE, 0);
                root.setInteger(DWELL, 0);
                root.setInteger(OVERSPEED_COUNT, 0);
                throttle = 0.1f;
                brake = 0.0f;
                root.setFloat("ap_lastThrottle", throttle);
                root.setFloat("ap_lastBrake", brake);
            }
            IrTrainReflection.applyThrottleAndBrake(train, throttle, brake);
            train.getEntityData().setTag(ROOT, root);
            return;
        }

        Double signalSpeedLimit = null;
        TrainSignalController.SignalCommand signalCmd = TrainSignalController.computeOverride(train, target, speedKmh, root.getDouble("ap_estDecel"), maxSpeed);
        if (signalCmd != null) {
            if (signalCmd.forceCommand) {
                root.setInteger(OVERSPEED_COUNT, 0);
                IrTrainReflection.applyThrottleAndBrake(train, signalCmd.throttle, signalCmd.brake);
                train.getEntityData().setTag(ROOT, root);
                return;
            }
            signalSpeedLimit = signalCmd.speedLimitKmh;
        }

        int overspeedCount = root.getInteger(OVERSPEED_COUNT);
        float throttle = 0.0f;
        float brake = 0.0f;

        if (distance <= STOP_DISTANCE) {
            throttle = 0.0f;
            brake = 1.0f;
        } else {
            double estDecel = root.getDouble("ap_estDecel");
            if (estDecel <= 0.05) {
                estDecel = DEFAULT_FULL_BRAKE_DECEL_MPS2;
            }
            double speedLimit = computeApproachSpeedLimitKmh(distance, maxSpeed);
            if (signalSpeedLimit != null) {
                speedLimit = Math.min(speedLimit, signalSpeedLimit);
            }

            double tolerance = Math.max(0.5, speedLimit * SPEED_TOLERANCE);
            float lastT = root.getFloat("ap_lastThrottle");
            float lastB = root.getFloat("ap_lastBrake");

            boolean inCoastZone = distance <= APPROACH_CAP_DISTANCE && distance > SLOW_APPROACH_DISTANCE
                    && speedLimit >= (APPROACH_CAP_SPEED - 1.0);
            int pulseTicks = root.getInteger("ap_coastPulseTicks");
            if (pulseTicks > 0) {
                pulseTicks--;
                root.setInteger("ap_coastPulseTicks", pulseTicks);
            }
            if (inCoastZone && speedKmh < COAST_ZONE_RESUME_KMH && pulseTicks <= 0) {
                pulseTicks = COAST_ZONE_PULSE_TICKS;
                root.setInteger("ap_coastPulseTicks", pulseTicks);
            }

            float brakeCap = 1.0f;
            if (inCoastZone) {
                brakeCap = 0.0f;
            } else if (distance <= APPROACH_CAP_DISTANCE && distance > STOP_DISTANCE) {
                brakeCap = 0.1f;
            }

            float throttleTarget;
            if (speedKmh >= speedLimit) {
                throttleTarget = 0.0f;
            } else if (speedKmh <= speedLimit - THROTTLE_RESUME_BAND_KMH) {
                throttleTarget = baseThrottle;
            } else {
                throttleTarget = Math.min(baseThrottle, lastT);
            }

            boolean overspeed = speedKmh > speedLimit + tolerance;
            if (overspeed) {
                overspeedCount++;
            } else {
                overspeedCount = 0;
            }

            float brakeTarget = 0.0f;
            if (overspeed) {
                throttleTarget = 0.0f;
                if (lastT <= 0.02f) {
                    double e = speedKmh - speedLimit;
                    double ratio = e / Math.max(1.0, OVERSPEED_RANGE_KMH);
                    brakeTarget = Math.min(brakeCap, clamp01((float) (ratio * ratio)));
                }
            }

            if (inCoastZone) {
                overspeedCount = 0;
                brakeTarget = 0.0f;
                throttleTarget = 0.0f;
                if (pulseTicks > 0) {
                    throttleTarget = COAST_ZONE_PULSE_THROTTLE;
                }
            }

            float fallRate = speedKmh >= speedLimit ? THROTTLE_LIMIT_FALL_PER_S : THROTTLE_FALL_PER_S;
            if (brakeTarget > 0.02f) {
                throttle = 0.0f;
            } else {
                throttle = slew(lastT, throttleTarget, THROTTLE_RISE_PER_S, fallRate, (float) dtS);
            }
            brake = lastB + (brakeTarget - lastB) * CMD_SMOOTH_ALPHA;

            if (brake > 0.2f && throttle < 0.01f && accelRaw < -0.05) {
                double sample = (-accelRaw) / Math.max(0.05, brake);
                double nextEst = estDecel * (1.0 - DECEL_EST_ALPHA) + sample * DECEL_EST_ALPHA;
                nextEst = Math.max(0.2, Math.min(4.0, nextEst));
                root.setDouble("ap_estDecel", nextEst);
            } else {
                root.setDouble("ap_estDecel", estDecel);
            }

            root.setFloat("ap_lastThrottle", throttle);
            root.setFloat("ap_lastBrake", brake);
        }

        if (distance <= POSITION_TOLERANCE) {
            int waitTicks = stop.hasKey("waitTicks", Constants.NBT.TAG_INT) ? stop.getInteger("waitTicks") : 200;
            waitTicks = Math.max(1, Math.min(20 * 60 * 5, waitTicks));
            root.setInteger(TRAIN_STATE, 1);
            root.setInteger(DWELL, 0);
            root.setLong(NEXT_DEPARTURE_TICK, worldTick + waitTicks);
            throttle = 0.0f;
            brake = 1.0f;
            root.setFloat("ap_lastThrottle", throttle);
            root.setFloat("ap_lastBrake", brake);
        }

        root.setInteger(OVERSPEED_COUNT, overspeedCount);
        IrTrainReflection.applyThrottleAndBrake(train, throttle, brake);
        train.getEntityData().setTag(ROOT, root);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float slew(float current, float target, float risePerS, float fallPerS, float dtS) {
        float maxDelta = (target >= current ? risePerS : fallPerS) * Math.max(0.0f, dtS);
        if (target > current) {
            return Math.min(target, current + maxDelta);
        }
        return Math.max(target, current - maxDelta);
    }

    private static double computeApproachSpeedLimitKmh(double distance, float maxSpeed) {
        double capSpeed = Math.min(maxSpeed, APPROACH_CAP_SPEED);
        double slowSpeed = Math.min(maxSpeed, SLOW_SPEED);
        if (distance > APPROACH_DISTANCE) {
            return maxSpeed;
        }
        if (distance > APPROACH_CAP_DISTANCE) {
            return capSpeed;
        }
        if (distance > SLOW_APPROACH_DISTANCE) {
            return capSpeed;
        }
        if (distance > STOP_DISTANCE) {
            return slowSpeed;
        }
        return 0.0;
    }
}

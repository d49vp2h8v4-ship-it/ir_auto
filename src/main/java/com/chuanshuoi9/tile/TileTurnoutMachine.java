package com.chuanshuoi9.tile;

import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import trackapi.lib.ITrack;
import trackapi.lib.Util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TileTurnoutMachine extends TileEntity implements net.minecraft.util.ITickable {
    private static final double DETECT_RANGE = 200.0;
    private static final int TICK_INTERVAL = 5;

    private BlockPos railPos;
    private EnumFacing railFacing = EnumFacing.NORTH;
    private boolean blacklistMode = true;
    private List<String> trainNumbers = new ArrayList<>();

    private int outputPower = 0;
    private String lastTrainNumber = "";

    private int tickCounter = 0;
    private boolean cacheDirty = true;
    private Set<Long> cachedRailKeys = new HashSet<>();

    public int getOutputPower() {
        return outputPower;
    }

    public BlockPos getRailPos() {
        return railPos;
    }

    public EnumFacing getRailFacing() {
        return railFacing;
    }

    public boolean isBlacklistMode() {
        return blacklistMode;
    }

    public List<String> getTrainNumbers() {
        return trainNumbers == null ? new ArrayList<>() : new ArrayList<>(trainNumbers);
    }

    public String getLastTrainNumber() {
        return lastTrainNumber == null ? "" : lastTrainNumber;
    }

    public void configure(BlockPos railPos, EnumFacing railFacing, boolean matchTriggers, List<String> trainNumbers) {
        this.railPos = railPos;
        if (railFacing != null && railFacing.getAxis().isHorizontal()) {
            this.railFacing = railFacing;
        }
        this.blacklistMode = matchTriggers;
        this.trainNumbers = trainNumbers == null ? new ArrayList<>() : new ArrayList<>(trainNumbers);
        this.cacheDirty = true;
        markAndSync();
    }

    public void applyConfig(boolean blacklistMode, List<String> trainNumbers) {
        this.blacklistMode = blacklistMode;
        this.trainNumbers = trainNumbers == null ? new ArrayList<>() : new ArrayList<>(trainNumbers);
        markAndSync();
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        if (railPos == null) {
            return;
        }

        if (cacheDirty) {
            cachedRailKeys = buildRailKeySet(world, railPos, railFacing, DETECT_RANGE);
            cacheDirty = false;
        }
        if (cachedRailKeys == null || cachedRailKeys.isEmpty()) {
            return;
        }

        Entity candidate = findNearestTrainOnKeys(world, railPos, cachedRailKeys, DETECT_RANGE);
        if (candidate == null) {
            return;
        }

        String trainNumber = "";
        try {
            trainNumber = TrainAutoPilotData.normalizeTrainNumber(TrainAutoPilotData.ensureAndGet(candidate).getString(TrainAutoPilotData.TRAIN_NUMBER));
        } catch (Exception ignored) {
        }

        if (trainNumber.isEmpty()) {
            return;
        }
        if (trainNumber.equals(getLastTrainNumber())) {
            return;
        }
        lastTrainNumber = trainNumber;

        boolean inList = containsTrainNumber(trainNumbers, trainNumber);
        boolean match = blacklistMode ? !inList : inList;
        setOutputPower(match ? 15 : 0);
    }

    private void setOutputPower(int value) {
        int v = Math.max(0, Math.min(15, value));
        if (v == outputPower) {
            return;
        }
        outputPower = v;
        markAndSync();
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        world.notifyNeighborsOfStateChange(pos.down(), getBlockType(), false);
        world.notifyNeighborsOfStateChange(pos.up(), getBlockType(), false);
        for (EnumFacing f : EnumFacing.HORIZONTALS) {
            world.notifyNeighborsOfStateChange(pos.offset(f), getBlockType(), false);
        }
    }

    private void markAndSync() {
        markDirty();
        if (world != null) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private static boolean containsTrainNumber(List<String> list, String normalized) {
        if (list == null || list.isEmpty() || normalized == null || normalized.isEmpty()) {
            return false;
        }
        for (String s : list) {
            if (normalized.equals(TrainAutoPilotData.normalizeTrainNumber(s))) {
                return true;
            }
        }
        return false;
    }

    private static Entity findNearestTrainOnKeys(World world, BlockPos center, Set<Long> keys, double range) {
        double r2 = range * range;
        Entity best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (Entity e : new ArrayList<>(world.loadedEntityList)) {
            if (e == null) {
                continue;
            }
            if (!IrTrainReflection.isLocomotive(e)) {
                continue;
            }
            double d2 = e.getDistanceSq(center);
            if (d2 > r2) {
                continue;
            }
            Long k = getLogicalRailKeyAt(world, new Vec3d(e.posX, e.posY, e.posZ));
            if (k == null || !keys.contains(k)) {
                continue;
            }
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = e;
            }
        }
        return best;
    }

    private static Entity resolveLocomotive(World world, Entity any) {
        if (any == null || world == null) {
            return null;
        }
        if (!IrTrainReflection.isIrTrainEntity(any)) {
            return null;
        }
        if (IrTrainReflection.isLocomotive(any)) {
            return any;
        }
        Set<UUID> consist = getConsistIds(any);
        if (consist == null || consist.isEmpty()) {
            return null;
        }
        for (Entity e : new ArrayList<>(world.loadedEntityList)) {
            if (e == null) {
                continue;
            }
            if (!IrTrainReflection.isIrTrainEntity(e)) {
                continue;
            }
            if (!IrTrainReflection.isLocomotive(e)) {
                continue;
            }
            UUID id = getUmcUUID(e);
            if (id != null && consist.contains(id)) {
                return e;
            }
        }
        return null;
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

    private static Object invoke(Object target, String method) {
        if (target == null || method == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object getFieldValue(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
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

    private static Set<Long> buildRailKeySet(World world, BlockPos railPos, EnumFacing facing, double range) {
        Set<Long> out = new HashSet<>();
        if (world == null || railPos == null || facing == null) {
            return out;
        }
        Vec3d from = new Vec3d(railPos.getX() + 0.5, railPos.getY() + 0.5, railPos.getZ() + 0.5);
        Vec3d dir = new Vec3d(facing.getFrontOffsetX(), 0.0, facing.getFrontOffsetZ());
        collectForwardKeys(world, from, dir, range, out);
        collectForwardKeys(world, from, dir.scale(-1.0), range, out);
        return out;
    }

    private static void collectForwardKeys(World world, Vec3d from, Vec3d motionIn, double maxDist, Set<Long> out) {
        double len = Math.sqrt(motionIn.x * motionIn.x + motionIn.z * motionIn.z);
        if (len < 1e-6) {
            return;
        }
        Vec3d motion = new Vec3d(motionIn.x / len, 0.0, motionIn.z / len);
        Vec3d p = from;
        double total = 0.0;
        int maxSteps = 10000;
        for (int i = 0; i < maxSteps && total <= maxDist; i++) {
            ITrack track = Util.getTileEntity(world, p, false);
            if (track == null) {
                return;
            }
            Long k = getLogicalRailKey(track, world, p);
            if (k != null) {
                out.add(k);
            }
            Vec3d next = track.getNextPosition(p, motion);
            if (next == null || next.squareDistanceTo(p) < 1e-10) {
                return;
            }
            double stepDist = next.distanceTo(p);
            total += stepDist;
            Vec3d step = next.subtract(p);
            double sLen = step.lengthVector();
            if (sLen < 1e-6) {
                return;
            }
            motion = step.scale(1.0 / sLen);
            p = next;
        }
    }

    private static Long getLogicalRailKeyAt(World world, Vec3d p) {
        if (world == null || p == null) {
            return null;
        }
        ITrack track = Util.getTileEntity(world, p, false);
        return getLogicalRailKey(track, world, p);
    }

    private static Long getLogicalRailKey(Object track, World world, Vec3d p) {
        if (track == null) {
            return null;
        }
        if (track instanceof TileEntity) {
            return ((TileEntity) track).getPos().toLong();
        }
        if (world != null && p != null) {
            TileEntity te = world.getTileEntity(new BlockPos(p));
            if (te != null) {
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

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (railPos != null) {
            compound.setLong("railPos", railPos.toLong());
        }
        compound.setByte("railFacing", (byte) railFacing.getHorizontalIndex());
        compound.setBoolean("matchTriggers", blacklistMode);
        NBTTagList list = new NBTTagList();
        for (String s : trainNumbers) {
            if (s == null) {
                continue;
            }
            String n = TrainAutoPilotData.normalizeTrainNumber(s);
            if (n.isEmpty()) {
                continue;
            }
            list.appendTag(new net.minecraft.nbt.NBTTagString(n));
        }
        compound.setTag("trainNumbers", list);
        compound.setInteger("outputPower", outputPower);
        compound.setString("lastTrainNumber", getLastTrainNumber());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("railPos")) {
            railPos = BlockPos.fromLong(compound.getLong("railPos"));
        } else {
            railPos = null;
        }
        railFacing = EnumFacing.getHorizontal(compound.getByte("railFacing"));
        blacklistMode = compound.getBoolean("matchTriggers");
        trainNumbers = new ArrayList<>();
        if (compound.hasKey("trainNumbers", Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList("trainNumbers", Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                String s = list.getStringTagAt(i);
                String n = TrainAutoPilotData.normalizeTrainNumber(s);
                if (!n.isEmpty()) {
                    trainNumbers.add(n);
                }
            }
        }
        outputPower = Math.max(0, Math.min(15, compound.getInteger("outputPower")));
        lastTrainNumber = compound.getString("lastTrainNumber");
        cacheDirty = true;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt != null && pkt.getNbtCompound() != null) {
            readFromNBT(pkt.getNbtCompound());
        }
    }
}

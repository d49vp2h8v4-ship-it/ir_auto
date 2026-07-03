package com.chuanshuoi9.tile;

import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.virtual.VirtualTrainSavedData;
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

        String trainNumber = "";
        Entity candidateEntity = findNearestTrainOnKeys(world, railPos, cachedRailKeys, DETECT_RANGE);
        VirtualTrainSavedData.VirtualTrainState candidateVirtual = findNearestVirtualTrainOnKeys(world, railPos, cachedRailKeys, DETECT_RANGE);
        double entityDistSq = candidateEntity == null ? Double.POSITIVE_INFINITY : candidateEntity.getDistanceSq(railPos);
        double virtualDistSq = candidateVirtual == null ? Double.POSITIVE_INFINITY : distSq(candidateVirtual, railPos);

        if (entityDistSq <= virtualDistSq) {
            if (candidateEntity == null) {
                return;
            }
            try {
                trainNumber = TrainAutoPilotData.normalizeTrainNumber(TrainAutoPilotData.ensureAndGet(candidateEntity).getString(TrainAutoPilotData.TRAIN_NUMBER));
            } catch (Exception ignored) {
            }
        } else {
            if (candidateVirtual == null) {
                return;
            }
            trainNumber = getVirtualTrainNumber(candidateVirtual);
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

    private static double distSq(VirtualTrainSavedData.VirtualTrainState st, BlockPos center) {
        if (st == null || center == null) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = (st.x - (center.getX() + 0.5));
        double dy = (st.y - (center.getY() + 0.5));
        double dz = (st.z - (center.getZ() + 0.5));
        return dx * dx + dy * dy + dz * dz;
    }

    private static VirtualTrainSavedData.VirtualTrainState findNearestVirtualTrainOnKeys(World world, BlockPos center, Set<Long> keys, double range) {
        if (world == null || center == null || keys == null || keys.isEmpty()) {
            return null;
        }
        VirtualTrainSavedData data = VirtualTrainSavedData.get(world);
        if (data == null) {
            return null;
        }
        int dim = world.provider == null ? 0 : world.provider.getDimension();
        double r2 = range * range;
        VirtualTrainSavedData.VirtualTrainState best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (VirtualTrainSavedData.VirtualTrainState st : data.byDimension(dim)) {
            if (st == null) continue;
            long k1 = st.currentKey;
            long k2 = st.nextKey;
            long k3 = st.prevKey;
            if (k1 == 0L && k2 == 0L && k3 == 0L) continue;
            boolean hit = (k1 != 0L && keys.contains(k1)) || (k2 != 0L && keys.contains(k2)) || (k3 != 0L && keys.contains(k3));
            if (!hit && st.recentKeys != null && !st.recentKeys.isEmpty()) {
                int n = st.recentKeys.size();
                int from = Math.max(0, n - 8);
                for (int i = from; i < n; i++) {
                    Long rk = st.recentKeys.get(i);
                    if (rk == null || rk == 0L) continue;
                    if (keys.contains(rk)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit) continue;
            double d2 = distSq(st, center);
            if (d2 > r2) {
                continue;
            }
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = st;
            }
        }
        return best;
    }

    private static String getVirtualTrainNumber(VirtualTrainSavedData.VirtualTrainState st) {
        if (st == null) {
            return "";
        }
        if (st.controlRoot != null) {
            try {
                NBTTagCompound root = st.controlRoot;
                TrainAutoPilotData.ensureDefaults(root);
                String n = TrainAutoPilotData.normalizeTrainNumber(root.getString(TrainAutoPilotData.TRAIN_NUMBER));
                if (!n.isEmpty()) {
                    return n;
                }
            } catch (Exception ignored) {
            }
        }
        if (st.cars != null && !st.cars.isEmpty()) {
            for (VirtualTrainSavedData.CarSnapshot c : st.cars) {
                if (c == null || c.entityTag == null) continue;
                NBTTagCompound tag = c.entityTag;
                if (tag.hasKey("ForgeData", Constants.NBT.TAG_COMPOUND)) {
                    NBTTagCompound fd = tag.getCompoundTag("ForgeData");
                    if (fd.hasKey(TrainAutoPilotData.ROOT, Constants.NBT.TAG_COMPOUND)) {
                        NBTTagCompound root = fd.getCompoundTag(TrainAutoPilotData.ROOT);
                        TrainAutoPilotData.ensureDefaults(root);
                        String n = TrainAutoPilotData.normalizeTrainNumber(root.getString(TrainAutoPilotData.TRAIN_NUMBER));
                        if (!n.isEmpty()) {
                            return n;
                        }
                    }
                }
                if (tag.hasKey(TrainAutoPilotData.ROOT, Constants.NBT.TAG_COMPOUND)) {
                    NBTTagCompound root = tag.getCompoundTag(TrainAutoPilotData.ROOT);
                    TrainAutoPilotData.ensureDefaults(root);
                    String n = TrainAutoPilotData.normalizeTrainNumber(root.getString(TrainAutoPilotData.TRAIN_NUMBER));
                    if (!n.isEmpty()) {
                        return n;
                    }
                }
            }
        }
        return "";
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
        // Invalidate signal segment caches because turnout alignment may have changed
        com.chuanshuoi9.signal.TrainSignalController.invalidateAllSegmentCaches();
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
        Set<UUID> consist = IrTrainReflection.getConsistIds(any);
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
            UUID id = IrTrainReflection.getUmcUUID(e);
            if (id != null && consist.contains(id)) {
                return e;
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
            Long k = IrTrainReflection.getLogicalRailKey(track, world, p);
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
        return IrTrainReflection.getLogicalRailKey(track, world, p);
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

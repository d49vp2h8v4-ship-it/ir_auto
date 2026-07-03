package com.chuanshuoi9.tile;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.BlockTrainDisplay;
import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.train.TrainTimetableStorage;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TileTrainDisplay extends TileEntity implements ITickable {
    private static final int EST_FONT_HEIGHT = 9;
    private static final int MAX_RENDER_LINES = 64;
    private static final String NBT_CONTROLLER = "tdController";
    private static final String NBT_FACE = "tdFace";
    private static final String NBT_MIN = "tdMin";
    private static final String NBT_MAX = "tdMax";
    private static final String NBT_TRAINS = "tdTrains";
    private static final String NBT_LINES = "tdLines";

    private BlockPos controllerPos;
    private EnumFacing displayFace = EnumFacing.NORTH;
    private BlockPos minPos;
    private BlockPos maxPos;
    private final List<String> selectedTrainUuids = new ArrayList<>();
    private final List<String> renderLines = new ArrayList<>();
    private int ticks;

    @Override
    public void update() {
        if (world == null) {
            return;
        }
        if (world.isRemote) {
            return;
        }
        ticks++;
        if (!isController()) {
            return;
        }
        if (ticks % 24 != 0) {
            return;
        }
        recomputeLines();
    }

    public boolean isController() {
        if (controllerPos == null) {
            return true;
        }
        return pos != null && pos.equals(controllerPos);
    }

    public BlockPos getControllerPos() {
        return controllerPos == null ? pos : controllerPos;
    }

    @Nullable
    public TileTrainDisplay resolveControllerClient() {
        if (world == null) {
            return null;
        }
        BlockPos p = getControllerPos();
        TileEntity te = world.getTileEntity(p);
        return te instanceof TileTrainDisplay ? (TileTrainDisplay) te : null;
    }

    public EnumFacing getDisplayFace() {
        return displayFace;
    }

    public int getWidthBlocks() {
        if (minPos == null || maxPos == null) {
            return 1;
        }
        if (displayFace == EnumFacing.NORTH || displayFace == EnumFacing.SOUTH) {
            return Math.abs(maxPos.getX() - minPos.getX()) + 1;
        }
        if (displayFace == EnumFacing.EAST || displayFace == EnumFacing.WEST) {
            return Math.abs(maxPos.getZ() - minPos.getZ()) + 1;
        }
        return Math.abs(maxPos.getX() - minPos.getX()) + 1;
    }

    public int getHeightBlocks() {
        if (minPos == null || maxPos == null) {
            return 1;
        }
        return Math.abs(maxPos.getY() - minPos.getY()) + 1;
    }

    @Nullable
    public BlockPos getMinPos() {
        return minPos;
    }

    @Nullable
    public BlockPos getMaxPos() {
        return maxPos;
    }

    public List<String> getRenderLines() {
        return new ArrayList<>(renderLines);
    }

    public void applyBoundsAndFace(ScreenBounds bounds, EnumFacing face) {
        if (bounds == null || world == null) {
            return;
        }
        BlockPos controller = bounds.controller;
        for (BlockPos p : BlockPos.getAllInBox(bounds.min, bounds.max)) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileTrainDisplay) {
                TileTrainDisplay t = (TileTrainDisplay) te;
                t.controllerPos = controller;
                if (p.equals(controller)) {
                    t.displayFace = face;
                    t.minPos = bounds.min;
                    t.maxPos = bounds.max;
                }
                t.syncToClient();
            }
        }
        TileEntity te = world.getTileEntity(controller);
        if (te instanceof TileTrainDisplay) {
            ((TileTrainDisplay) te).syncToClient();
        }
    }

    public void configureFromClient(NBTTagCompound cfg) {
        if (cfg == null || world == null || world.isRemote) {
            return;
        }
        if (!isController()) {
            return;
        }
        selectedTrainUuids.clear();
        if (cfg.hasKey(NBT_TRAINS, Constants.NBT.TAG_LIST)) {
            NBTTagList list = cfg.getTagList(NBT_TRAINS, Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                String s = list.getStringTagAt(i);
                if (s != null && !s.trim().isEmpty()) {
                    selectedTrainUuids.add(s.trim());
                }
            }
        }
        recomputeLines();
        syncToClient();
    }

    public NBTTagCompound snapshotConfig() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(NBT_CONTROLLER, getControllerPos().toLong());
        tag.setInteger(NBT_FACE, displayFace.getIndex());
        if (minPos != null) {
            tag.setLong(NBT_MIN, minPos.toLong());
        }
        if (maxPos != null) {
            tag.setLong(NBT_MAX, maxPos.toLong());
        }
        NBTTagList trains = new NBTTagList();
        for (String s : selectedTrainUuids) {
            trains.appendTag(new net.minecraft.nbt.NBTTagString(s));
        }
        tag.setTag(NBT_TRAINS, trains);
        return tag;
    }

    private void recomputeLines() {
        if (world == null || world.isRemote) {
            return;
        }
        renderLines.clear();
        int maxLines = estimateMaxLines(getHeightBlocks());
        for (int i = 0; i < selectedTrainUuids.size() && renderLines.size() < maxLines; i++) {
            String uuid = selectedTrainUuids.get(i);
            String line = formatTrainLine(uuid);
            if (line != null && !line.isEmpty()) {
                renderLines.add(line);
            }
        }
        markDirty();
        syncToClient();
    }

    private static int estimateMaxLines(int heightBlocks) {
        int hPx = Math.max(1, heightBlocks) * 16;
        int base = Math.max(1, hPx / (EST_FONT_HEIGHT + 1));
        return Math.max(1, Math.min(MAX_RENDER_LINES, base * 2));
    }

    private String formatTrainLine(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) {
            return "";
        }
        UUID id;
        try {
            id = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ex) {
            return "";
        }

        Entity train = findTrainEntity(id);
        NBTTagCompound dataFile = null;
        String trainNumber = "";
        String nextStopName = "";

        if (train != null && IrTrainReflection.isLocomotive(train)) {
            NBTTagCompound live = TrainAutoPilotData.ensureAndGet(train);
            trainNumber = TrainAutoPilotData.normalizeTrainNumber(live.getString(TrainAutoPilotData.TRAIN_NUMBER));
            nextStopName = readNextStopName(live);
            String etaText = computeEtaText(train, live);
            return formatLine(trainNumber, nextStopName, etaText);
        }

        World world0 = FMLCommonHandler.instance().getMinecraftServerInstance() == null ? null : FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        if (world0 != null) {
            dataFile = TrainTimetableStorage.getTimetable(world0, id);
        }
        if (dataFile != null) {
            TrainAutoPilotData.ensureDefaults(dataFile);
            trainNumber = TrainAutoPilotData.normalizeTrainNumber(dataFile.getString(TrainAutoPilotData.TRAIN_NUMBER));
            nextStopName = readNextStopName(dataFile);
        }
        return formatLine(trainNumber, nextStopName, "--");
    }

    private String computeEtaText(Entity train, NBTTagCompound live) {
        if (train == null || live == null) {
            return "--";
        }
        if (!live.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)) {
            return "--";
        }
        NBTTagList stops = live.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            return "--";
        }

        int targetIndex = Math.max(0, Math.min(stops.tagCount() - 1, live.getInteger(TrainAutoPilotData.CURRENT_INDEX)));

        BlockPos from = new BlockPos(train.posX, train.posY, train.posZ);
        double speedMps = Math.abs(IrTrainReflection.getSpeedKmh(train)) / 3.6;
        double totalSeconds = 0.0;

        NBTTagCompound stop = stops.getCompoundTagAt(targetIndex);
        BlockPos to = new BlockPos(stop.getInteger("x"), stop.getInteger("y"), stop.getInteger("z"));
        double dist = horizontalDistance(from, to);
        double decel = 0.8;
        double arrivedDist = 3.0;
        double decelDist = speedMps * speedMps / (2.0 * decel) + 6.0;
        if (dist <= arrivedDist) {
            return "已进站";
        }
        if (dist <= decelDist) {
            return "进站中";
        }

        totalSeconds += estimateTravelSeconds(dist, speedMps, decel);

        double min = totalSeconds / 60.0;
        return String.format("%.1fmin", Math.max(0.1, min));
    }

    private static double estimateTravelSeconds(double dist, double speedMps, double decel) {
        double v = Math.max(0.0, speedMps);
        double d = Math.max(0.0, dist);
        if (v <= 0.1) {
            return d / 2.0;
        }
        double dBrake = v * v / (2.0 * Math.max(0.1, decel));
        double tBrake = v / Math.max(0.1, decel);
        if (d <= dBrake) {
            double vAvg = Math.max(0.1, v * 0.5);
            return d / vAvg;
        }
        double dCruise = d - dBrake;
        return dCruise / v + tBrake;
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() + 0.5 - (b.getX() + 0.5);
        double dz = a.getZ() + 0.5 - (b.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String formatLine(String trainNumber, String nextStopName, String etaText) {
        String num = trainNumber == null || trainNumber.isEmpty() ? "?" : trainNumber;
        String stop = normalizeStopName(nextStopName);
        String eta = etaText == null || etaText.isEmpty() ? "--" : etaText;
        return num + "  " + stop + "  " + eta;
    }

    private static String readNextStopName(NBTTagCompound data) {
        if (data == null) {
            return "";
        }
        if (!data.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)) {
            return "";
        }
        NBTTagList stops = data.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
        if (stops.tagCount() <= 0) {
            return "";
        }
        int index = Math.max(0, Math.min(stops.tagCount() - 1, data.getInteger(TrainAutoPilotData.CURRENT_INDEX)));
        return normalizeStopName(stops.getCompoundTagAt(index).getString("name"));
    }

    private static String normalizeStopName(String value) {
        if (value == null) {
            return "未命名站点";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "未命名站点" : trimmed;
    }

    @Nullable
    private Entity findTrainEntity(UUID id) {
        if (id == null) {
            return null;
        }
        if (FMLCommonHandler.instance().getMinecraftServerInstance() == null) {
            return null;
        }
        for (WorldServer w : FMLCommonHandler.instance().getMinecraftServerInstance().worlds) {
            if (w == null) {
                continue;
            }
            Entity e = w.getEntityFromUuid(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private void syncToClient() {
        if (world == null || world.isRemote) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (isController() && minPos != null && maxPos != null) {
            BlockPos min = minPos;
            BlockPos max = maxPos.add(1, 1, 1);
            return new AxisAlignedBB(min, max);
        }
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 256.0 * 256.0;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (controllerPos != null) {
            compound.setLong(NBT_CONTROLLER, controllerPos.toLong());
        }
        compound.setInteger(NBT_FACE, displayFace.getIndex());
        if (minPos != null) {
            compound.setLong(NBT_MIN, minPos.toLong());
        }
        if (maxPos != null) {
            compound.setLong(NBT_MAX, maxPos.toLong());
        }
        NBTTagList trains = new NBTTagList();
        for (String s : selectedTrainUuids) {
            trains.appendTag(new net.minecraft.nbt.NBTTagString(s));
        }
        compound.setTag(NBT_TRAINS, trains);
        NBTTagList lines = new NBTTagList();
        for (String s : renderLines) {
            lines.appendTag(new net.minecraft.nbt.NBTTagString(s));
        }
        compound.setTag(NBT_LINES, lines);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        controllerPos = compound.hasKey(NBT_CONTROLLER, Constants.NBT.TAG_LONG) ? BlockPos.fromLong(compound.getLong(NBT_CONTROLLER)) : null;
        int idx = compound.getInteger(NBT_FACE);
        displayFace = EnumFacing.getFront(Math.max(0, Math.min(5, idx)));
        minPos = compound.hasKey(NBT_MIN, Constants.NBT.TAG_LONG) ? BlockPos.fromLong(compound.getLong(NBT_MIN)) : null;
        maxPos = compound.hasKey(NBT_MAX, Constants.NBT.TAG_LONG) ? BlockPos.fromLong(compound.getLong(NBT_MAX)) : null;
        selectedTrainUuids.clear();
        if (compound.hasKey(NBT_TRAINS, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(NBT_TRAINS, Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                String s = list.getStringTagAt(i);
                if (s != null && !s.trim().isEmpty()) {
                    selectedTrainUuids.add(s.trim());
                }
            }
        }
        renderLines.clear();
        if (compound.hasKey(NBT_LINES, Constants.NBT.TAG_LIST)) {
            NBTTagList list = compound.getTagList(NBT_LINES, Constants.NBT.TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                String s = list.getStringTagAt(i);
                if (s != null) {
                    renderLines.add(s);
                }
            }
        }
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

    public static class ScreenBounds {
        public final BlockPos controller;
        public final BlockPos min;
        public final BlockPos max;

        public ScreenBounds(BlockPos controller, BlockPos min, BlockPos max) {
            this.controller = controller;
            this.min = min;
            this.max = max;
        }
    }

    @Nullable
    public static ScreenBounds detectRectangle(World world, BlockPos clicked, EnumFacing face, int max) {
        if (world == null || clicked == null || face == null) {
            return null;
        }
        if (!face.getAxis().isHorizontal()) {
            return null;
        }
        int minY = clicked.getY();
        int maxY = clicked.getY();
        while (maxY - clicked.getY() + 1 < max) {
            BlockPos p = new BlockPos(clicked.getX(), maxY + 1, clicked.getZ());
            if (world.getBlockState(p).getBlock() != IrAutoModBlocks.trainDisplayBlock()) {
                break;
            }
            maxY++;
        }
        while (clicked.getY() - minY + 1 < max) {
            BlockPos p = new BlockPos(clicked.getX(), minY - 1, clicked.getZ());
            if (world.getBlockState(p).getBlock() != IrAutoModBlocks.trainDisplayBlock()) {
                break;
            }
            minY--;
        }

        int minH = face == EnumFacing.NORTH || face == EnumFacing.SOUTH ? clicked.getX() : clicked.getZ();
        int maxH = minH;
        while (maxH - (face == EnumFacing.NORTH || face == EnumFacing.SOUTH ? clicked.getX() : clicked.getZ()) + 1 < max) {
            BlockPos p = face == EnumFacing.NORTH || face == EnumFacing.SOUTH
                ? new BlockPos(maxH + 1, clicked.getY(), clicked.getZ())
                : new BlockPos(clicked.getX(), clicked.getY(), maxH + 1);
            if (world.getBlockState(p).getBlock() != IrAutoModBlocks.trainDisplayBlock()) {
                break;
            }
            maxH++;
        }
        while ((face == EnumFacing.NORTH || face == EnumFacing.SOUTH ? clicked.getX() : clicked.getZ()) - minH + 1 < max) {
            BlockPos p = face == EnumFacing.NORTH || face == EnumFacing.SOUTH
                ? new BlockPos(minH - 1, clicked.getY(), clicked.getZ())
                : new BlockPos(clicked.getX(), clicked.getY(), minH - 1);
            if (world.getBlockState(p).getBlock() != IrAutoModBlocks.trainDisplayBlock()) {
                break;
            }
            minH--;
        }

        int width = maxH - minH + 1;
        int height = maxY - minY + 1;
        if (width <= 0 || height <= 0 || width > max || height > max) {
            return null;
        }

        BlockPos min;
        BlockPos maxPos;
        if (face == EnumFacing.NORTH || face == EnumFacing.SOUTH) {
            min = new BlockPos(minH, minY, clicked.getZ());
            maxPos = new BlockPos(maxH, maxY, clicked.getZ());
        } else {
            min = new BlockPos(clicked.getX(), minY, minH);
            maxPos = new BlockPos(clicked.getX(), maxY, maxH);
        }

        for (BlockPos p : BlockPos.getAllInBox(min, maxPos)) {
            if (world.getBlockState(p).getBlock() != IrAutoModBlocks.trainDisplayBlock()) {
                return null;
            }
        }

        BlockPos controller = min;
        return new ScreenBounds(controller, min, maxPos);
    }

    private static class IrAutoModBlocks {
        private static Block trainDisplayBlock() {
            return com.chuanshuoi9.block.ModBlocks.TRAIN_DISPLAY;
        }
    }
}

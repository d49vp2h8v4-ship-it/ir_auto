package com.chuanshuoi9.proxy;

import com.chuanshuoi9.client.gui.GuiTrainControl;
import com.chuanshuoi9.client.gui.GuiTrainDisplayConfig;
import com.chuanshuoi9.client.gui.GuiTrainTimetable;
import com.chuanshuoi9.client.gui.GuiStationMarkerName;
import com.chuanshuoi9.client.gui.GuiTrainSignalStatus;
import com.chuanshuoi9.client.gui.GuiTurnoutMachineConfig;
import com.chuanshuoi9.client.gui.GuiTurnoutMachineBlock;
import com.chuanshuoi9.client.map.RailMapClientCache;
import com.chuanshuoi9.client.SignalStatusClientCache;
import com.chuanshuoi9.client.TrainManagerClientCache;
import com.chuanshuoi9.client.TimetableTemplateClientCache;
import com.chuanshuoi9.client.TurnoutStatusClientCache;
import com.chuanshuoi9.client.gui.GuiTimetableTemplateEditor;
import com.chuanshuoi9.client.gui.GuiTimetableTemplatePicker;
import com.chuanshuoi9.network.StationSyncMessage;
import com.chuanshuoi9.tile.TileTrainDisplay;
import com.chuanshuoi9.tile.TileStationMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ClientRegistry.bindTileEntitySpecialRenderer(TileTrainDisplay.class, new com.chuanshuoi9.client.render.TileTrainDisplayRenderer());
    }

    @Override
    public void handleRailMapSync(int dimension, long[] railPositions, long[] signalPositions) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Set<BlockPos> rails = new HashSet<>();
            if (railPositions != null) {
                for (long v : railPositions) rails.add(BlockPos.fromLong(v));
            }
            Set<BlockPos> signals = new HashSet<>();
            if (signalPositions != null) {
                for (long v : signalPositions) signals.add(BlockPos.fromLong(v));
            }
            RailMapClientCache.update(dimension, rails, signals);
        });
    }

    @Override
    public void handleStationSync(List<StationSyncMessage.StationData> stations) {
        Minecraft.getMinecraft().addScheduledTask(() -> RailMapClientCache.updateStations(stations));
    }

    @Override
    public void handleTrainManagerListSync(NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() -> TrainManagerClientCache.updateList(payload));
    }

    @Override
    public void handleTrainManagerDetailSync(NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (payload == null) return;
            String uuid = payload.getString("trainUuid");
            TrainManagerClientCache.updateDetail(uuid, payload);
        });
    }

    @Override
    public void handleTrainPositionsSync(int dimension, long[] packedXZ) {
        Minecraft.getMinecraft().addScheduledTask(() -> TrainManagerClientCache.updateTrainPositions(dimension, packedXZ));
    }

    @Override
    public void handleTimetableTemplateListSync(NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            TimetableTemplateClientCache.updateList(payload);
            if (Minecraft.getMinecraft().currentScreen instanceof GuiTimetableTemplatePicker) {
                ((GuiTimetableTemplatePicker) Minecraft.getMinecraft().currentScreen).refreshFromCache();
            }
            if (Minecraft.getMinecraft().currentScreen instanceof GuiTimetableTemplateEditor) {
                ((GuiTimetableTemplateEditor) Minecraft.getMinecraft().currentScreen).refreshFromCache();
            }
        });
    }

    @Override
    public void handleTimetableTemplateLoadSync(NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (payload == null) return;
            String id = payload.getString("id");
            if (payload.hasKey("template", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
                TimetableTemplateClientCache.updateTemplate(id, payload.getCompoundTag("template"));
            }
            if (Minecraft.getMinecraft().currentScreen instanceof GuiTimetableTemplateEditor) {
                ((GuiTimetableTemplateEditor) Minecraft.getMinecraft().currentScreen).onTemplateLoaded(id);
            }
            if (Minecraft.getMinecraft().currentScreen instanceof com.chuanshuoi9.client.gui.GuiTrainTimetable) {
                ((com.chuanshuoi9.client.gui.GuiTrainTimetable) Minecraft.getMinecraft().currentScreen).onTemplateLoaded(id);
            }
        });
    }

    @Override
    public void openTrainControlGui(String trainUuid, NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() ->
            Minecraft.getMinecraft().displayGuiScreen(new GuiTrainControl(trainUuid, payload)));
    }

    @Override
    public void openTrainTimetableGui(String trainUuid, NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() ->
            Minecraft.getMinecraft().displayGuiScreen(new GuiTrainTimetable(trainUuid, payload)));
    }

    @Override
    public void openTrainDisplayGui(BlockPos pos) {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiTrainDisplayConfig(pos)));
    }

    @Override
    public void openStationMarkerNameGui(BlockPos pos) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (pos == null || Minecraft.getMinecraft().world == null) return;
            TileEntity te = Minecraft.getMinecraft().world.getTileEntity(pos);
            String name = "";
            if (te instanceof TileStationMarker) name = ((TileStationMarker) te).getStationName();
            Minecraft.getMinecraft().displayGuiScreen(new GuiStationMarkerName(pos, name));
        });
    }

    @Override
    public void openTurnoutMachineConfigGui(EnumHand hand, ItemStack stack) {
        Minecraft.getMinecraft().addScheduledTask(() ->
            Minecraft.getMinecraft().displayGuiScreen(new GuiTurnoutMachineConfig(hand, stack)));
    }

    @Override
    public void openTurnoutMachineGui(BlockPos pos) {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiTurnoutMachineBlock(pos)));
    }

    @Override
    public void openTrainSignalStatusGui(BlockPos pos) {
        Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new GuiTrainSignalStatus(pos)));
    }

    @Override
    public void handleSignalStatusSync(BlockPos pos, NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() -> SignalStatusClientCache.update(pos, payload));
    }

    @Override
    public void handleTurnoutStatusSync(BlockPos pos, NBTTagCompound payload) {
        Minecraft.getMinecraft().addScheduledTask(() -> TurnoutStatusClientCache.update(pos, payload));
    }
}

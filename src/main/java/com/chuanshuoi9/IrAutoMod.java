package com.chuanshuoi9;

import com.chuanshuoi9.network.RailMapSyncMessage;
import com.chuanshuoi9.network.StationSyncMessage;
import com.chuanshuoi9.map.MapSyncTickHandler;
import net.minecraftforge.common.MinecraftForge;
import com.chuanshuoi9.network.OpenTrainControlGuiMessage;
import com.chuanshuoi9.network.OpenTrainTimetableGuiMessage;
import com.chuanshuoi9.network.SignalStatusRequestMessage;
import com.chuanshuoi9.network.SignalStatusSyncMessage;
import com.chuanshuoi9.network.TurnoutBlockConfigMessage;
import com.chuanshuoi9.network.TrainControlMessage;
import com.chuanshuoi9.network.StationMarkerRenameMessage;
import com.chuanshuoi9.network.TurnoutConfigMessage;
import com.chuanshuoi9.network.TurnoutStatusRequestMessage;
import com.chuanshuoi9.network.TurnoutStatusSyncMessage;
import com.chuanshuoi9.network.TrainManagerDeleteMessage;
import com.chuanshuoi9.network.TrainManagerDetailRequestMessage;
import com.chuanshuoi9.network.TrainManagerDetailSyncMessage;
import com.chuanshuoi9.network.TrainManagerListRequestMessage;
import com.chuanshuoi9.network.TrainManagerListSyncMessage;
import com.chuanshuoi9.network.TrainManagerSaveMessage;
import com.chuanshuoi9.network.TrainPositionsRequestMessage;
import com.chuanshuoi9.network.TrainPositionsSyncMessage;
import com.chuanshuoi9.network.OpenTrainDisplayGuiMessage;
import com.chuanshuoi9.network.TrainDisplayConfigMessage;
import com.chuanshuoi9.network.TrainDisplayOpenMessage;
import com.chuanshuoi9.irfix.TrainChunkLoader;
import com.chuanshuoi9.irfix.TrainChunkLoadingConfig;
import com.chuanshuoi9.item.ModItems;
import com.chuanshuoi9.tile.TileTrainSignal;
import com.chuanshuoi9.tile.TileTurnoutMachine;
import com.chuanshuoi9.tile.TileStationMarker;
import com.chuanshuoi9.tile.TileTrainDisplay;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = IrAutoMod.MODID,
    name = IrAutoMod.NAME,
    version = IrAutoMod.VERSION,
    dependencies = "required-after:immersiverailroading@"
)
public class IrAutoMod
{
    public static final String MODID = "ir_auto";
    public static final String NAME = "ir_auto";
    public static final String VERSION = "1.0";
    public static final String NETWORK_CHANNEL = MODID;
    public static final int GUI_RAILWAY_MAP = 1;
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MODID) {
        @Override
        public ItemStack getTabIconItem() {
            return new ItemStack(ModItems.RAILWAY_MAP);
        }
    };

    @Instance(MODID)
    public static IrAutoMod INSTANCE;

    public static SimpleNetworkWrapper NETWORK;

    @SidedProxy(clientSide = "com.chuanshuoi9.proxy.ClientProxy", serverSide = "com.chuanshuoi9.proxy.CommonProxy")
    public static com.chuanshuoi9.proxy.CommonProxy PROXY;

    private static Logger logger;

    public static Logger getLogger() {
        return logger;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        TrainChunkLoadingConfig.load(event.getSuggestedConfigurationFile());
        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(NETWORK_CHANNEL);
        NETWORK.registerMessage(RailMapSyncMessage.Handler.class, RailMapSyncMessage.class, 0, Side.CLIENT);
        NETWORK.registerMessage(TrainControlMessage.Handler.class, TrainControlMessage.class, 1, Side.SERVER);
        NETWORK.registerMessage(OpenTrainControlGuiMessage.Handler.class, OpenTrainControlGuiMessage.class, 2, Side.CLIENT);
        NETWORK.registerMessage(OpenTrainTimetableGuiMessage.Handler.class, OpenTrainTimetableGuiMessage.class, 3, Side.CLIENT);
        NETWORK.registerMessage(com.chuanshuoi9.network.BindTrainFromMapMessage.Handler.class, com.chuanshuoi9.network.BindTrainFromMapMessage.class, 4, Side.SERVER);
        NETWORK.registerMessage(StationSyncMessage.Handler.class, StationSyncMessage.class, 5, Side.CLIENT);
        NETWORK.registerMessage(TurnoutConfigMessage.Handler.class, TurnoutConfigMessage.class, 6, Side.SERVER);
        NETWORK.registerMessage(StationMarkerRenameMessage.Handler.class, StationMarkerRenameMessage.class, 7, Side.SERVER);
        NETWORK.registerMessage(SignalStatusRequestMessage.Handler.class, SignalStatusRequestMessage.class, 8, Side.SERVER);
        NETWORK.registerMessage(SignalStatusSyncMessage.Handler.class, SignalStatusSyncMessage.class, 9, Side.CLIENT);
        NETWORK.registerMessage(TurnoutStatusRequestMessage.Handler.class, TurnoutStatusRequestMessage.class, 10, Side.SERVER);
        NETWORK.registerMessage(TurnoutStatusSyncMessage.Handler.class, TurnoutStatusSyncMessage.class, 11, Side.CLIENT);
        NETWORK.registerMessage(TurnoutBlockConfigMessage.Handler.class, TurnoutBlockConfigMessage.class, 12, Side.SERVER);
        NETWORK.registerMessage(TrainManagerListRequestMessage.Handler.class, TrainManagerListRequestMessage.class, 13, Side.SERVER);
        NETWORK.registerMessage(TrainManagerListSyncMessage.Handler.class, TrainManagerListSyncMessage.class, 14, Side.CLIENT);
        NETWORK.registerMessage(TrainManagerDetailRequestMessage.Handler.class, TrainManagerDetailRequestMessage.class, 15, Side.SERVER);
        NETWORK.registerMessage(TrainManagerDetailSyncMessage.Handler.class, TrainManagerDetailSyncMessage.class, 16, Side.CLIENT);
        NETWORK.registerMessage(TrainManagerSaveMessage.Handler.class, TrainManagerSaveMessage.class, 17, Side.SERVER);
        NETWORK.registerMessage(TrainManagerDeleteMessage.Handler.class, TrainManagerDeleteMessage.class, 18, Side.SERVER);
        NETWORK.registerMessage(TrainPositionsRequestMessage.Handler.class, TrainPositionsRequestMessage.class, 19, Side.SERVER);
        NETWORK.registerMessage(TrainPositionsSyncMessage.Handler.class, TrainPositionsSyncMessage.class, 20, Side.CLIENT);
        NETWORK.registerMessage(OpenTrainDisplayGuiMessage.Handler.class, OpenTrainDisplayGuiMessage.class, 21, Side.CLIENT);
        NETWORK.registerMessage(TrainDisplayConfigMessage.Handler.class, TrainDisplayConfigMessage.class, 22, Side.SERVER);
        NETWORK.registerMessage(TrainDisplayOpenMessage.Handler.class, TrainDisplayOpenMessage.class, 23, Side.SERVER);
        
        GameRegistry.registerTileEntity(TileTrainSignal.class, new ResourceLocation(MODID, "train_signal"));
        GameRegistry.registerTileEntity(TileTurnoutMachine.class, new ResourceLocation(MODID, "turnout_machine"));
        GameRegistry.registerTileEntity(TileStationMarker.class, new ResourceLocation(MODID, "station_marker"));
        GameRegistry.registerTileEntity(TileTrainDisplay.class, new ResourceLocation(MODID, "train_display"));
        
        MinecraftForge.EVENT_BUS.register(new MapSyncTickHandler());
        MinecraftForge.EVENT_BUS.register(new TrainChunkLoader());
        PROXY.preInit(event);
        
        logger.info("ir_auto mod pre-initializing...");
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        logger.info("ir_auto mod initialized - Immersive Rail addon ready!");
        logger.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
        PROXY.init(event);
    }
}

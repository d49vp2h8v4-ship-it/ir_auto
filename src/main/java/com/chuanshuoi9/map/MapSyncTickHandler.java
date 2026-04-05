package com.chuanshuoi9.map;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.network.StationSyncMessage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapSyncTickHandler {
    private static final int PERIODIC_SCAN_TICKS = 100;
    private static final int DIRTY_DEBOUNCE_TICKS = 20;

    private int tickCounter = 0;
    private final Map<Integer, Integer> dirtyCountdownByDim = new HashMap<>();
    private final Map<Integer, Set<Long>> dirtyChunksByDim = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        processDirtyScans(server);

        boolean doPeriodic = server.isDedicatedServer() || server.getPlayerList().getPlayers().size() > 1;
        if (!doPeriodic) {
            tickCounter = 0;
            return;
        }

        tickCounter++;
        if (tickCounter < PERIODIC_SCAN_TICKS) {
            return;
        }
        tickCounter = 0;
        Map<Integer, List<EntityPlayerMP>> playersByDim = new HashMap<>();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            int dim = player.world.provider.getDimension();
            playersByDim.computeIfAbsent(dim, k -> new ArrayList<>()).add(player);
        }
        for (Map.Entry<Integer, List<EntityPlayerMP>> entry : playersByDim.entrySet()) {
            int dim = entry.getKey();
            WorldServer world = server.getWorld(dim);
            if (world == null) {
                continue;
            }
            RailMapCollector.collectForPlayersInDimension(world, entry.getValue());
        }
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.PlaceEvent event) {
        if (event.getWorld() == null || event.getWorld().isRemote) {
            return;
        }
        IBlockState state = event.getPlacedBlock();
        if (!isRailOrSignal(state)) {
            return;
        }
        if (event.getWorld() instanceof WorldServer) {
            markDirty((WorldServer) event.getWorld(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getWorld() == null || event.getWorld().isRemote) {
            return;
        }
        IBlockState state = event.getState();
        if (!isRailOrSignal(state)) {
            return;
        }
        if (event.getWorld() instanceof WorldServer) {
            markDirty((WorldServer) event.getWorld(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) {
                return;
            }
            if (!server.isDedicatedServer() && server.getPlayerList().getPlayers().size() <= 1) {
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            int dimension = player.world.provider.getDimension();
            RailMapSavedData data = RailMapSavedData.get(player.world);

            IrAutoMod.NETWORK.sendTo(new com.chuanshuoi9.network.RailMapSyncMessage(
                dimension, 
                data.getRails(dimension), 
                data.getSignalRailLongs(dimension)
            ), player);

            syncStationsTo(player);
        }
    }

    private void markDirty(WorldServer world, BlockPos pos) {
        int dim = world.provider.getDimension();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long chunkKey = (((long) cx) << 32) ^ (cz & 0xffffffffL);
        dirtyChunksByDim.computeIfAbsent(dim, k -> new HashSet<>()).add(chunkKey);
        dirtyCountdownByDim.put(dim, DIRTY_DEBOUNCE_TICKS);
    }

    private void processDirtyScans(MinecraftServer server) {
        if (dirtyCountdownByDim.isEmpty()) {
            return;
        }
        Set<Integer> readyDims = new HashSet<>();
        Iterator<Map.Entry<Integer, Integer>> it = dirtyCountdownByDim.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                readyDims.add(entry.getKey());
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        if (readyDims.isEmpty()) {
            return;
        }

        for (int dim : readyDims) {
            Set<Long> chunks = dirtyChunksByDim.remove(dim);
            if (chunks == null || chunks.isEmpty()) {
                continue;
            }
            WorldServer world = server.getWorld(dim);
            if (world == null) {
                continue;
            }
            RailMapCollector.collectForDirtyChunks(world, chunks);
        }
    }

    private static boolean isRailOrSignal(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        ResourceLocation registryName = state.getBlock().getRegistryName();
        if (registryName == null) {
            return false;
        }
        String namespace = registryName.getResourceDomain();
        String path = registryName.getResourcePath();
        if ("immersiverailroading".equals(namespace) && path != null && path.contains("rail")) {
            return true;
        }
        return IrAutoMod.MODID.equals(namespace) && "train_signal".equals(path);
    }
    
    public static void syncStationsToAll() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        if (!server.isDedicatedServer() && server.getPlayerList().getPlayers().size() <= 1) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            syncStationsTo(player);
        }
    }
    
    public static void syncStationsTo(EntityPlayerMP player) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        if (!server.isDedicatedServer() && server.getPlayerList().getPlayers().size() <= 1) {
            return;
        }
        int dim = player.world.provider.getDimension();
        StationMarkerSavedData data = StationMarkerSavedData.get(player.world);
        List<StationSyncMessage.StationData> stations = data.getStations(dim);
        IrAutoMod.NETWORK.sendTo(new StationSyncMessage(stations), player);
    }
}

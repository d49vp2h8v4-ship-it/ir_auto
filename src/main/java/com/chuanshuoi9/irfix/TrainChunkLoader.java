package com.chuanshuoi9.irfix;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.IrTrainReflection;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TrainChunkLoader {
    private static class WorldState {
        final Map<ChunkPos, ForgeChunkManager.Ticket> chunkToTicket = new HashMap<>();
        final List<ForgeChunkManager.Ticket> tickets = new ArrayList<>();
        final Map<UUID, Long> lastActiveByEntity = new HashMap<>();
        long lastTicketFailLogTick = -1;
    }

    private final Map<Integer, WorldState> states = new HashMap<>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!TrainChunkLoadingConfig.enabled) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        World world = event.world;
        if (!(world instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) world;
        int dim = ws.provider.getDimension();
        WorldState state = states.computeIfAbsent(dim, k -> new WorldState());

        long now = ws.getTotalWorldTime();
        Set<ChunkPos> desired = collectDesiredChunks(ws, state, now);
        updateForcedChunks(ws, state, desired, now);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (!(world instanceof WorldServer)) {
            return;
        }
        WorldServer ws = (WorldServer) world;
        int dim = ws.provider.getDimension();
        WorldState state = states.remove(dim);
        if (state == null) {
            return;
        }
        for (Map.Entry<ChunkPos, ForgeChunkManager.Ticket> e : state.chunkToTicket.entrySet()) {
            try {
                ForgeChunkManager.unforceChunk(e.getValue(), e.getKey());
            } catch (Exception ignored) {
            }
        }
        for (ForgeChunkManager.Ticket ticket : state.tickets) {
            try {
                ForgeChunkManager.releaseTicket(ticket);
            } catch (Exception ignored) {
            }
        }
    }

    private Set<ChunkPos> collectDesiredChunks(WorldServer world, WorldState state, long now) {
        Set<ChunkPos> desired = new HashSet<>();

        for (Entity entity : world.loadedEntityList) {
            if (!IrTrainReflection.isIrTrainEntity(entity)) {
                continue;
            }
            UUID id = entity.getUniqueID();
            double speedKmh = IrTrainReflection.getSpeedKmh(entity);
            boolean moving = speedKmh > TrainChunkLoadingConfig.speedThresholdKmh;
            if (moving) {
                state.lastActiveByEntity.put(id, now);
            } else {
                Long last = state.lastActiveByEntity.get(id);
                if (last == null || now - last > TrainChunkLoadingConfig.keepAliveTicksAfterStop) {
                    continue;
                }
            }

            int radius = moving ? TrainChunkLoadingConfig.movingRadiusChunks : TrainChunkLoadingConfig.idleRadiusChunks;
            int cx = (int) Math.floor(entity.posX) >> 4;
            int cz = (int) Math.floor(entity.posZ) >> 4;
            addSquare(desired, cx, cz, radius);

            if (moving && TrainChunkLoadingConfig.aheadChunksMax > 0) {
                int ahead = computeAheadChunks(speedKmh);
                if (ahead > 0) {
                    double dx = entity.motionX;
                    double dz = entity.motionZ;
                    double mag = Math.sqrt(dx * dx + dz * dz);
                    if (mag < 1.0E-4) {
                        double yawRad = Math.toRadians(entity.rotationYaw);
                        dx = -Math.sin(yawRad);
                        dz = Math.cos(yawRad);
                        mag = Math.sqrt(dx * dx + dz * dz);
                    }
                    if (mag >= 1.0E-4) {
                        dx /= mag;
                        dz /= mag;
                        for (int i = 1; i <= ahead; i++) {
                            int ax = (int) Math.floor(entity.posX + dx * (16.0 * i)) >> 4;
                            int az = (int) Math.floor(entity.posZ + dz * (16.0 * i)) >> 4;
                            addSquare(desired, ax, az, TrainChunkLoadingConfig.aheadRadiusChunks);
                        }
                    }
                }
            }
        }

        if (!state.lastActiveByEntity.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = state.lastActiveByEntity.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                if (now - e.getValue() > TrainChunkLoadingConfig.keepAliveTicksAfterStop) {
                    it.remove();
                }
            }
        }

        return desired;
    }

    private void updateForcedChunks(WorldServer world, WorldState state, Set<ChunkPos> desired, long now) {
        if (state.chunkToTicket.isEmpty()) {
            for (ChunkPos chunk : desired) {
                forceChunk(world, state, chunk, now);
            }
            return;
        }

        List<ChunkPos> toRemove = new ArrayList<>();
        for (ChunkPos forced : state.chunkToTicket.keySet()) {
            if (!desired.contains(forced)) {
                toRemove.add(forced);
            }
        }

        for (ChunkPos chunk : toRemove) {
            ForgeChunkManager.Ticket ticket = state.chunkToTicket.remove(chunk);
            if (ticket != null) {
                try {
                    ForgeChunkManager.unforceChunk(ticket, chunk);
                } catch (Exception ignored) {
                }
            }
        }

        for (ChunkPos chunk : desired) {
            if (!state.chunkToTicket.containsKey(chunk)) {
                forceChunk(world, state, chunk, now);
            }
        }
    }

    private void forceChunk(WorldServer world, WorldState state, ChunkPos chunk, long now) {
        ForgeChunkManager.Ticket ticket = findOrCreateTicket(world, state, now);
        if (ticket == null) {
            return;
        }
        try {
            ForgeChunkManager.forceChunk(ticket, chunk);
            state.chunkToTicket.put(chunk, ticket);
        } catch (Exception ignored) {
        }
    }

    private ForgeChunkManager.Ticket findOrCreateTicket(WorldServer world, WorldState state, long now) {
        for (ForgeChunkManager.Ticket t : state.tickets) {
            int size = safeChunkListSize(t);
            if (size < TrainChunkLoadingConfig.maxChunksPerTicket) {
                return t;
            }
        }
        if (state.tickets.size() >= TrainChunkLoadingConfig.maxTicketsPerWorld) {
            logTicketFailure(world, state, now);
            return null;
        }
        ForgeChunkManager.Ticket ticket;
        try {
            ticket = ForgeChunkManager.requestTicket(IrAutoMod.INSTANCE, world, ForgeChunkManager.Type.NORMAL);
        } catch (Exception ex) {
            ticket = null;
        }
        if (ticket == null) {
            logTicketFailure(world, state, now);
            return null;
        }
        state.tickets.add(ticket);
        return ticket;
    }

    private int safeChunkListSize(ForgeChunkManager.Ticket ticket) {
        try {
            return ticket.getChunkList().size();
        } catch (Exception ex) {
            return TrainChunkLoadingConfig.maxChunksPerTicket;
        }
    }

    private void logTicketFailure(WorldServer world, WorldState state, long now) {
        if (state.lastTicketFailLogTick == now) {
            return;
        }
        state.lastTicketFailLogTick = now;
        IrAutoMod.getLogger().warn("Chunk loading ticket limit reached in dim {}", world.provider.getDimension());
    }

    private void addSquare(Set<ChunkPos> out, int cx, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add(new ChunkPos(cx + dx, cz + dz));
            }
        }
    }

    private int computeAheadChunks(double speedKmh) {
        if (speedKmh <= TrainChunkLoadingConfig.speedThresholdKmh) {
            return 0;
        }
        int ahead = (int) Math.ceil(speedKmh / 40.0);
        if (ahead < 1) {
            ahead = 1;
        }
        if (ahead > TrainChunkLoadingConfig.aheadChunksMax) {
            ahead = TrainChunkLoadingConfig.aheadChunksMax;
        }
        return ahead;
    }
}

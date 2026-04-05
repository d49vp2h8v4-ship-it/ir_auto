package com.chuanshuoi9.client;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.item.ModItems;
import com.chuanshuoi9.tile.TileTrainSignal;
import com.chuanshuoi9.util.IrRailUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Mod.EventBusSubscriber(modid = IrAutoMod.MODID, value = Side.CLIENT)
public class ClientSignalPreview {
    private static final int MAX_NODES = 2500;
    private static final int MAX_PARTICLES_PER_TICK = 60;
    private static final int MAX_SIGNAL_SEGMENTS = 4;
    private static final int MAX_PATH_NODES = 20000;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (mc.player.getHeldItemMainhand().getItem() != ModItems.TRAIN_SIGNAL && mc.player.getHeldItemOffhand().getItem() != ModItems.TRAIN_SIGNAL) {
            return;
        }
        net.minecraft.item.ItemStack held = mc.player.getHeldItemMainhand().getItem() == ModItems.TRAIN_SIGNAL ? mc.player.getHeldItemMainhand() : mc.player.getHeldItemOffhand();

        int painted = 0;
        for (net.minecraft.tileentity.TileEntity te : new ArrayList<>(mc.world.loadedTileEntityList)) {
            if (!(te instanceof TileTrainSignal)) continue;
            TileTrainSignal s = (TileTrainSignal) te;
            if (s.getRailA() == null || s.getRailB() == null) continue;
            if (s.getPos().distanceSq(mc.player.getPosition()) > 256 * 256) continue;
            Set<Long> rails = collectNearbyRails(mc.world, s.getRailA(), 0);
            rails.add(s.getRailA().toLong());
            rails.add(s.getRailB().toLong());
            List<BlockPos> path = bfsPath(s.getRailA(), s.getRailB(), rails);
            spawnPath(mc.world, path, 1.0, 0.0, 0.0);
            painted++;
            if (painted >= MAX_SIGNAL_SEGMENTS) break;
        }

        RayTraceResult hit = mc.objectMouseOver;
        BlockPos lookPos = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit.getBlockPos() : null;

        net.minecraft.nbt.NBTTagCompound tag = held.getTagCompound();
        boolean hasA = tag != null && tag.hasKey("sigA");
        boolean hasB = tag != null && tag.hasKey("sigB");

        if (hasA && hasB) {
            BlockPos a = BlockPos.fromLong(tag.getLong("sigA"));
            BlockPos b = BlockPos.fromLong(tag.getLong("sigB"));
            Set<Long> rails = collectNearbyRails(mc.world, a, 0);
            rails.add(a.toLong());
            rails.add(b.toLong());
            List<BlockPos> path = bfsPath(a, b, rails);
            spawnPath(mc.world, path, 1.0, 0.0, 0.0);
            return;
        }

        if (hasA) {
            BlockPos a = BlockPos.fromLong(tag.getLong("sigA"));
            Set<Long> rails = collectNearbyRails(mc.world, a, 0);
            rails.add(a.toLong());
            List<BlockPos> section = bfsSection(a, rails);
            spawnPath(mc.world, section, 0.0, 1.0, 0.0);
            return;
        }

        if (lookPos != null && IrRailUtil.isIrRail(mc.world, lookPos)) {
            Set<Long> rails = collectNearbyRails(mc.world, lookPos, 0);
            rails.add(lookPos.toLong());
            List<BlockPos> section = bfsSection(lookPos, rails);
            spawnPath(mc.world, section, 0.0, 1.0, 0.0);
        }
    }

    private static void spawnPath(World world, List<BlockPos> nodes, double r, double g, double b) {
        if (nodes == null || nodes.isEmpty()) return;
        int step = Math.max(1, nodes.size() / MAX_PARTICLES_PER_TICK);
        for (int i = 0; i < nodes.size(); i += step) {
            BlockPos p = nodes.get(i);
            if (p == null) continue;
            world.spawnParticle(EnumParticleTypes.REDSTONE, p.getX() + 0.5, p.getY() + 0.15, p.getZ() + 0.5, r, g, b);
        }
    }

    private static Set<Long> collectNearbyRails(World world, BlockPos center, int radius) {
        Set<Long> set = new HashSet<>();
        long r2 = radius <= 0 ? Long.MAX_VALUE : 1L * radius * radius;
        for (net.minecraft.tileentity.TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {
            if (te == null) continue;
            BlockPos pos = te.getPos();
            if (pos == null) continue;
            if (center != null && pos.distanceSq(center) > r2) continue;
            if (IrRailUtil.isIrRail(world, pos)) {
                set.add(pos.toLong());
            }
        }
        return set;
    }

    private static List<BlockPos> bfsSection(BlockPos start, Set<Long> rails) {
        List<BlockPos> out = new ArrayList<>();
        Queue<BlockPos> q = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        q.add(start);
        visited.add(start.toLong());
        while (!q.isEmpty() && out.size() < MAX_NODES) {
            BlockPos cur = q.poll();
            out.add(cur);
            for (BlockPos n : neighbors(cur)) {
                long key = n.toLong();
                if (!rails.contains(key)) continue;
                if (visited.add(key)) {
                    q.add(n);
                }
            }
        }
        return out;
    }

    private static List<BlockPos> bfsPath(BlockPos start, BlockPos end, Set<Long> rails) {
        if (start == null || end == null || rails == null || rails.isEmpty()) {
            return new ArrayList<>();
        }
        long startKey = start.toLong();
        long endKey = end.toLong();
        Queue<BlockPos> q = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Map<Long, Long> prev = new HashMap<>();
        q.add(start);
        visited.add(startKey);
        boolean found = false;
        long bestKey = startKey;
        double bestDist = start.distanceSq(end);
        while (!q.isEmpty() && visited.size() < MAX_PATH_NODES) {
            BlockPos cur = q.poll();
            long curKey = cur.toLong();
            double d = cur.distanceSq(end);
            if (d < bestDist) {
                bestDist = d;
                bestKey = curKey;
            }
            if (curKey == endKey) {
                found = true;
                break;
            }
            for (BlockPos n : neighbors(cur)) {
                long key = n.toLong();
                if (!rails.contains(key)) continue;
                if (visited.add(key)) {
                    prev.put(key, curKey);
                    q.add(n);
                }
            }
        }
        long backtrackStart = found ? endKey : bestKey;
        if (backtrackStart == startKey) {
            return new ArrayList<>();
        }
        List<BlockPos> path = new ArrayList<>();
        long cur = backtrackStart;
        path.add(BlockPos.fromLong(cur));
        while (cur != startKey) {
            Long p = prev.get(cur);
            if (p == null) break;
            cur = p;
            path.add(BlockPos.fromLong(cur));
        }
        return path;
    }

    private static BlockPos[] neighbors(BlockPos p) {
        return new BlockPos[] {
            p.north(), p.south(), p.west(), p.east(),
            p.up(), p.down(),
            p.north().up(), p.south().up(), p.west().up(), p.east().up(),
            p.north().down(), p.south().down(), p.west().down(), p.east().down()
        };
    }
}

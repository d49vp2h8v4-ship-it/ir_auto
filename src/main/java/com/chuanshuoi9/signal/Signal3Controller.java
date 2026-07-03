package com.chuanshuoi9.signal;

import com.chuanshuoi9.tile.TileSignal3;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 三状态信号机列车控制系统。
 * 当列车进入信号机监控的区间 (railA→railB) 即受控，不论离信号机方块多远。
 * RED → 紧急制动（EB），YELLOW → 限速 45 km/h
 * 由 TrainAutoPilot 调用，不冲突车站停靠。
 */
public class Signal3Controller {

    public static final double YELLOW_SPEED = 45.0;

    /**
     * 查询列车当前所在的信号区间，返回速度限制。
     * @return SpeedLimit or null if not in any controlled section
     */
    public static SpeedLimit getSpeedLimit(World world, Entity train) {
        if (world == null || train == null || world.isRemote) return null;

        for (Object obj : world.loadedTileEntityList) {
            if (!(obj instanceof TileSignal3)) continue;
            TileSignal3 sig = (TileSignal3) obj;
            BlockPos a = sig.getRailA(), b = sig.getRailB();
            if (a == null || b == null) continue;

            // 列车在这段区间里吗？
            TrainSignalController.SignalSegmentReport report =
                TrainSignalController.getSignalSegmentReport(world, a, b);
            if (report == null || !report.occupied) continue;

            // 车在这个信号机管的区间里
            int aspect = sig.getAspect();
            if (aspect == 0) {
                // RED → 紧急制动
                return new SpeedLimit(0, sig.getPos(), true);
            }
            if (aspect == 1) {
                // YELLOW → 限速
                return new SpeedLimit(YELLOW_SPEED, sig.getPos(), false);
            }
            // GREEN → 不限速
            return null;
        }
        return null;
    }

    public static class SpeedLimit {
        public final double speedKmh;
        public final BlockPos signalPos;
        public final boolean emergencyBrake; // true = EB, full stop

        public SpeedLimit(double speedKmh, BlockPos signalPos, boolean emergencyBrake) {
            this.speedKmh = speedKmh;
            this.signalPos = signalPos;
            this.emergencyBrake = emergencyBrake;
        }
    }
}

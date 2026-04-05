package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.signal.TrainSignalController;
import com.chuanshuoi9.tile.TileTrainSignal;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SignalStatusRequestMessage implements IMessage {
    private long posLong;

    public SignalStatusRequestMessage() {
    }

    public SignalStatusRequestMessage(BlockPos pos) {
        this.posLong = pos == null ? 0L : pos.toLong();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posLong = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posLong);
    }

    public static class Handler implements IMessageHandler<SignalStatusRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(SignalStatusRequestMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, SignalStatusRequestMessage message) {
            if (player == null || message == null) {
                return;
            }
            World world = player.getServerWorld();
            if (world == null) {
                return;
            }
            BlockPos pos = BlockPos.fromLong(message.posLong);
            if (!world.isBlockLoaded(pos)) {
                return;
            }
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileTrainSignal)) {
                return;
            }
            TileTrainSignal signal = (TileTrainSignal) te;
            BlockPos a = signal.getRailA();
            BlockPos b = signal.getRailB();
            if (a == null || b == null) {
                return;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("a", a.toLong());
            tag.setLong("b", b.toLong());
            EnumFacing facing = signal.getFacing();
            tag.setString("facing", facing == null ? "" : facing.getName());

            try {
                TrainSignalController.SignalSegmentReport ba = TrainSignalController.getSignalSegmentReport(world, b, a);
                if (ba != null) {
                    tag.merge(ba.toTag("ba_"));
                }
            } catch (Throwable ignored) {
            }
            IrAutoMod.NETWORK.sendTo(new SignalStatusSyncMessage(pos, tag), player);
        }
    }
}

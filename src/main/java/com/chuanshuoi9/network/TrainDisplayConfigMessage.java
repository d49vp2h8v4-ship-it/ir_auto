package com.chuanshuoi9.network;

import com.chuanshuoi9.tile.TileTrainDisplay;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TrainDisplayConfigMessage implements IMessage {
    private long posLong;
    private NBTTagCompound cfg;

    public TrainDisplayConfigMessage() {
    }

    public TrainDisplayConfigMessage(BlockPos pos, NBTTagCompound cfg) {
        this.posLong = pos == null ? 0L : pos.toLong();
        this.cfg = cfg == null ? new NBTTagCompound() : cfg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posLong = buf.readLong();
        cfg = ByteBufUtils.readTag(buf);
        if (cfg == null) {
            cfg = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posLong);
        ByteBufUtils.writeTag(buf, cfg);
    }

    public static class Handler implements IMessageHandler<TrainDisplayConfigMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainDisplayConfigMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, BlockPos.fromLong(message.posLong), message.cfg));
            return null;
        }

        private void handle(EntityPlayerMP player, BlockPos pos, NBTTagCompound cfg) {
            if (player == null || pos == null) {
                return;
            }
            if (player.getDistanceSq(pos) > 64.0 * 64.0) {
                return;
            }
            TileEntity te = player.world.getTileEntity(pos);
            if (!(te instanceof TileTrainDisplay)) {
                return;
            }
            TileTrainDisplay tile = (TileTrainDisplay) te;
            TileTrainDisplay controller = tile.isController() ? tile : (TileTrainDisplay) player.world.getTileEntity(tile.getControllerPos());
            if (controller == null) {
                return;
            }
            controller.configureFromClient(cfg);
        }
    }
}


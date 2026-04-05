package com.chuanshuoi9.network;

import com.chuanshuoi9.tile.TileStationMarker;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class StationMarkerRenameMessage implements IMessage {
    private long pos;
    private String name;

    public StationMarkerRenameMessage() {
    }

    public StationMarkerRenameMessage(BlockPos pos, String name) {
        this.pos = pos == null ? 0L : pos.toLong();
        this.name = name == null ? "" : name;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = buf.readLong();
        name = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
    }

    public static class Handler implements IMessageHandler<StationMarkerRenameMessage, IMessage> {
        @Override
        public IMessage onMessage(StationMarkerRenameMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, StationMarkerRenameMessage message) {
            BlockPos p = BlockPos.fromLong(message.pos);
            TileEntity te = player.world.getTileEntity(p);
            if (!(te instanceof TileStationMarker)) {
                player.sendMessage(new TextComponentString("未找到站台标记器"));
                return;
            }
            String v = message.name == null ? "" : message.name.trim();
            if (v.isEmpty()) {
                player.sendMessage(new TextComponentString("站名不能为空"));
                return;
            }
            if (v.length() > 32) {
                v = v.substring(0, 32);
            }
            ((TileStationMarker) te).setStationName(v);
            player.sendMessage(new TextComponentString("已修改站名: " + v));
        }
    }
}


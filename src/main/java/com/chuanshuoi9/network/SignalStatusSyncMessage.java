package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SignalStatusSyncMessage implements IMessage {
    private long posLong;
    private NBTTagCompound payload;

    public SignalStatusSyncMessage() {
    }

    public SignalStatusSyncMessage(BlockPos pos, NBTTagCompound payload) {
        this.posLong = pos == null ? 0L : pos.toLong();
        this.payload = payload == null ? new NBTTagCompound() : payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posLong = buf.readLong();
        payload = ByteBufUtils.readTag(buf);
        if (payload == null) {
            payload = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posLong);
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<SignalStatusSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(SignalStatusSyncMessage message, MessageContext ctx) {
            IrAutoMod.PROXY.handleSignalStatusSync(BlockPos.fromLong(message.posLong), message.payload);
            return null;
        }
    }
}


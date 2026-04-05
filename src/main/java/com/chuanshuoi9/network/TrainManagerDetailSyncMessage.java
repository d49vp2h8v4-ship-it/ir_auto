package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TrainManagerDetailSyncMessage implements IMessage {
    private NBTTagCompound payload;

    public TrainManagerDetailSyncMessage() {
    }

    public TrainManagerDetailSyncMessage(NBTTagCompound payload) {
        this.payload = payload == null ? new NBTTagCompound() : payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        payload = ByteBufUtils.readTag(buf);
        if (payload == null) {
            payload = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<TrainManagerDetailSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainManagerDetailSyncMessage message, MessageContext ctx) {
            IrAutoMod.PROXY.handleTrainManagerDetailSync(message.payload);
            return null;
        }
    }
}


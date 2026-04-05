package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class OpenTrainControlGuiMessage implements IMessage {
    private String trainUuid;
    private NBTTagCompound payload;

    public OpenTrainControlGuiMessage() {
    }

    public OpenTrainControlGuiMessage(String trainUuid, NBTTagCompound payload) {
        this.trainUuid = trainUuid == null ? "" : trainUuid;
        this.payload = payload == null ? new NBTTagCompound() : payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        trainUuid = ByteBufUtils.readUTF8String(buf);
        payload = ByteBufUtils.readTag(buf);
        if (payload == null) {
            payload = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, trainUuid);
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<OpenTrainControlGuiMessage, IMessage> {
        @Override
        public IMessage onMessage(OpenTrainControlGuiMessage message, MessageContext ctx) {
            IrAutoMod.PROXY.openTrainControlGui(message.trainUuid, message.payload);
            return null;
        }
    }
}

package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class OpenTrainDisplayGuiMessage implements IMessage {
    private long posLong;

    public OpenTrainDisplayGuiMessage() {
    }

    public OpenTrainDisplayGuiMessage(BlockPos pos) {
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

    public static class Handler implements IMessageHandler<OpenTrainDisplayGuiMessage, IMessage> {
        @Override
        public IMessage onMessage(OpenTrainDisplayGuiMessage message, MessageContext ctx) {
            BlockPos pos = BlockPos.fromLong(message.posLong);
            IrAutoMod.PROXY.openTrainDisplayGui(pos);
            return null;
        }
    }
}


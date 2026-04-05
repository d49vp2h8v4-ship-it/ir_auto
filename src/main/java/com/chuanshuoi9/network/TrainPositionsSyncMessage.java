package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TrainPositionsSyncMessage implements IMessage {
    private int dimension;
    private long[] packedXZ;

    public TrainPositionsSyncMessage() {
    }

    public TrainPositionsSyncMessage(int dimension, long[] packedXZ) {
        this.dimension = dimension;
        this.packedXZ = packedXZ == null ? new long[0] : packedXZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt();
        int count = buf.readInt();
        if (count < 0) {
            count = 0;
        }
        packedXZ = new long[count];
        for (int i = 0; i < count; i++) {
            packedXZ[i] = buf.readLong();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(packedXZ == null ? 0 : packedXZ.length);
        if (packedXZ != null) {
            for (long v : packedXZ) {
                buf.writeLong(v);
            }
        }
    }

    public static class Handler implements IMessageHandler<TrainPositionsSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainPositionsSyncMessage message, MessageContext ctx) {
            IrAutoMod.PROXY.handleTrainPositionsSync(message.dimension, message.packedXZ);
            return null;
        }
    }
}


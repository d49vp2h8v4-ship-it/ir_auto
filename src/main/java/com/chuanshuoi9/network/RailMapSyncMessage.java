package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Set;

public class RailMapSyncMessage implements IMessage {
    private int dimension;
    private long[] positions;
    private long[] signalPositions;

    public RailMapSyncMessage() {
    }

    public RailMapSyncMessage(int dimension, Set<BlockPos> positions, Set<Long> signalPositions) {
        this.dimension = dimension;
        this.positions = new long[positions.size()];
        int index = 0;
        for (BlockPos pos : positions) {
            this.positions[index++] = pos.toLong();
        }
        
        this.signalPositions = new long[signalPositions.size()];
        index = 0;
        for (long pos : signalPositions) {
            this.signalPositions[index++] = pos;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt();
        int size = buf.readInt();
        positions = new long[size];
        for (int i = 0; i < size; i++) {
            positions[i] = buf.readLong();
        }
        int sigSize = buf.readInt();
        signalPositions = new long[sigSize];
        for (int i = 0; i < sigSize; i++) {
            signalPositions[i] = buf.readLong();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(positions.length);
        for (long position : positions) {
            buf.writeLong(position);
        }
        buf.writeInt(signalPositions.length);
        for (long position : signalPositions) {
            buf.writeLong(position);
        }
    }

    public static class Handler implements IMessageHandler<RailMapSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(RailMapSyncMessage message, MessageContext ctx) {
            IrAutoMod.PROXY.handleRailMapSync(message.dimension, message.positions, message.signalPositions);
            return null;
        }
    }
}

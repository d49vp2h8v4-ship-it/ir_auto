package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class StationSyncMessage implements IMessage {

    public static class StationData {
        public int x, y, z;
        public String name;
        public StationData(int x, int y, int z, String name) {
            this.x = x; this.y = y; this.z = z; this.name = name;
        }
    }

    private List<StationData> stations;

    public StationSyncMessage() {
        this.stations = new ArrayList<>();
    }

    public StationSyncMessage(List<StationData> stations) {
        this.stations = stations;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        stations = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            String name = ByteBufUtils.readUTF8String(buf);
            stations.add(new StationData(x, y, z, name));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(stations.size());
        for (StationData station : stations) {
            buf.writeInt(station.x);
            buf.writeInt(station.y);
            buf.writeInt(station.z);
            ByteBufUtils.writeUTF8String(buf, station.name);
        }
    }

    public static class Handler implements IMessageHandler<StationSyncMessage, IMessage> {
        @Override
        public IMessage onMessage(StationSyncMessage message, MessageContext ctx) {
            IrAutoMod.PROXY.handleStationSync(message.stations);
            return null;
        }
    }
}

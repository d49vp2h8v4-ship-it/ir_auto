package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.IrTrainReflection;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class TrainPositionsRequestMessage implements IMessage {
    private int dimension;

    public TrainPositionsRequestMessage() {
    }

    public TrainPositionsRequestMessage(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
    }

    public static class Handler implements IMessageHandler<TrainPositionsRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainPositionsRequestMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message.dimension));
            return null;
        }

        private void handle(EntityPlayerMP player, int dimension) {
            if (player == null) {
                return;
            }
            WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(dimension);
            if (world == null) {
                return;
            }
            List<Long> packed = new ArrayList<>();
            for (Entity entity : world.loadedEntityList) {
                if (!IrTrainReflection.isLocomotive(entity)) {
                    continue;
                }
                int x = (int) Math.floor(entity.posX);
                int z = (int) Math.floor(entity.posZ);
                long v = (((long) x) << 32) | (((long) z) & 0xFFFFFFFFL);
                packed.add(v);
            }
            long[] arr = new long[packed.size()];
            for (int i = 0; i < packed.size(); i++) {
                arr[i] = packed.get(i);
            }
            IrAutoMod.NETWORK.sendTo(new TrainPositionsSyncMessage(dimension, arr), player);
        }
    }
}


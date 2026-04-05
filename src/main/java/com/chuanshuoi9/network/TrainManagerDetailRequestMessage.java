package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.train.TrainTimetableStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class TrainManagerDetailRequestMessage implements IMessage {
    private String trainUuid;

    public TrainManagerDetailRequestMessage() {
    }

    public TrainManagerDetailRequestMessage(String trainUuid) {
        this.trainUuid = trainUuid == null ? "" : trainUuid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        trainUuid = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, trainUuid == null ? "" : trainUuid);
    }

    public static class Handler implements IMessageHandler<TrainManagerDetailRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainManagerDetailRequestMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message.trainUuid));
            return null;
        }

        private void handle(EntityPlayerMP player, String uuidStr) {
            if (player == null || uuidStr == null || uuidStr.isEmpty()) {
                return;
            }
            UUID id;
            try {
                id = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ex) {
                return;
            }
            World world0 = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
            if (world0 == null) {
                return;
            }
            NBTTagCompound file = TrainTimetableStorage.getTimetable(world0, id);
            if (file == null) {
                file = new NBTTagCompound();
                TrainAutoPilotData.ensureDefaults(file);
                file.setString(TrainAutoPilotData.TRAIN_NUMBER, "");
            } else {
                TrainAutoPilotData.ensureDefaults(file);
            }

            Entity loaded = IrTrainReflection.findBoundTrain(player, id.toString());
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("trainUuid", id.toString());
            payload.setTag("timetable", file);
            boolean online = loaded != null && IrTrainReflection.isLocomotive(loaded);
            payload.setBoolean("online", online);
            if (online) {
                payload.setDouble("speedKmh", Math.abs(IrTrainReflection.getSpeedKmh(loaded)));
                payload.setInteger("dim", loaded.world == null ? 0 : loaded.world.provider.getDimension());
                payload.setLong("pos", new BlockPos(loaded.posX, loaded.posY, loaded.posZ).toLong());
                payload.setTag("live", TrainAutoPilotData.ensureAndGet(loaded).copy());
            } else {
                payload.setDouble("speedKmh", 0.0);
                payload.setInteger("dim", -999);
                payload.setLong("pos", 0L);
            }
            IrAutoMod.NETWORK.sendTo(new TrainManagerDetailSyncMessage(payload), player);
        }
    }
}


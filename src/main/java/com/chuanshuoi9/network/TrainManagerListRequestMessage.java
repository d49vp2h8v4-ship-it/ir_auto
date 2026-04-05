package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.train.TrainTimetableStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Set;
import java.util.UUID;

public class TrainManagerListRequestMessage implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<TrainManagerListRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainManagerListRequestMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player));
            return null;
        }

        private void handle(EntityPlayerMP player) {
            if (player == null) {
                return;
            }
            World world0 = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
            if (world0 == null) {
                return;
            }
            Set<UUID> ids = TrainTimetableStorage.getAllTimetables(world0);
            NBTTagList list = new NBTTagList();
            for (UUID id : ids) {
                if (id == null) {
                    continue;
                }
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("trainUuid", id.toString());
                NBTTagCompound data = TrainTimetableStorage.getTimetable(world0, id);
                String trainNumber = data == null ? "" : TrainAutoPilotData.normalizeTrainNumber(data.getString(TrainAutoPilotData.TRAIN_NUMBER));
                entry.setString("trainNumber", trainNumber);

                Entity loaded = IrTrainReflection.findBoundTrain(player, id.toString());
                boolean online = loaded != null && IrTrainReflection.isLocomotive(loaded);
                entry.setBoolean("online", online);
                if (online) {
                    entry.setDouble("speedKmh", Math.abs(IrTrainReflection.getSpeedKmh(loaded)));
                    entry.setInteger("dim", loaded.world == null ? 0 : loaded.world.provider.getDimension());
                    entry.setLong("pos", new BlockPos(loaded.posX, loaded.posY, loaded.posZ).toLong());
                    NBTTagCompound live = TrainAutoPilotData.ensureAndGet(loaded);
                    entry.setString("nextStop", readNextStopName(live));
                } else {
                    entry.setDouble("speedKmh", 0.0);
                    entry.setInteger("dim", -999);
                    entry.setLong("pos", 0L);
                    entry.setString("nextStop", readNextStopName(data));
                }
                list.appendTag(entry);
            }
            NBTTagCompound payload = new NBTTagCompound();
            payload.setTag("list", list);
            IrAutoMod.NETWORK.sendTo(new TrainManagerListSyncMessage(payload), player);
        }

        private String readNextStopName(NBTTagCompound data) {
            if (data == null) {
                return "";
            }
            if (!data.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)) {
                return "";
            }
            NBTTagList stops = data.getTagList(TrainAutoPilotData.STOPS, Constants.NBT.TAG_COMPOUND);
            if (stops.tagCount() <= 0) {
                return "";
            }
            int index = Math.max(0, Math.min(stops.tagCount() - 1, data.getInteger(TrainAutoPilotData.CURRENT_INDEX)));
            NBTTagCompound stop = stops.getCompoundTagAt(index);
            if (stop.hasKey("name", Constants.NBT.TAG_STRING)) {
                String name = stop.getString("name");
                return name == null ? "" : name;
            }
            return "站台" + (index + 1);
        }
    }
}


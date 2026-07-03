package com.chuanshuoi9.network;

import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.train.TrainTimetableStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class TrainManagerSaveMessage implements IMessage {
    private String trainUuid;
    private NBTTagCompound timetable;

    public TrainManagerSaveMessage() {
    }

    public TrainManagerSaveMessage(String trainUuid, NBTTagCompound timetable) {
        this.trainUuid = trainUuid == null ? "" : trainUuid;
        this.timetable = timetable == null ? new NBTTagCompound() : timetable;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        trainUuid = ByteBufUtils.readUTF8String(buf);
        timetable = ByteBufUtils.readTag(buf);
        if (timetable == null) {
            timetable = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, trainUuid == null ? "" : trainUuid);
        ByteBufUtils.writeTag(buf, timetable);
    }

    public static class Handler implements IMessageHandler<TrainManagerSaveMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainManagerSaveMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message.trainUuid, message.timetable));
            return null;
        }

        private void handle(EntityPlayerMP player, String uuidStr, NBTTagCompound incoming) {
            if (player == null || uuidStr == null || uuidStr.isEmpty()) {
                return;
            }
            UUID id;
            try {
                id = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ex) {
                return;
            }
            net.minecraft.world.World world0 = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
            if (world0 == null) {
                return;
            }
            NBTTagCompound root = incoming == null ? new NBTTagCompound() : incoming.copy();
            TrainAutoPilotData.ensureDefaults(root);
            String normalized = TrainAutoPilotData.normalizeTrainNumber(root.getString(TrainAutoPilotData.TRAIN_NUMBER));
            if (root.getBoolean(TrainAutoPilotData.ENABLED) && normalized.isEmpty()) {
                player.sendMessage(new TextComponentString("必须先设置车次（例如 K1001），否则无法开启自动驾驶"));
                return;
            }
            if (!normalized.isEmpty() && !TrainAutoPilotData.isTrainNumberFormatValid(normalized)) {
                player.sendMessage(new TextComponentString("车次格式无效：仅允许 A-Z 0-9 - _ .，最长 24 个字符"));
                return;
            }
            if (!normalized.isEmpty()) {
                UUID conflict = TrainAutoPilotData.findTrainIdByNumber(world0, normalized, id);
                if (conflict != null) {
                    player.sendMessage(new TextComponentString("车次已被占用：" + normalized + "（冲突列车UUID: " + conflict + "）"));
                    return;
                }
            }
            root.setString(TrainAutoPilotData.TRAIN_NUMBER, normalized);
            if (!root.hasKey(TrainAutoPilotData.STOPS, Constants.NBT.TAG_LIST)) {
                root.setTag(TrainAutoPilotData.STOPS, new net.minecraft.nbt.NBTTagList());
            }
            TrainTimetableStorage.saveTimetable(new DummyEntityWrapper(player, id), root);

            Entity loaded = IrTrainReflection.findBoundTrain(player, id.toString());
            if (loaded != null && IrTrainReflection.isIrTrainEntity(loaded)) {
                TrainAutoPilotData.applyClientConfig(loaded, root);
            }
            player.sendMessage(new TextComponentString("已保存列车自动驾驶文件: " + id));
        }
    }

    private static class DummyEntityWrapper extends net.minecraft.entity.Entity {
        private final UUID id;

        DummyEntityWrapper(EntityPlayerMP player, UUID id) {
            super(player.getEntityWorld());
            this.id = id;
        }

        @Override
        protected void entityInit() {
        }

        @Override
        protected void readEntityFromNBT(NBTTagCompound compound) {
        }

        @Override
        protected void writeEntityToNBT(NBTTagCompound compound) {
        }

        @Override
        public UUID getUniqueID() {
            return id;
        }
    }
}

package com.chuanshuoi9.network;

import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.train.TrainTimetableStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.File;
import java.util.UUID;

public class TrainManagerDeleteMessage implements IMessage {
    private String trainUuid;

    public TrainManagerDeleteMessage() {
    }

    public TrainManagerDeleteMessage(String trainUuid) {
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

    public static class Handler implements IMessageHandler<TrainManagerDeleteMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainManagerDeleteMessage message, MessageContext ctx) {
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
            net.minecraft.world.World world0 = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
            if (world0 == null) {
                return;
            }
            File dir = TrainTimetableStorage.getStorageDir(world0);
            File file = new File(dir, id.toString() + ".dat");
            boolean ok = !file.exists() || file.delete();

            Entity loaded = IrTrainReflection.findBoundTrain(player, id.toString());
            if (loaded != null && IrTrainReflection.isIrTrainEntity(loaded)) {
                NBTTagCompound root = new NBTTagCompound();
                TrainAutoPilotData.ensureDefaults(root);
                root.setBoolean(TrainAutoPilotData.ENABLED, false);
                root.setString(TrainAutoPilotData.TRAIN_NUMBER, "");
                loaded.getEntityData().setTag(TrainAutoPilotData.ROOT, root);
            }

            player.sendMessage(new TextComponentString(ok ? "已删除列车自动驾驶文件: " + id : "删除失败: " + id));
        }
    }
}


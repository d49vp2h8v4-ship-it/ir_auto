package com.chuanshuoi9.network;

import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class TrainControlMessage implements IMessage {
    private String trainUuid;
    private NBTTagCompound payload;

    public TrainControlMessage() {
    }

    public TrainControlMessage(String trainUuid, NBTTagCompound payload) {
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

    public static class Handler implements IMessageHandler<TrainControlMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainControlMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, TrainControlMessage message) {
            if (message.trainUuid == null || message.trainUuid.isEmpty()) {
                player.sendMessage(new TextComponentString("列车标识无效"));
                return;
            }
            Entity train = IrTrainReflection.findBoundTrain(player, message.trainUuid);
            if (train == null) {
                player.sendMessage(new TextComponentString("未找到绑定列车（可能不在已加载区块）"));
                return;
            }
            if (!IrTrainReflection.isIrTrainEntity(train)) {
                player.sendMessage(new TextComponentString("目标不是沉浸铁路列车"));
                return;
            }
            NBTTagCompound payload = message.payload == null ? new NBTTagCompound() : message.payload;

            NBTTagCompound existing = TrainAutoPilotData.ensureAndGet(train);
            String oldNumber = existing.getString(TrainAutoPilotData.TRAIN_NUMBER);
            String incomingNumber = payload.hasKey(TrainAutoPilotData.TRAIN_NUMBER, net.minecraftforge.common.util.Constants.NBT.TAG_STRING)
                ? payload.getString(TrainAutoPilotData.TRAIN_NUMBER)
                : oldNumber;

            String normalized = TrainAutoPilotData.normalizeTrainNumber(incomingNumber);
            boolean enabledRequested = payload.getBoolean(TrainAutoPilotData.ENABLED);
            boolean numberProvidedOrEnabled = enabledRequested || (incomingNumber != null && !incomingNumber.trim().isEmpty());

            if (numberProvidedOrEnabled) {
                if (normalized.isEmpty()) {
                    payload.setBoolean(TrainAutoPilotData.ENABLED, false);
                    player.sendMessage(new TextComponentString("必须先设置车次（例如 K1001），否则无法开启自动驾驶"));
                } else if (!TrainAutoPilotData.isTrainNumberFormatValid(normalized)) {
                    payload.setBoolean(TrainAutoPilotData.ENABLED, false);
                    payload.setString(TrainAutoPilotData.TRAIN_NUMBER, oldNumber == null ? "" : oldNumber);
                    player.sendMessage(new TextComponentString("车次格式无效：仅允许 A-Z 0-9 - _ .，最长 24 个字符"));
                } else {
                    UUID conflict = TrainAutoPilotData.findTrainIdByNumber(player.world, normalized, train.getUniqueID());
                    if (conflict != null) {
                        payload.setBoolean(TrainAutoPilotData.ENABLED, false);
                        payload.setString(TrainAutoPilotData.TRAIN_NUMBER, oldNumber == null ? "" : oldNumber);
                        player.sendMessage(new TextComponentString("车次已被占用：" + normalized + "（冲突列车UUID: " + conflict + "），无法开启自动驾驶"));
                    } else {
                        payload.setString(TrainAutoPilotData.TRAIN_NUMBER, normalized);
                    }
                }
            }

            TrainAutoPilotData.applyClientConfig(train, payload);
            com.chuanshuoi9.map.MapSyncTickHandler.syncStationsToAll();
            NBTTagCompound snap = TrainAutoPilotData.snapshotForClient(train);
            player.sendMessage(new TextComponentString(
                "已保存列车控制: 站台=" + snap.getTagList(TrainAutoPilotData.STOPS, 10).tagCount()
                    + " 当前索引=" + snap.getInteger(TrainAutoPilotData.CURRENT_INDEX)
                    + " 自动驾驶=" + (snap.getBoolean(TrainAutoPilotData.ENABLED) ? "开" : "关")
            ));
            if (train.getUniqueID() != null) {
                player.sendMessage(new TextComponentString("列车UUID: " + train.getUniqueID().toString()));
            }
        }
    }
}

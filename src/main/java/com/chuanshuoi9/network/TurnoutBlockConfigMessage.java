package com.chuanshuoi9.network;

import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.tile.TileTurnoutMachine;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class TurnoutBlockConfigMessage implements IMessage {
    private long posLong;
    private NBTTagCompound payload;

    public TurnoutBlockConfigMessage() {
    }

    public TurnoutBlockConfigMessage(BlockPos pos, boolean blacklistMode, List<String> trainNumbers) {
        this.posLong = pos == null ? 0L : pos.toLong();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("blacklist", blacklistMode);
        NBTTagList list = new NBTTagList();
        if (trainNumbers != null) {
            for (String s : trainNumbers) {
                if (s == null) {
                    continue;
                }
                String n = TrainAutoPilotData.normalizeTrainNumber(s);
                if (!n.isEmpty() && TrainAutoPilotData.isTrainNumberFormatValid(n)) {
                    list.appendTag(new net.minecraft.nbt.NBTTagString(n));
                }
            }
        }
        tag.setTag("trainNumbers", list);
        this.payload = tag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posLong = buf.readLong();
        payload = ByteBufUtils.readTag(buf);
        if (payload == null) {
            payload = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posLong);
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<TurnoutBlockConfigMessage, IMessage> {
        @Override
        public IMessage onMessage(TurnoutBlockConfigMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, TurnoutBlockConfigMessage message) {
            if (player == null) {
                return;
            }
            BlockPos pos = BlockPos.fromLong(message.posLong);
            TileEntity te = player.getServerWorld().getTileEntity(pos);
            if (!(te instanceof TileTurnoutMachine)) {
                return;
            }
            boolean blacklist = message.payload.getBoolean("blacklist");
            List<String> numbers = new ArrayList<>();
            if (message.payload.hasKey("trainNumbers", Constants.NBT.TAG_LIST)) {
                NBTTagList list = message.payload.getTagList("trainNumbers", Constants.NBT.TAG_STRING);
                for (int i = 0; i < list.tagCount(); i++) {
                    String n = TrainAutoPilotData.normalizeTrainNumber(list.getStringTagAt(i));
                    if (!n.isEmpty() && TrainAutoPilotData.isTrainNumberFormatValid(n) && !numbers.contains(n)) {
                        numbers.add(n);
                    }
                }
            }
            ((TileTurnoutMachine) te).applyConfig(blacklist, numbers);
        }
    }
}

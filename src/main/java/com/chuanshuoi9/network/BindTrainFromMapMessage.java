package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.item.ItemTrainControlPaper;
import com.chuanshuoi9.item.ModItems;
import com.chuanshuoi9.train.IrTrainReflection;
import com.chuanshuoi9.train.TrainAutoPilotData;
import com.chuanshuoi9.train.TrainBindingHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class BindTrainFromMapMessage implements IMessage {
    private String trainUuid;

    public BindTrainFromMapMessage() {}

    public BindTrainFromMapMessage(String trainUuid) {
        this.trainUuid = trainUuid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        trainUuid = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, trainUuid);
    }

    public static class Handler implements IMessageHandler<BindTrainFromMapMessage, IMessage> {
        @Override
        public IMessage onMessage(BindTrainFromMapMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Entity train = IrTrainReflection.findBoundTrain(player, message.trainUuid);
                if (train == null) {
                    player.sendMessage(new TextComponentString("未找到选择的列车"));
                    return;
                }
                
                if (!IrTrainReflection.isLocomotive(train)) {
                    player.sendMessage(new TextComponentString("只能绑定机车"));
                    return;
                }

                ItemStack stack = player.getHeldItemMainhand();
                if (stack.getItem() != ModItems.TRAIN_CONTROL_PAPER) {
                    stack = player.getHeldItemOffhand();
                }

                if (stack.getItem() == ModItems.TRAIN_CONTROL_PAPER) {
                    NBTTagCompound tag = stack.getTagCompound();
                    if (tag == null) {
                        tag = new NBTTagCompound();
                        stack.setTagCompound(tag);
                    }
                    tag.setString(ItemTrainControlPaper.NBT_BOUND_TRAIN, message.trainUuid);
                    
                    // Trigger data initialization if needed
                    TrainAutoPilotData.ensureAndGet(train);
                    
                    player.sendMessage(new TextComponentString("已成功绑定列车: " + train.getName()));
                    
                    // Open the timetable GUI
                    NBTTagCompound payload = TrainAutoPilotData.snapshotForClient(train);
                    IrAutoMod.NETWORK.sendTo(new OpenTrainTimetableGuiMessage(message.trainUuid, payload), player);
                }
            });
            return null;
        }
    }
}

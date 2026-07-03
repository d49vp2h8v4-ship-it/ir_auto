package com.chuanshuoi9.network;

import com.chuanshuoi9.item.ItemTurnoutMachine;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TurnoutConfigMessage implements IMessage {
    private int hand;
    private NBTTagCompound config;

    public TurnoutConfigMessage() {
    }

    public TurnoutConfigMessage(EnumHand hand, NBTTagCompound config) {
        this.hand = hand == EnumHand.OFF_HAND ? 1 : 0;
        this.config = config == null ? new NBTTagCompound() : config;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = buf.readInt();
        config = ByteBufUtils.readTag(buf);
        if (config == null) {
            config = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(hand);
        ByteBufUtils.writeTag(buf, config);
    }

    public static class Handler implements IMessageHandler<TurnoutConfigMessage, IMessage> {
        @Override
        public IMessage onMessage(TurnoutConfigMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, TurnoutConfigMessage message) {
            EnumHand h = message.hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack stack = player.getHeldItem(h);
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemTurnoutMachine)) {
                player.sendMessage(new TextComponentString("未手持道岔机"));
                return;
            }
            ItemTurnoutMachine.applyConfigToStack(stack, message.config);
            player.sendMessage(new TextComponentString("道岔机配置已保存"));
        }
    }
}


package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.TimetableTemplateStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Map;
import java.util.UUID;

public class TimetableTemplateListRequestMessage implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<TimetableTemplateListRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(TimetableTemplateListRequestMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player));
            return null;
        }

        private void handle(EntityPlayerMP player) {
            if (player == null || player.getServerWorld() == null) {
                return;
            }
            Map<UUID, String> list = TimetableTemplateStorage.listTemplates(player.getServerWorld());
            NBTTagList out = new NBTTagList();
            for (Map.Entry<UUID, String> e : list.entrySet()) {
                if (e == null || e.getKey() == null) {
                    continue;
                }
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("id", e.getKey().toString());
                entry.setString("name", e.getValue() == null ? "" : e.getValue());
                out.appendTag(entry);
            }
            NBTTagCompound payload = new NBTTagCompound();
            payload.setTag("list", out);
            payload.setInteger("listTagType", Constants.NBT.TAG_COMPOUND);
            IrAutoMod.NETWORK.sendTo(new TimetableTemplateListSyncMessage(payload), player);
        }
    }
}


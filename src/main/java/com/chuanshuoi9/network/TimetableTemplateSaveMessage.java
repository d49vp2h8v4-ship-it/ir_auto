package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.TimetableTemplateStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Map;
import java.util.UUID;

public class TimetableTemplateSaveMessage implements IMessage {
    private NBTTagCompound payload;

    public TimetableTemplateSaveMessage() {
    }

    public TimetableTemplateSaveMessage(NBTTagCompound payload) {
        this.payload = payload == null ? new NBTTagCompound() : payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        payload = ByteBufUtils.readTag(buf);
        if (payload == null) {
            payload = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<TimetableTemplateSaveMessage, IMessage> {
        @Override
        public IMessage onMessage(TimetableTemplateSaveMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message.payload));
            return null;
        }

        private void handle(EntityPlayerMP player, NBTTagCompound payload) {
            if (player == null || payload == null) {
                return;
            }
            String idStr = payload.getString("id");
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            NBTTagCompound data = payload.hasKey("template", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)
                    ? payload.getCompoundTag("template")
                    : new NBTTagCompound();
            data.setString("id", id.toString());
            if (!data.hasKey("name", net.minecraftforge.common.util.Constants.NBT.TAG_STRING)) {
                data.setString("name", "");
            }
            TimetableTemplateStorage.saveTemplate(player.getServerWorld(), id, data);
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
            NBTTagCompound listPayload = new NBTTagCompound();
            listPayload.setTag("list", out);
            IrAutoMod.NETWORK.sendTo(new TimetableTemplateListSyncMessage(listPayload), player);
        }
    }
}

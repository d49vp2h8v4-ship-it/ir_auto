package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.TimetableTemplateStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Map;
import java.util.UUID;

public class TimetableTemplateDeleteMessage implements IMessage {
    private String templateId;

    public TimetableTemplateDeleteMessage() {
    }

    public TimetableTemplateDeleteMessage(String templateId) {
        this.templateId = templateId == null ? "" : templateId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        if (len <= 0 || len > 200) {
            templateId = "";
            return;
        }
        byte[] b = new byte[len];
        buf.readBytes(b);
        templateId = new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] b = templateId == null ? new byte[0] : templateId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(b.length);
        buf.writeBytes(b);
    }

    public static class Handler implements IMessageHandler<TimetableTemplateDeleteMessage, IMessage> {
        @Override
        public IMessage onMessage(TimetableTemplateDeleteMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message.templateId));
            return null;
        }

        private void handle(EntityPlayerMP player, String id) {
            if (player == null || id == null || id.isEmpty()) {
                return;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            TimetableTemplateStorage.deleteTemplate(player.getServerWorld(), uuid);
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


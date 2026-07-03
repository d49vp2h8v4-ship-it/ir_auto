package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.TimetableTemplateStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class TimetableTemplateLoadRequestMessage implements IMessage {
    private String templateId;

    public TimetableTemplateLoadRequestMessage() {
    }

    public TimetableTemplateLoadRequestMessage(String templateId) {
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

    public static class Handler implements IMessageHandler<TimetableTemplateLoadRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(TimetableTemplateLoadRequestMessage message, MessageContext ctx) {
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
            NBTTagCompound data = TimetableTemplateStorage.loadTemplate(player.getServerWorld(), uuid);
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("id", uuid.toString());
            if (data != null) {
                payload.setTag("template", data);
            }
            IrAutoMod.NETWORK.sendTo(new TimetableTemplateLoadSyncMessage(payload), player);
        }
    }
}


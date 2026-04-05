package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.tile.TileTurnoutMachine;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TurnoutStatusRequestMessage implements IMessage {
    private long posLong;

    public TurnoutStatusRequestMessage() {
    }

    public TurnoutStatusRequestMessage(BlockPos pos) {
        this.posLong = pos == null ? 0L : pos.toLong();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posLong = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posLong);
    }

    public static class Handler implements IMessageHandler<TurnoutStatusRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(TurnoutStatusRequestMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private void handle(EntityPlayerMP player, TurnoutStatusRequestMessage message) {
            if (player == null) {
                return;
            }
            World world = player.getServerWorld();
            BlockPos pos = BlockPos.fromLong(message.posLong);
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileTurnoutMachine)) {
                return;
            }
            TileTurnoutMachine turnout = (TileTurnoutMachine) te;
            NBTTagCompound tag = new NBTTagCompound();
            if (turnout.getRailPos() != null) {
                tag.setLong("railPos", turnout.getRailPos().toLong());
            }
            tag.setByte("railFacing", (byte) turnout.getRailFacing().getHorizontalIndex());
            tag.setBoolean("blacklist", turnout.isBlacklistMode());
            NBTTagList list = new NBTTagList();
            for (String s : turnout.getTrainNumbers()) {
                if (s == null) {
                    continue;
                }
                list.appendTag(new net.minecraft.nbt.NBTTagString(s));
            }
            tag.setTag("trainNumbers", list);
            tag.setInteger("outputPower", turnout.getOutputPower());
            tag.setString("lastTrainNumber", turnout.getLastTrainNumber());
            IrAutoMod.NETWORK.sendTo(new TurnoutStatusSyncMessage(pos, tag), player);
        }
    }
}

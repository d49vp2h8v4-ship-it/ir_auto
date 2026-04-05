package com.chuanshuoi9.network;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.BlockTrainDisplay;
import com.chuanshuoi9.block.ModBlocks;
import com.chuanshuoi9.tile.TileTrainDisplay;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class TrainDisplayOpenMessage implements IMessage {
    private long posLong;
    private int side;

    public TrainDisplayOpenMessage() {
    }

    public TrainDisplayOpenMessage(BlockPos pos, EnumFacing side) {
        this.posLong = pos == null ? 0L : pos.toLong();
        this.side = side == null ? 0 : side.getIndex();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posLong = buf.readLong();
        side = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(posLong);
        buf.writeInt(side);
    }

    public static class Handler implements IMessageHandler<TrainDisplayOpenMessage, IMessage> {
        @Override
        public IMessage onMessage(TrainDisplayOpenMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, BlockPos.fromLong(message.posLong), EnumFacing.getFront(message.side)));
            return null;
        }

        private void handle(EntityPlayerMP player, BlockPos pos, EnumFacing facing) {
            if (player == null || pos == null) {
                return;
            }
            World world = player.world;
            if (world == null) {
                return;
            }
            if (world.getBlockState(pos).getBlock() != ModBlocks.TRAIN_DISPLAY) {
                return;
            }
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileTrainDisplay)) {
                return;
            }
            if (facing == null || !facing.getAxis().isHorizontal()) {
                return;
            }
            TileTrainDisplay.ScreenBounds bounds = TileTrainDisplay.detectRectangle(world, pos, facing, BlockTrainDisplay.MAX_SIZE);
            if (bounds == null) {
                return;
            }
            ((TileTrainDisplay) te).applyBoundsAndFace(bounds, facing);
            IrAutoMod.NETWORK.sendTo(new OpenTrainDisplayGuiMessage(bounds.controller), player);
        }
    }
}


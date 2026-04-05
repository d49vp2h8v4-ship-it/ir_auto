package com.chuanshuoi9.item;

import com.chuanshuoi9.block.BlockTrainSignal;
import com.chuanshuoi9.block.ModBlocks;
import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.tile.TileTrainSignal;
import com.chuanshuoi9.util.IrRailUtil;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ItemTrainSignal extends net.minecraft.item.Item {
    private static final String NBT_A = "sigA";
    private static final String NBT_B = "sigB";
    private static final int PLACE_MAX_DISTANCE_FROM_A = 8;

    public ItemTrainSignal() {
        setRegistryName(ModBlocks.TRAIN_SIGNAL.getRegistryName());
        setUnlocalizedName(IrAutoMod.MODID + ".train_signal");
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player == null || world == null || pos == null || hand == null || facing == null) {
            return EnumActionResult.FAIL;
        }
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) {
            return EnumActionResult.FAIL;
        }

        boolean isRail = IrRailUtil.isIrRail(world, pos);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        boolean hasA = tag.hasKey(NBT_A);
        boolean hasB = tag.hasKey(NBT_B);

        if (isRail) {
            if (!hasA) {
                if (!world.isRemote) {
                    tag.setLong(NBT_A, pos.toLong());
                    tag.removeTag(NBT_B);
                }
                return EnumActionResult.SUCCESS;
            }
            if (!hasB) {
                long a = tag.getLong(NBT_A);
                if (!world.isRemote) {
                    if (a == pos.toLong()) {
                        tag.removeTag(NBT_A);
                        tag.removeTag(NBT_B);
                    } else {
                        tag.setLong(NBT_B, pos.toLong());
                    }
                }
                return EnumActionResult.SUCCESS;
            }
            if (!world.isRemote) {
                tag.setLong(NBT_B, pos.toLong());
            }
            return EnumActionResult.SUCCESS;
        }

        if (!hasA || !hasB) {
            return EnumActionResult.FAIL;
        }

        long a = tag.getLong(NBT_A);
        long b = tag.getLong(NBT_B);
        BlockPos railA = BlockPos.fromLong(a);
        BlockPos railB = BlockPos.fromLong(b);

        BlockPos placePos = pos.offset(facing);
        int maxDistSq = PLACE_MAX_DISTANCE_FROM_A * PLACE_MAX_DISTANCE_FROM_A;
        if (placePos.distanceSq(railA) > maxDistSq && pos.distanceSq(railA) > maxDistSq) {
            return EnumActionResult.FAIL;
        }
        if (!world.isBlockLoaded(placePos)) {
            return EnumActionResult.FAIL;
        }
        if (!world.getBlockState(placePos).getBlock().isReplaceable(world, placePos)) {
            return EnumActionResult.FAIL;
        }

        EnumFacing signalFacing = player.getHorizontalFacing();
        int dx = railB.getX() - railA.getX();
        int dz = railB.getZ() - railA.getZ();
        if (Math.abs(dx) > 0 || Math.abs(dz) > 0) {
            if (Math.abs(dx) >= Math.abs(dz)) {
                signalFacing = dx >= 0 ? EnumFacing.EAST : EnumFacing.WEST;
            } else {
                signalFacing = dz >= 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
            }
        }

        IBlockState state = ModBlocks.TRAIN_SIGNAL.getDefaultState();
        if (state.getPropertyKeys().contains(BlockHorizontal.FACING)) {
            state = state.withProperty(BlockHorizontal.FACING, signalFacing);
        }

        if (!world.isRemote) {
            world.setBlockState(placePos, state, 3);
            TileEntity te = world.getTileEntity(placePos);
            if (te instanceof TileTrainSignal) {
                ((TileTrainSignal) te).setRailA(railA.toImmutable());
                ((TileTrainSignal) te).setRailB(railB.toImmutable());
                ((TileTrainSignal) te).setFacing(signalFacing);
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            tag.removeTag(NBT_A);
            tag.removeTag(NBT_B);
        }

        return EnumActionResult.SUCCESS;
    }
}

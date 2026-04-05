package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.ModBlocks;
import com.chuanshuoi9.tile.TileStationMarker;
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

public class ItemStationMarker extends net.minecraft.item.Item {
    public static final String NBT_RAIL = "smRail";
    private static final int PLACE_MAX_DISTANCE_FROM_RAIL = 8;

    public ItemStationMarker() {
        setRegistryName(ModBlocks.STATION_MARKER.getRegistryName());
        setUnlocalizedName(IrAutoMod.MODID + ".station_marker");
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
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

        boolean hasRail = tag.hasKey(NBT_RAIL);

        if (isRail) {
            if (!world.isRemote) {
                long cur = pos.toLong();
                if (hasRail && tag.getLong(NBT_RAIL) == cur) {
                    tag.removeTag(NBT_RAIL);
                } else {
                    tag.setLong(NBT_RAIL, cur);
                }
            }
            return EnumActionResult.SUCCESS;
        }

        if (!hasRail) {
            return EnumActionResult.FAIL;
        }

        BlockPos railPos = BlockPos.fromLong(tag.getLong(NBT_RAIL));
        BlockPos placePos = pos.offset(facing);
        int maxDistSq = PLACE_MAX_DISTANCE_FROM_RAIL * PLACE_MAX_DISTANCE_FROM_RAIL;
        if (placePos.distanceSq(railPos) > maxDistSq && pos.distanceSq(railPos) > maxDistSq) {
            return EnumActionResult.FAIL;
        }
        if (!world.getBlockState(placePos).getBlock().isReplaceable(world, placePos)) {
            return EnumActionResult.FAIL;
        }

        EnumFacing blockFacing = player.getHorizontalFacing();
        IBlockState state = ModBlocks.STATION_MARKER.getDefaultState();
        if (state.getPropertyKeys().contains(BlockHorizontal.FACING)) {
            state = state.withProperty(BlockHorizontal.FACING, blockFacing);
        }

        if (!world.isRemote) {
            world.setBlockState(placePos, state, 3);
            TileEntity te = world.getTileEntity(placePos);
            if (te instanceof TileStationMarker) {
                ((TileStationMarker) te).configure(railPos.toImmutable(), "站台");
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            tag.removeTag(NBT_RAIL);
        }

        return EnumActionResult.SUCCESS;
    }
}

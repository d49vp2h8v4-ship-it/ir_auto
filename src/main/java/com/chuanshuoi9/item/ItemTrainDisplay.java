package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.ModBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ItemTrainDisplay extends net.minecraft.item.Item {
    public ItemTrainDisplay() {
        setRegistryName(ModBlocks.TRAIN_DISPLAY.getRegistryName());
        setUnlocalizedName(IrAutoMod.MODID + ".train_display");
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) {
            return EnumActionResult.FAIL;
        }
        BlockPos placePos = pos.offset(facing);
        if (!world.getBlockState(placePos).getBlock().isReplaceable(world, placePos)) {
            return EnumActionResult.FAIL;
        }
        IBlockState state = ModBlocks.TRAIN_DISPLAY.getDefaultState();
        if (!world.isRemote) {
            world.setBlockState(placePos, state, 3);
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
        }
        return EnumActionResult.SUCCESS;
    }
}


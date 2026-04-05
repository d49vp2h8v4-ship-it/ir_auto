package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.BlockSignalLight;
import com.chuanshuoi9.block.ModBlocks;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ItemSignalLight extends net.minecraft.item.Item {
    public ItemSignalLight() {
        setRegistryName(ModBlocks.SIGNAL_LIGHT.getRegistryName());
        setUnlocalizedName(IrAutoMod.MODID + ".signal_light");
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

        EnumFacing blockFacing = player.getHorizontalFacing();
        boolean powered = world.isBlockPowered(placePos);

        IBlockState state = ModBlocks.SIGNAL_LIGHT.getDefaultState();
        if (state.getPropertyKeys().contains(BlockHorizontal.FACING)) {
            state = state.withProperty(BlockHorizontal.FACING, blockFacing);
        }
        if (state.getPropertyKeys().contains(BlockSignalLight.POWERED)) {
            state = state.withProperty(BlockSignalLight.POWERED, powered);
        }

        if (!world.isRemote) {
            world.setBlockState(placePos, state, 3);
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
        }

        return EnumActionResult.SUCCESS;
    }
}

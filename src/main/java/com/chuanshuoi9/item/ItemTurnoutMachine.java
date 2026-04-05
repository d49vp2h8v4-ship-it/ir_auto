package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.ModBlocks;
import com.chuanshuoi9.tile.TileTurnoutMachine;
import com.chuanshuoi9.util.IrRailUtil;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class ItemTurnoutMachine extends net.minecraft.item.Item {
    public static final String NBT_RAIL = "tmRail";
    public static final String NBT_FACING = "tmFacing";
    public static final String NBT_MATCH_TRIGGERS = "tmMatchTriggers";
    public static final String NBT_LIST = "tmTrainNumbers";
    private static final int PLACE_MAX_DISTANCE_FROM_RAIL = 8;

    public ItemTurnoutMachine() {
        setRegistryName(ModBlocks.TURNOUT_MACHINE.getRegistryName());
        setUnlocalizedName(IrAutoMod.MODID + ".turnout_machine");
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        if (worldIn.isRemote) {
            IrAutoMod.PROXY.openTurnoutMachineConfigGui(handIn, stack);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
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
                    tag.removeTag(NBT_FACING);
                } else {
                    tag.setLong(NBT_RAIL, cur);
                    EnumFacing dir = player.getHorizontalFacing();
                    tag.setByte(NBT_FACING, (byte) dir.getHorizontalIndex());
                }
            }
            return EnumActionResult.SUCCESS;
        }

        if (!hasRail) {
            return EnumActionResult.FAIL;
        }

        BlockPos railPos = BlockPos.fromLong(tag.getLong(NBT_RAIL));
        EnumFacing railFacing = EnumFacing.getHorizontal(tag.getByte(NBT_FACING));

        BlockPos placePos = pos.offset(facing);
        int maxDistSq = PLACE_MAX_DISTANCE_FROM_RAIL * PLACE_MAX_DISTANCE_FROM_RAIL;
        if (placePos.distanceSq(railPos) > maxDistSq && pos.distanceSq(railPos) > maxDistSq) {
            return EnumActionResult.FAIL;
        }
        if (!world.getBlockState(placePos).getBlock().isReplaceable(world, placePos)) {
            return EnumActionResult.FAIL;
        }

        EnumFacing blockFacing = player.getHorizontalFacing();
        int dx = railPos.getX() - placePos.getX();
        int dz = railPos.getZ() - placePos.getZ();
        if (dx != 0 || dz != 0) {
            if (Math.abs(dx) >= Math.abs(dz)) {
                blockFacing = dx >= 0 ? EnumFacing.EAST : EnumFacing.WEST;
            } else {
                blockFacing = dz >= 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
            }
        }
        IBlockState state = ModBlocks.TURNOUT_MACHINE.getDefaultState();
        if (state.getPropertyKeys().contains(BlockHorizontal.FACING)) {
            state = state.withProperty(BlockHorizontal.FACING, blockFacing);
        }

        if (!world.isRemote) {
            world.setBlockState(placePos, state, 3);
            TileEntity te = world.getTileEntity(placePos);
            if (te instanceof TileTurnoutMachine) {
                boolean matchTriggers = !tag.hasKey(NBT_MATCH_TRIGGERS, Constants.NBT.TAG_BYTE) || tag.getBoolean(NBT_MATCH_TRIGGERS);
                List<String> numbers = new ArrayList<>();
                if (tag.hasKey(NBT_LIST, Constants.NBT.TAG_LIST)) {
                    NBTTagList list = tag.getTagList(NBT_LIST, Constants.NBT.TAG_STRING);
                    for (int i = 0; i < list.tagCount(); i++) {
                        String s = list.getStringTagAt(i);
                        if (s != null && !s.trim().isEmpty()) {
                            numbers.add(s);
                        }
                    }
                }
                ((TileTurnoutMachine) te).configure(railPos.toImmutable(), railFacing, matchTriggers, numbers);
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            tag.removeTag(NBT_RAIL);
            tag.removeTag(NBT_FACING);
        }

        return EnumActionResult.SUCCESS;
    }

    public static NBTTagCompound buildConfigTag(boolean matchTriggers, List<String> trainNumbers) {
        NBTTagCompound out = new NBTTagCompound();
        out.setBoolean(NBT_MATCH_TRIGGERS, matchTriggers);
        NBTTagList list = new NBTTagList();
        if (trainNumbers != null) {
            for (String s : trainNumbers) {
                if (s == null) {
                    continue;
                }
                String v = s.trim();
                if (!v.isEmpty()) {
                    list.appendTag(new net.minecraft.nbt.NBTTagString(v));
                }
            }
        }
        out.setTag(NBT_LIST, list);
        return out;
    }

    public static void applyConfigToStack(ItemStack stack, NBTTagCompound config) {
        if (stack == null || stack.isEmpty() || config == null) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean(NBT_MATCH_TRIGGERS, config.getBoolean(NBT_MATCH_TRIGGERS));
        if (config.hasKey(NBT_LIST, Constants.NBT.TAG_LIST)) {
            tag.setTag(NBT_LIST, config.getTagList(NBT_LIST, Constants.NBT.TAG_STRING));
        } else {
            tag.removeTag(NBT_LIST);
        }
    }
}

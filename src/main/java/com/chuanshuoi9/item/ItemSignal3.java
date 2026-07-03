package com.chuanshuoi9.item;

import com.chuanshuoi9.block.ModBlocks;
import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.tile.TileSignal3;
import com.chuanshuoi9.util.IrRailUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * 三状态信号机物品。
 * 1. 右键铁轨 → 选区间起点 A
 * 2. 右键另一铁轨 → 选区间终点 B
 * 3. 右键地面 → 放信号机（区间 A→B）
 * 4. Shift+右键已有信号机 → 记录为链接触点
 *    右键另一信号机 → 完成上下游联动
 */
public class ItemSignal3 extends net.minecraft.item.Item {
    private static final String NBT_A = "sigA";
    private static final String NBT_B = "sigB";
    private static final String LINK = "linkTarget";
    private static final String LAST = "lastB";

    public ItemSignal3() {
        setRegistryName(ModBlocks.SIGNAL_3.getRegistryName());
        setUnlocalizedName(IrAutoMod.MODID + ".signal_3");
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                       EnumHand hand, EnumFacing facing,
                                       float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) { tag = new NBTTagCompound(); stack.setTagCompound(tag); }

        boolean isRail = IrRailUtil.isIrRail(world, pos);
        boolean isSignal = world.getTileEntity(pos) instanceof TileSignal3;
        boolean hasA = tag.hasKey(NBT_A), hasB = tag.hasKey(NBT_B);

        // Shift+右键信号机 → 记录链接触点
        if (player.isSneaking() && isSignal) {
            tag.setLong(LINK, pos.toLong());
            tag.removeTag(NBT_A); tag.removeTag(NBT_B);
            player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "已记录链接触点，右键另一个信号机完成联动。Shift+空气取消。"));
            return EnumActionResult.SUCCESS;
        }

        // 右键信号机 + NBT 有链接触点 → 建立联动
        if (isSignal && tag.hasKey(LINK)) {
            BlockPos target = BlockPos.fromLong(tag.getLong(LINK));
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileSignal3) {
                if (target.equals(pos)) {
                    player.sendMessage(new TextComponentString(TextFormatting.RED + "不能链接自身。"));
                } else {
                    ((TileSignal3) te).prevSignal = target;
                    te.markDirty();
                    player.sendMessage(new TextComponentString(
                        TextFormatting.GREEN + "已联动：当前信号机的上游 = " + fmt(target)));
                }
            }
            tag.removeTag(LINK);
            return EnumActionResult.SUCCESS;
        }

        // 右键 IR 铁轨 → 选区间的 A/B
        if (isRail) {
            // Auto-continue: if last placed B matches this position, use it as new A
            if (!hasA && tag.hasKey(LAST)) {
                long lastB = tag.getLong(LAST);
                if (pos.toLong() == lastB) {
                    tag.setLong(NBT_A, lastB);
                    tag.removeTag(LAST);
                    tag.removeTag(NBT_B);
                    player.sendMessage(new TextComponentString(
                        TextFormatting.GREEN + "自动续接：起点 A = " + fmt(pos) + "（上段终点），请选新终点"));
                    return EnumActionResult.SUCCESS;
                }
            }

            if (!hasA) {
                tag.setLong(NBT_A, pos.toLong()); tag.removeTag(NBT_B);
                player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "区间起点 A 已选: " + fmt(pos) + " ，请选终点 B"));
            } else if (!hasB) {
                long a = tag.getLong(NBT_A);
                if (a == pos.toLong()) {
                    tag.removeTag(NBT_A);
                    player.sendMessage(new TextComponentString(TextFormatting.RED + "起点 A 已清除。"));
                } else {
                    tag.setLong(NBT_B, pos.toLong());
                    player.sendMessage(new TextComponentString(
                        TextFormatting.GREEN + "区间终点 B 已选: " + fmt(pos) + " ，右击地面放置"));
                }
            } else {
                tag.setLong(NBT_B, pos.toLong());
                player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "区间终点 B 已更新: " + fmt(pos)));
            }
            return EnumActionResult.SUCCESS;
        }

        // 右键非铁轨 → 放置信号机
        if (!hasA || !hasB) return EnumActionResult.FAIL;
        tag.removeTag(LINK);

        BlockPos railA = BlockPos.fromLong(tag.getLong(NBT_A));
        BlockPos railB = BlockPos.fromLong(tag.getLong(NBT_B));
        BlockPos placePos = pos.offset(facing);
        if (!world.getBlockState(placePos).getBlock().isReplaceable(world, placePos))
            return EnumActionResult.FAIL;

        world.setBlockState(placePos, ModBlocks.SIGNAL_3.getDefaultState(), 3);
        TileEntity te = world.getTileEntity(placePos);
        if (te instanceof TileSignal3) {
            ((TileSignal3) te).setRailA(railA.toImmutable());
            ((TileSignal3) te).setRailB(railB.toImmutable());
        }
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        tag.removeTag(NBT_A); tag.removeTag(NBT_B);
        tag.setLong(LAST, railB.toLong()); // 记住终点，下次自动续接
        player.sendMessage(new TextComponentString(
            TextFormatting.GREEN + "三状态信号机已放置，区间 " + fmt(railA) + " \u2192 " + fmt(railB)));
        return EnumActionResult.SUCCESS;
    }

    private static String fmt(BlockPos p) { return "["+p.getX()+","+p.getY()+","+p.getZ()+"]"; }
}

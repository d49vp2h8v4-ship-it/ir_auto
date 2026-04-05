package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.train.TrainBindingHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public class ItemTrainControlPaper extends Item {
    public static final String NBT_BOUND_TRAIN = "boundTrain";

    public ItemTrainControlPaper() {
        setRegistryName(IrAutoMod.MODID, "train_control_paper");
        setUnlocalizedName(IrAutoMod.MODID + ".train_control_paper");
        setMaxStackSize(1);
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        if (world.isRemote) {
            openMapGui(stack);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private void openMapGui(ItemStack stack) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new com.chuanshuoi9.client.gui.GuiTrainBindingMap(stack));
    }
}

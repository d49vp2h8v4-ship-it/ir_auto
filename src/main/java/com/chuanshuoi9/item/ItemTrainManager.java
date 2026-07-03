package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.block.ModBlocks;
import com.chuanshuoi9.network.TrainDisplayOpenMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemTrainManager extends Item {
    public ItemTrainManager() {
        setRegistryName(IrAutoMod.MODID, "train_manager");
        setUnlocalizedName(IrAutoMod.MODID + ".train_manager");
        setMaxStackSize(1);
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player != null && player.isSneaking()) {
            RayTraceResult hit = rayTrace(world, player, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && hit.getBlockPos() != null) {
                if (world.getBlockState(hit.getBlockPos()).getBlock() == ModBlocks.TRAIN_DISPLAY) {
                    if (world.isRemote) {
                        IrAutoMod.NETWORK.sendToServer(new TrainDisplayOpenMessage(hit.getBlockPos(), hit.sideHit));
                    }
                    return new ActionResult<>(EnumActionResult.SUCCESS, stack);
                }
            }
        }
        if (world.isRemote) {
            openClientGui();
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)
    private void openClientGui() {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new com.chuanshuoi9.client.gui.GuiTrainManager());
    }
}

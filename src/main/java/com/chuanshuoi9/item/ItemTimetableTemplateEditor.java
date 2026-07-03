package com.chuanshuoi9.item;

import com.chuanshuoi9.IrAutoMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemTimetableTemplateEditor extends Item {
    public ItemTimetableTemplateEditor() {
        setRegistryName(IrAutoMod.MODID, "timetable_template_editor");
        setUnlocalizedName(IrAutoMod.MODID + ".timetable_template_editor");
        setMaxStackSize(1);
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            openClientGui();
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)
    private void openClientGui() {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new com.chuanshuoi9.client.gui.GuiTimetableTemplateEditor());
    }
}

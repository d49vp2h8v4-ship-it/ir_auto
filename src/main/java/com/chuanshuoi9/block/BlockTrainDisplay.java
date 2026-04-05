package com.chuanshuoi9.block;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.item.ModItems;
import com.chuanshuoi9.network.OpenTrainDisplayGuiMessage;
import com.chuanshuoi9.tile.TileTrainDisplay;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class BlockTrainDisplay extends Block implements ITileEntityProvider {
    public static final int MAX_SIZE = 20;

    public BlockTrainDisplay() {
        super(Material.ROCK);
        setRegistryName(IrAutoMod.MODID, "train_display");
        setUnlocalizedName(IrAutoMod.MODID + ".train_display");
        setHardness(2.0f);
        setResistance(10.0f);
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileTrainDisplay();
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            return true;
        }
        if (!playerIn.isSneaking()) {
            return true;
        }
        if (playerIn.getHeldItem(hand).getItem() != ModItems.TRAIN_MANAGER) {
            return true;
        }
        if (!(playerIn instanceof EntityPlayerMP)) {
            return true;
        }
        TileEntity te = worldIn.getTileEntity(pos);
        if (!(te instanceof TileTrainDisplay)) {
            return true;
        }
        if (!facing.getAxis().isHorizontal()) {
            playerIn.sendMessage(new TextComponentString("请点击大屏正面（南北/东西方向）"));
            return true;
        }
        TileTrainDisplay tile = (TileTrainDisplay) te;
        TileTrainDisplay.ScreenBounds bounds = TileTrainDisplay.detectRectangle(worldIn, pos, facing, MAX_SIZE);
        if (bounds == null) {
            playerIn.sendMessage(new TextComponentString("多方块结构不完整：需要竖向矩形，且必须填满，最大 20x20"));
            return true;
        }
        tile.applyBoundsAndFace(bounds, facing);
        IrAutoMod.NETWORK.sendTo(new OpenTrainDisplayGuiMessage(bounds.controller), (EntityPlayerMP) playerIn);
        return true;
    }
}


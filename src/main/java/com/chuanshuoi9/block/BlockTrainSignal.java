package com.chuanshuoi9.block;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.tile.TileTrainSignal;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

public class BlockTrainSignal extends BlockHorizontal implements ITileEntityProvider {

    public BlockTrainSignal() {
        super(Material.IRON);
        setRegistryName(IrAutoMod.MODID, "train_signal");
        setUnlocalizedName(IrAutoMod.MODID + ".train_signal");
        setHardness(3.0f);
        setResistance(10.0f);
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.getHorizontal(meta & 3);
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (placer != null) {
            EnumFacing placedFacing = placer.getHorizontalFacing();
            worldIn.setBlockState(pos, state.withProperty(FACING, placedFacing), 2);
            TileEntity te = worldIn.getTileEntity(pos);
            if (te instanceof TileTrainSignal) {
                ((TileTrainSignal) te).setFacing(placedFacing);
            }
        }
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, net.minecraft.entity.player.EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            IrAutoMod.PROXY.openTrainSignalStatusGui(pos);
            return true;
        }
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TileTrainSignal) {
            ((TileTrainSignal) te).setFacing(state.getValue(FACING));
        }
        return true;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileTrainSignal();
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean useNeighborBrightness(IBlockState state) {
        return true;
    }
}

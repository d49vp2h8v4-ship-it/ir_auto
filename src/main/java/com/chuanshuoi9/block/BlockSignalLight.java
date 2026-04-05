package com.chuanshuoi9.block;

import com.chuanshuoi9.IrAutoMod;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

public class BlockSignalLight extends BlockHorizontal {
    public static final PropertyBool POWERED = PropertyBool.create("powered");

    private static final AxisAlignedBB AABB_NORTH = new AxisAlignedBB(0.6875, 0.0, 0.25, 1.0, 1.0, 0.75);
    private static final AxisAlignedBB AABB_EAST = new AxisAlignedBB(0.25, 0.0, 0.6875, 0.75, 1.0, 1.0);
    private static final AxisAlignedBB AABB_SOUTH = new AxisAlignedBB(0.0, 0.0, 0.25, 0.3125, 1.0, 0.75);
    private static final AxisAlignedBB AABB_WEST = new AxisAlignedBB(0.25, 0.0, 0.0, 0.75, 1.0, 0.3125);

    public BlockSignalLight() {
        super(Material.IRON);
        setRegistryName(IrAutoMod.MODID, "signal_light");
        setUnlocalizedName(IrAutoMod.MODID + ".signal_light");
        setHardness(3.0f);
        setResistance(10.0f);
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(POWERED, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, POWERED);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(FACING).getHorizontalIndex();
        if (state.getValue(POWERED)) {
            meta |= 4;
        }
        return meta;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.getHorizontal(meta & 3);
        boolean powered = (meta & 4) != 0;
        return getDefaultState().withProperty(FACING, facing).withProperty(POWERED, powered);
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (placer != null) {
            worldIn.setBlockState(pos, state.withProperty(FACING, placer.getHorizontalFacing()), 2);
        }
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
    }

    @Override
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state) {
        if (!worldIn.isRemote) {
            updatePoweredState(worldIn, pos, state);
        }
        super.onBlockAdded(worldIn, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, net.minecraft.block.Block blockIn, BlockPos fromPos) {
        if (!worldIn.isRemote) {
            updatePoweredState(worldIn, pos, state);
        }
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
    }

    private void updatePoweredState(World world, BlockPos pos, IBlockState state) {
        boolean powered = world.isBlockPowered(pos);
        if (powered != state.getValue(POWERED)) {
            world.setBlockState(pos, state.withProperty(POWERED, powered), 2);
        }
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

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return getAabb(state);
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return getAabb(state);
    }

    private AxisAlignedBB getAabb(IBlockState state) {
        EnumFacing f = state.getValue(FACING);
        if (f == EnumFacing.SOUTH) {
            return AABB_SOUTH;
        }
        if (f == EnumFacing.WEST) {
            return AABB_WEST;
        }
        if (f == EnumFacing.EAST) {
            return AABB_EAST;
        }
        return AABB_NORTH;
    }
}

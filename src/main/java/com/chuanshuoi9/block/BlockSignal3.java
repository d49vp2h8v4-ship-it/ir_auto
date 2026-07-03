package com.chuanshuoi9.block;

import com.chuanshuoi9.IrAutoMod;
import com.chuanshuoi9.signal.TrainSignalController;
import com.chuanshuoi9.signal.Signal3TickHandler;
import com.chuanshuoi9.tile.TileSignal3;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

public class BlockSignal3 extends BlockContainer {
    public static final PropertyBool RED    = PropertyBool.create("red");
    public static final PropertyBool YELLOW = PropertyBool.create("yellow");
    public static final PropertyBool GREEN  = PropertyBool.create("green");

    public BlockSignal3() {
        super(Material.IRON);
        setRegistryName(IrAutoMod.MODID, "signal_3");
        setUnlocalizedName(IrAutoMod.MODID + ".signal_3");
        setHardness(3f); setResistance(10f);
        setCreativeTab(IrAutoMod.CREATIVE_TAB);
        setDefaultState(blockState.getBaseState()
            .withProperty(RED, true).withProperty(YELLOW, false).withProperty(GREEN, false));
    }

    @Override public TileEntity createNewTileEntity(World w, int m) { return new TileSignal3(); }
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, RED, YELLOW, GREEN); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState(); }
    @Override public int getMetaFromState(IBlockState s) { return 0; }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                     EntityPlayer player, EnumHand hand,
                                     EnumFacing facing, float hx, float hy, float hz) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileSignal3)) return true;
        TileSignal3 sig = (TileSignal3) te;
        String[] l = {"RED","YELLOW","GREEN"};
        player.sendMessage(new TextComponentString(TextFormatting.GOLD +
            "Signal: " + l[sig.getAspect()] + "  A=" + sig.getRailA() + " B=" + sig.getRailB()));
        return true;
    }

    @Override @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }

    private static final AxisAlignedBB AABB = new AxisAlignedBB(0.2, 0, 0.3, 0.8, 1, 0.7);
    @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return AABB; }
    @Nullable @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return AABB; }
}

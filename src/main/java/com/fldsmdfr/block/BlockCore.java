package com.fldsmdfr.block;

import com.fldsmdfr.FldsmdfrMod;
import com.fldsmdfr.core.MachineBody;
import com.fldsmdfr.core.MachineModel;
import com.fldsmdfr.tile.TileEntityFldsmdfr;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/** The FLDSMDFR machine core. Drawn entirely by the tile entity renderer. */
public class BlockCore extends BlockContainer {
    public BlockCore() {
        super(Material.iron);
        setUnlocalizedName("fldsmdfr.core");
        setHardness(5.0F);
        setResistance(30.0F);
        setStepSound(Block.soundTypeMetal);
        setLightLevel(0.6F);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public int getRenderType() { return -1; }

    @Override
    public boolean isOpaqueCube() { return false; }

    @Override
    public boolean isFullCube() { return false; }

    /**
     * Collision follows the current central body. The box starts just above the lower
     * aperture, so food spawned below the mouth never starts inside the block and can
     * fall freely. The horizontal size comes from the deformed shell (including lean),
     * not from a fixed 1x1 cube.
     */
    @Override
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        return bodyBounds(world, pos);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBox(World world, BlockPos pos) {
        return bodyBounds(world, pos);
    }

    private static AxisAlignedBB bodyBounds(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFldsmdfr)) {
            return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        }
        TileEntityFldsmdfr m = (TileEntityFldsmdfr) te;
        // The server owns the authoritative state; the client only receives MachineView.
        long seed = m.state.seed != 0 ? m.state.seed : m.view.seed;
        double tier = m.state.seed != 0 ? m.state.tier() : m.view.tier;
        if (Double.isNaN(tier) || Double.isInfinite(tier)) tier = 0.0;
        double scale = MachineModel.scale(Math.max(0.0, tier));
        MachineBody.Form body = MachineBody.form(seed, tier);

        double half = Math.max(0.9, body.maxHalfExtent() * scale + 0.06);
        double bottom = pos.getY() + MachineBody.LIFT * scale + 0.02;
        double top = pos.getY() + (MachineBody.LIFT + body.height) * scale + 0.55 * scale + 0.05;
        if (top < bottom + 0.10) top = bottom + 0.10;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        return new AxisAlignedBB(cx - half, bottom, cz - half, cx + half, top, cz + half);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) { return new TileEntityFldsmdfr(); }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityFldsmdfr) {
            ((TileEntityFldsmdfr) te).facing = placer.getHorizontalFacing().getOpposite().getHorizontalIndex();
            te.markDirty();
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFldsmdfr)) return true;
        TileEntityFldsmdfr m = (TileEntityFldsmdfr) te;
        if (player.isSneaking() && player.getHeldItem() == null) {
            m.state.running = !m.state.running;
            m.markDirty();
            player.addChatMessage(new ChatComponentText("FLDSMDFR: " + (m.state.running ? "ON" : "OFF")));
            m.syncNow();
            return true;
        }
        m.syncNow();
        player.openGui(FldsmdfrMod.instance, 0, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
}

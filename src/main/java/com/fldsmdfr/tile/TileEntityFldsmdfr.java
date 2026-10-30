package com.fldsmdfr.tile;

import com.fldsmdfr.config.ModConfig;
import com.fldsmdfr.core.LaunchSystem;
import com.fldsmdfr.core.MachineBody;
import com.fldsmdfr.core.MachineModel;
import com.fldsmdfr.core.MachineState;
import com.fldsmdfr.core.MachineView;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.world.WorldServer;

/**
 * Server side: runs the logic (evolution, production, launching). Client side: only holds the
 * small {@link MachineView} snapshot used by the renderer and the GUI.
 */
public class TileEntityFldsmdfr extends TileEntity implements ITickable {
    public final MachineState state = new MachineState();
    public final MachineView view = new MachineView();
    public int facing; // horizontal index of the direction the machine front faces

    private int ticks;
    private boolean offlineChecked;
    private int activeFood;

    @Override
    public void update() {
        if (worldObj == null || worldObj.isRemote) return;
        if (state.seed == 0) state.initNew(pos.toLong());
        if (!offlineChecked) {
            offlineChecked = true;
            long elapsed = System.currentTimeMillis() - state.lastActiveMs;
            if (elapsed > 5000) state.applyOffline(elapsed);
            state.lastActiveMs = System.currentTimeMillis();
        }
        ticks++;
        int interval = Math.max(1, ModConfig.i("updateInterval"));
        if (ticks % interval == 0) step(interval);
        if (ticks % Math.max(5, ModConfig.i("syncInterval")) == 0) syncNow();
        if (ticks % 100 == 0) {
            state.lastActiveMs = System.currentTimeMillis();
            markDirty();
        }
    }

    private void step(int interval) {
        state.advance(interval);
        if (state.surgeStarted) {
            state.surgeStarted = false;
            if (ModConfig.b("sounds")) worldObj.playSoundEffect(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, "random.explode", 0.6f, 0.5f);
        }
        if (!state.running) return;
        if (ticks % 40 == 0) activeFood = LaunchSystem.recount(worldObj, ModConfig.i("maxActiveFood"));
        if (worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) worldObj;
            activeFood += LaunchSystem.update(ws, pos, facing, state, interval, activeFood);
            effects(ws);
        }
    }

    /** Smoke, sparks and arcs on the central body and at the lower discharge mouth. */
    private void effects(WorldServer w) {
        if (!ModConfig.b("visualEffects")) return;
        double sat = state.sat();
        if (sat < 0.05 || state.rng.nextDouble() > sat * 0.8 + 0.05) return;
        double tier = state.tier();
        double scale = MachineModel.scale(tier);
        MachineBody.Form body = MachineBody.form(state.seed, tier);
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        double facingDeg = (facing & 3) * 90.0;
        double worldTime = w.getTotalWorldTime();

        // The main effect is always under the machine, at the real discharge mouth.
        double[] ap = body.aperturePoint(state.rng.nextDouble() * Math.PI * 2.0, 0.20 + 0.65 * state.rng.nextDouble());
        ap[1] += MachineBody.apertureBob(worldTime, sat);
        double[] o = MachineModel.toWorldOffset(ap, scale, facingDeg);
        double px = cx + o[0], py = cy + o[1], pz = cz + o[2];
        w.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, px, py, pz, 2 + (int) (sat * 6), 0.28, 0.16, 0.28, 0.02);
        w.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, px, py, pz, 1 + (int) (sat * 5), 0.35, 0.35, 0.35, 0.05);

        // A few sparks crawl over the shell; they are attached to the body, not to detached parts.
        int n = 1 + (int) (sat * 2.0);
        for (int i = 0; i < n; i++) {
            double theta = state.rng.nextDouble() * Math.PI * 2.0;
            double t = 0.25 + 0.55 * state.rng.nextDouble();
            double[] sp = body.surfacePoint(theta, t);
            double[] so = MachineModel.toWorldOffset(sp, scale, facingDeg);
            w.spawnParticle(EnumParticleTypes.CRIT_MAGIC, cx + so[0], cy + so[1], cz + so[2],
                    1, 0.25, 0.25, 0.25, 0.02);
        }
        if (ModConfig.b("sounds") && state.rng.nextDouble() < sat * 0.05) {
            w.playSoundEffect(px, py, pz, "random.fizz", 0.6f, 0.5f + state.rng.nextFloat());
        }
    }

    /** Refreshes the client snapshot and sends it to nearby players. */
    public void syncNow() {
        if (worldObj == null || worldObj.isRemote) return;
        state.fillView(view, activeFood, facing);
        worldObj.markBlockForUpdate(pos);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        state.fillView(view, activeFood, facing);
        view.write(t);
        return new S35PacketUpdateTileEntity(pos, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        view.read(pkt.getNbtCompound());
        facing = view.facing;
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        state.lastActiveMs = System.currentTimeMillis();
        state.write(t);
        t.setInteger("facing", facing);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        state.read(t);
        facing = t.getInteger("facing");
        offlineChecked = false;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        double e = Math.max(4.0, MachineBody.horizontalExtent(view.seed, view.tier, view.scale));
        double top = MachineBody.topY(view.seed, view.tier, view.scale);
        return new AxisAlignedBB(pos.getX() + 0.5 - e, pos.getY() - 1.0, pos.getZ() + 0.5 - e,
                pos.getX() + 0.5 + e, pos.getY() + top + 1.0, pos.getZ() + 0.5 + e);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        double e = Math.max(4.0, MachineBody.horizontalExtent(view.seed, view.tier, view.scale));
        double d = Math.min(512.0, Math.max(128.0, e * 4.0));
        return d * d;
    }
}

package com.fldsmdfr.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.fldsmdfr.config.ModConfig;
import com.fldsmdfr.entity.EntityFoodDrop;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Fires physical food ONLY from the animated lower aperture of the central body.
 * The origin is recomputed from the current body dimensions, facing and aperture
 * animation, so it follows the machine when it grows and deforms. The main launch
 * direction is always downward; horizontal speed is only a controlled dispersion.
 */
public final class LaunchSystem {
    private static final double GRAVITY = 0.04;

    private LaunchSystem() {}

    /** Counts food entities in the world and removes the oldest above the limit. Returns the remaining count. */
    public static int recount(World w, int max) {
        List<EntityFoodDrop> list = new ArrayList<EntityFoodDrop>();
        for (Entity e : w.loadedEntityList) {
            if (e instanceof EntityFoodDrop && !e.isDead) list.add((EntityFoodDrop) e);
        }
        if (list.size() <= max) return list.size();
        Collections.sort(list, new Comparator<EntityFoodDrop>() {
            @Override
            public int compare(EntityFoodDrop a, EntityFoodDrop b) { return b.ticksExisted - a.ticksExisted; }
        });
        int remove = list.size() - max;
        for (int i = 0; i < remove; i++) list.get(i).setDead();
        return max;
    }

    /** One launch cycle. Returns the number of entities spawned. */
    public static int update(WorldServer w, BlockPos core, int facing, MachineState st, double dt, int activeFood) {
        if (!st.running) return 0;
        st.launchCooldown -= dt;
        if (st.launchCooldown > 0) return 0;

        st.recompute();
        double tier = st.tier();
        double sat = st.sat();
        double scale = MachineModel.scale(tier);
        MachineBody.Form body = MachineBody.form(st.seed, tier);
        double period = MachineBody.dischargePeriod(tier, sat, st.surgeTicks);
        st.launchCooldown = period;

        BigNum rate = st.capacityPerSec().min(st.stats.launchPerSec.mul(ModConfig.d("productionIntensity")));
        rate = rate.mul(ModConfig.d("stormIntensity") * (st.surgeTicks > 0 ? 2.0 + 3.0 * sat : 1.0));
        if (rate.isZero()) return 0;
        st.launched = st.launched.add(rate.mul(period / 20.0));

        double cx = core.getX() + 0.5, cy = core.getY(), cz = core.getZ() + 0.5;
        EntityPlayer near = w.getClosestPlayer(cx, cy, cz, ModConfig.d("simulationDistance"));
        if (near == null) return 0; // logical only while nobody is close

        int room = Math.min(ModConfig.i("maxLaunchesPerUpdate"), ModConfig.i("maxActiveFood") - activeFood);
        if (room <= 0) return 0;
        double want = 1.0 + 1.5 * rate.log10OnePlus() * (1.0 + 2.0 * sat) * (st.surgeTicks > 0 ? 2.0 : 1.0);
        int entities = (int) Math.min((double) room, Math.floor(want));
        if (entities <= 0) return 0;

        double facingDeg = (facing & 3) * 90.0;
        double baseSize = MachineModel.foodSize(tier, ModConfig.d("foodSizeMultiplier"));
        Random r = st.rng;

        // Downward launch: the mouth is at the bottom of the body, not on top. The bob is
        // the same aperture animation used by the renderer.
        double bob = MachineBody.apertureBob(w.getTotalWorldTime(), sat);
        double bodyBottom = cy + (MachineBody.LIFT + bob) * scale;
        double desiredSpread = Math.min(24.0, (2.5 + 0.07 * ModConfig.d("launchDistance") + 0.35 * st.stats.rangeBonus)
                * (1.0 + 0.15 * Math.log(scale)));

        for (int i = 0; i < entities; i++) {
            double angle = r.nextDouble() * Math.PI * 2.0;
            double radial = 0.10 + 0.80 * r.nextDouble();
            double[] local = body.aperturePoint(angle, radial);
            double[] off = MachineModel.toWorldOffset(local, scale, facingDeg);
            double x = cx + off[0];
            double z = cz + off[2];

            double size = baseSize * (0.75 + 0.5 * r.nextDouble()) * (st.surgeTicks > 0 ? 1.25 : 1.0);
            int foodType = st.pickFood(r);
            double foodHeight = Math.min(3.7, Math.max(0.2, size * FoodRegistry.get(foodType).height));
            // Entity.setPosition uses the feet (bottom) of the entity. Subtract the FULL
            // food height so the top of the food starts just below the animated mouth
            // instead of intersecting the machine body.
            double y = bodyBottom - 0.05 - foodHeight;
            if (y < cy + 0.05) y = cy + 0.05;
            double fallHeight = Math.max(1.0, y - cy);

            EntityFoodDrop e = new EntityFoodDrop(w);
            e.setPosition(x, y, z);
            e.setFood(foodType, (float) size);
            e.lifespan = ModConfig.i("foodLifespanTicks");

            double outward = angle + (r.nextDouble() - 0.5) * (0.55 + 0.65 * sat);
            double vy = MachineBody.dischargeVelocityY(scale, r.nextDouble());
            double tFall = (-vy + Math.sqrt(vy * vy + 2.0 * GRAVITY * fallHeight)) / GRAVITY;
            double hs = MachineBody.dischargeSpeedXZ(desiredSpread, tFall, r.nextDouble());
            e.motionX = Math.cos(outward) * hs;
            e.motionZ = Math.sin(outward) * hs;
            e.motionY = vy; // always negative: the food is expelled toward the ground

            w.spawnEntityInWorld(e);
            if (i == 0 && ModConfig.b("visualEffects")) {
                w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z, 5, 0.22, 0.10, 0.22, 0.01);
            }
        }
        if (ModConfig.b("sounds") && r.nextInt(3) == 0) {
            w.playSoundEffect(cx, cy + MachineBody.LIFT * scale, cz, "tile.piston.out", 0.8f, 0.6f + r.nextFloat() * 0.8f);
        }
        return entities;
    }
}

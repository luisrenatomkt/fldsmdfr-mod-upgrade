package com.fldsmdfr.client;

import com.fldsmdfr.core.Hash;
import com.fldsmdfr.core.MachineBody;
import com.fldsmdfr.core.MachineModel;
import com.fldsmdfr.core.MachineView;
import com.fldsmdfr.tile.TileEntityFldsmdfr;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Draws the ONE FLDSMDFR body. There are no pods, arms, rings, satellites, spires,
 * platforms or thrusters around the machine anymore: every mutation is a deformation
 * of the central shell or a volume embedded in it. The only discharge mouth is the
 * animated aperture under the body, and all food is launched from there.
 *
 * <p>Front of the model is +Z. Geometry is recorded into two Gfx batches (solid and
 * additive glow) and sent to the GPU in two draw calls, so the extra mutations do not
 * cost one draw call per part.</p>
 */
public class RenderCore extends TileEntitySpecialRenderer<TileEntityFldsmdfr> {
    private final Gfx solid = new Gfx();
    private final Gfx glow = new Gfx();

    @Override
    public void renderTileEntityAt(TileEntityFldsmdfr te, double x, double y, double z, float pt, int destroyStage) {
        MachineView v = te.view;
        double sat = v.sat();
        float time = (te.getWorld() != null ? te.getWorld().getTotalWorldTime() : 0L) + pt;
        boolean on = v.running;
        float flick = on ? (float) (0.78 + 0.22 * Math.sin(time * (0.6 + 6.0 * sat)) * (0.5 + sat)) : 0.15f;
        if (sat > 0.4 && ((int) (time * 0.7) % 11) == 0) flick *= 0.2f;

        float[] acc = Gfx.hsv(MachineModel.eraHue(v.seed, v.era), 0.85f, 1.0f);
        MachineBody.Form body = MachineBody.form(v.seed, v.tier);
        int details = Math.min(body.featureCount, Math.max(0, v.rendered));
        double S = v.scale;

        solid.clear();
        glow.clear();
        glow.glow = true;
        glow.alpha = 0.85f;
        solid.glow = false;
        solid.alpha = 1f;

        if (on && sat > 0.05) {
            double amp = 0.010 * sat * S;
            solid.translate(Math.sin(time * 9.0) * amp, Math.abs(Math.sin(time * 13.0)) * amp, Math.cos(time * 11.0) * amp);
            glow.translate(Math.sin(time * 9.0) * amp, Math.abs(Math.sin(time * 13.0)) * amp, Math.cos(time * 11.0) * amp);
        }
        solid.scale(S);
        glow.scale(S);

        legs(solid, body, acc, time, on, sat);
        shell(solid, body, acc, time, sat, on);
        top(solid, glow, body, acc, time, on, flick);
        plates(solid, body, acc);
        features(solid, glow, body, acc, time, on, flick, details);
        screen(solid, glow, body, acc, time, on, flick);
        aperture(solid, glow, body, acc, time, sat, on, v.surge);
        arcs(glow, body, acc, time, sat, on);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(EnumFacing.getHorizontal(te.facing & 3).getHorizontalIndex() * 90.0F, 0, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.color(1, 1, 1, 1);
        solid.flush();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 1);
        GlStateManager.depthMask(false);
        glow.flush();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ legs / base

    private void legs(Gfx s, MachineBody.Form b, float[] acc, float time, boolean on, double sat) {
        double bottomW = b.ringHalfW(0.0);
        double bottomD = b.ringHalfD(0.0);
        double sx = Math.max(0.52, bottomW * 0.82);
        double sz = Math.max(0.38, bottomD * 0.82);
        double thick = 0.085 + 0.035 * Math.min(1.0, b.tier / 12.0);
        double top = MachineBody.LIFT - 0.05;

        for (int ix = -1; ix <= 1; ix += 2) {
            for (int iz = -1; iz <= 1; iz += 2) {
                double x = ix * sx, z = iz * sz;
                s.box(x - thick, 0.05, z - thick, x + thick, top, z + thick, 0.34f, 0.35f, 0.38f);
                s.box(x - thick * 1.35, 0.0, z - thick * 1.35, x + thick * 1.35, 0.09, z + thick * 1.35, 0.16f, 0.16f, 0.18f);
                s.box(x - thick * 0.72, 0.55, z - thick * 0.72, x + thick * 0.72, 0.62, z + thick * 0.72, acc[0] * 0.85f, acc[1] * 0.85f, acc[2] * 0.85f);
                // short hydraulic piston on the inner face of each leg
                double px = x - ix * (thick + 0.035), pz = z - iz * (thick + 0.035);
                s.cyl(px, 0.16, pz, 0.026, 0.026, 0.55, 6, 0.55f, 0.57f, 0.60f, true, false);
                s.cyl(px, 0.71, pz, 0.045, 0.045, 0.10, 6, 0.22f, 0.23f, 0.26f, true, true);
            }
        }

        // Heavy base collar, still part of the same body. It is an open ring: the bottom
        // aperture must remain visible and unobstructed.
        s.ellipseTube(0, MachineBody.LIFT - 0.14, 0, bottomW * 1.04, bottomD * 1.04,
                bottomW * 0.97, bottomD * 0.97, 0.15, 20, 0.28f, 0.29f, 0.32f, false, false);
        s.ellipseTube(0, MachineBody.LIFT - 0.105, 0, bottomW * 1.00, bottomD * 1.00,
                bottomW * 0.985, bottomD * 0.985, 0.035, 20,
                acc[0] * 0.85f, acc[1] * 0.85f, acc[2] * 0.85f, false, false);

        int vents = 6 + (int) Math.min(6, b.tier / 3.0);
        for (int i = 0; i < vents; i++) {
            double a = Math.PI * 2.0 * i / vents;
            double vx = Math.cos(a) * bottomW * 1.05;
            double vz = Math.sin(a) * bottomD * 1.05;
            s.push();
            s.translate(vx, MachineBody.LIFT - 0.045, vz);
            s.rotate(Math.toDegrees(a), 0, 1, 0);
            s.box(-0.09, -0.035, 0.0, 0.09, 0.035, 0.025, 0.10f, 0.11f, 0.13f);
            s.box(-0.07, -0.015, 0.026, 0.07, 0.015, 0.032, 0.20f, 0.22f, 0.26f);
            s.pop();
        }
    }

    // ------------------------------------------------------------------ central shell

    private void shell(Gfx s, MachineBody.Form b, float[] acc, float time, double sat, boolean on) {
        int R = MachineBody.SHELL_RINGS;
        int N = MachineBody.SHELL_SEGMENTS;
        double[][][] p = new double[R][N][3];
        for (int i = 0; i < R; i++) {
            double t = i / (double) (R - 1);
            double pulse = 1.0 + (on ? 0.008 * Math.sin(time * 1.6 + t * 4.0) * sat : 0.0);
            for (int j = 0; j < N; j++) {
                double theta = Math.PI * 2.0 * j / N;
                p[i][j] = b.surfacePoint(theta, t, pulse);
            }
        }
        for (int i = 0; i < R - 1; i++) {
            double tMid = (i + 0.5) / (R - 1.0);
            double cx = b.centerXAt(tMid), cz = b.centerZAt(tMid);
            for (int j = 0; j < N; j++) {
                int j1 = (j + 1) % N;
                double theta = Math.PI * 2.0 * (j + 0.5) / N;
                float[] col = bodyColor(b, tMid, theta, acc);
                quadN(s, p[i][j], p[i + 1][j], p[i + 1][j1], p[i][j1], cx, cz, col);
            }
        }

        // Bottom rim: the shell is open in the middle so the discharge mouth is a real hole.
        // The outer edge uses the actual deformed bottom ring, so the rim never separates
        // from the shell when the body becomes asymmetric.
        bottomRim(s, b, N);

        // Top cap: closes the shell and supports the low exhaust/valve assembly.
        topCap(s, b, N);
    }

    private void bottomRim(Gfx s, MachineBody.Form b, int seg) {
        double cx = b.centerXAt(0.0), cz = b.centerZAt(0.0);
        double apW = b.apertureHalfW * 1.03, apD = b.apertureHalfD * 1.03;
        for (int j = 0; j < seg; j++) {
            int j1 = (j + 1) % seg;
            double a0 = Math.PI * 2.0 * j / seg;
            double a1 = Math.PI * 2.0 * j1 / seg;
            double ix0 = cx + Math.cos(a0) * apW, iz0 = cz + Math.sin(a0) * apD;
            double ix1 = cx + Math.cos(a1) * apW, iz1 = cz + Math.sin(a1) * apD;
            double[] o0 = b.surfacePoint(a0, 0.0);
            double[] o1 = b.surfacePoint(a1, 0.0);
            s.quad(ix0, MachineBody.LIFT, iz0, ix1, MachineBody.LIFT, iz1,
                    o1[0], o1[1], o1[2], o0[0], o0[1], o0[2],
                    0, -1, 0, 0.20f, 0.21f, 0.24f);
        }
    }

    private void topCap(Gfx s, MachineBody.Form b, int seg) {
        double cx = b.centerXAt(1.0), cy = b.topY(), cz = b.centerZAt(1.0);
        for (int j = 0; j < seg; j++) {
            int j1 = (j + 1) % seg;
            double a0 = Math.PI * 2.0 * j / seg;
            double a1 = Math.PI * 2.0 * j1 / seg;
            double[] p0 = b.surfacePoint(a0, 1.0);
            double[] p1 = b.surfacePoint(a1, 1.0);
            s.quad(cx, cy, cz, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p1[0], p1[1], p1[2],
                    0, 1, 0, 0.82f, 0.82f, 0.80f);
        }
    }

    private float[] bodyColor(MachineBody.Form b, double t, double theta, float[] acc) {
        double n = 0.5 + 0.5 * Math.sin(theta * 6.0 + t * 19.0 + b.seed * 0.0001);
        float base = (float) (0.94 - 0.24 * b.grime + 0.035 * (n - 0.5));
        float r = base, g = base * 0.985f, bl = base * 0.955f;
        if (t < 0.045) {
            r = acc[0] * 0.90f; g = acc[1] * 0.90f; bl = acc[2] * 0.90f;
        } else if (t > 0.90) {
            float k = (float) ((t - 0.90) / 0.10);
            r = r * (1 - 0.35f * k) + acc[0] * 0.35f * k;
            g = g * (1 - 0.35f * k) + acc[1] * 0.35f * k;
            bl = bl * (1 - 0.35f * k) + acc[2] * 0.35f * k;
        } else if (b.grime > 0.35 && n > 0.72) {
            r *= 0.82f; g *= 0.82f; bl *= 0.82f;
        }
        return new float[]{r, g, bl};
    }

    private void quadN(Gfx s, double[] a, double[] bb, double[] c, double[] d, double cx, double cz, float[] col) {
        double ux = bb[0] - a[0], uy = bb[1] - a[1], uz = bb[2] - a[2];
        double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9) { nx = 0; ny = 1; nz = 0; } else { nx /= l; ny /= l; nz /= l; }
        double mx = (a[0] + c[0]) * 0.5 - cx, mz = (a[2] + c[2]) * 0.5 - cz;
        if (nx * mx + nz * mz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        s.quad(a[0], a[1], a[2], bb[0], bb[1], bb[2], c[0], c[1], c[2], d[0], d[1], d[2],
                nx, ny, nz, col[0], col[1], col[2]);
    }

    // ------------------------------------------------------------------ top assembly

    private void top(Gfx s, Gfx g, MachineBody.Form b, float[] acc, float time, boolean on, float flick) {
        double cx = b.centerXAt(1.0), cz = b.centerZAt(1.0);
        double topW = b.ringHalfW(1.0), topD = b.ringHalfD(1.0);
        s.ell(cx, b.topY() - 0.02, cz, topW * 1.22, 0.20 + 0.05 * Math.min(1.0, b.tier / 20.0), topD * 1.18,
                12, 6, 0.80f, 0.80f, 0.78f, true);
        s.torus(cx, b.topY() + 0.01, cz, topW * 1.04, 0.025, 14, 3, acc[0], acc[1], acc[2], 0, 360);

        int stacks = b.tier > 8.0 ? 2 : 1;
        for (int i = 0; i < stacks; i++) {
            double ox = (i == 0 ? -0.10 : 0.14) * topW;
            double oz = (i == 0 ? 0.08 : -0.09) * topD;
            double h = 0.18 + 0.10 * Math.min(1.0, b.tier / 24.0);
            double r = 0.075 + 0.015 * i;
            s.cyl(cx + ox, b.topY() + 0.02, cz + oz, r, r * 0.9, h, 8, 0.36f, 0.37f, 0.40f, true, false);
            s.box(cx + ox - r * 1.2, b.topY() + 0.02 + h, cz + oz - r * 1.2,
                    cx + ox + r * 1.2, b.topY() + 0.055 + h, cz + oz + r * 1.2,
                    0.18f, 0.18f, 0.20f);
            float gl = on ? (0.45f + 0.55f * Math.abs((float) Math.sin(time * 0.5 + i))) * flick : 0.10f;
            g.box(cx + ox - r * 0.45, b.topY() + 0.045 + h, cz + oz - r * 0.45,
                    cx + ox + r * 0.45, b.topY() + 0.075 + h, cz + oz + r * 0.45,
                    acc[0] * gl, acc[1] * gl, acc[2] * gl);
            // short return pipe that ends on the body shell (never a loose antenna)
            double a = i == 0 ? 2.2 : -0.7;
            double[] end = b.surfacePoint(a, 0.92);
            s.beam(cx + ox, b.topY() + 0.05, cz + oz, end[0], end[1], end[2], 0.030, 0.42f, 0.43f, 0.46f);
        }
    }

    // ------------------------------------------------------------------ integrated armour plates

    private void plates(Gfx s, MachineBody.Form b, float[] acc) {
        int count = Math.min(10, (int) Math.floor(b.tier * 0.45));
        for (int i = 0; i < count; i++) {
            long h = Hash.mix(b.seed ^ (i * 0x9E3779B97F4A7C15L) ^ 0x7F4A7C15B5C3D2E1L);
            double theta = i * 2.399963229728653 + (MachineBody.unit(h, 1) - 0.5) * 0.55;
            double t = 0.22 + 0.54 * MachineBody.unit(h, 2);
            double[] p = b.surfacePoint(theta, t);
            double[] n = b.normalAt(theta, t);
            double yaw = Math.toDegrees(Math.atan2(n[0], n[2]));
            double w = 0.18 + 0.16 * MachineBody.unit(h, 3);
            double hgt = 0.20 + 0.26 * MachineBody.unit(h, 4);
            double tilt = (MachineBody.unit(h, 5) - 0.5) * 16.0;
            float shade = (float) (0.62 + 0.18 * MachineBody.unit(h, 6));
            float ar = acc[0] * 0.55f + shade * 0.45f;
            float ag = acc[1] * 0.55f + shade * 0.45f;
            float ab = acc[2] * 0.55f + shade * 0.45f;

            s.push();
            s.translate(p[0], p[1], p[2]);
            s.rotate(yaw, 0, 1, 0);
            s.rotate(tilt, 1, 0, 0);
            s.box(-w, -hgt, -0.005, w, hgt, 0.040, ar, ag, ab);
            s.box(-w * 0.92, -hgt * 0.92, 0.040, w * 0.92, -hgt * 0.72, 0.055, 0.22f, 0.23f, 0.26f);
            if (b.tier > 6.0) {
                // bent continuation: the plate folds over itself instead of floating away
                s.push();
                s.translate(w * 0.55, hgt * 0.25, 0.045);
                s.rotate(-24.0 - 10.0 * MachineBody.unit(h, 7), 0, 0, 1);
                s.box(-w * 0.45, -hgt * 0.55, 0.0, w * 0.45, hgt * 0.55, 0.035, ar * 0.92f, ag * 0.92f, ab * 0.92f);
                s.pop();
            }
            for (int r = 0; r < 4; r++) {
                double rx = (r % 2 == 0 ? -1 : 1) * w * 0.80;
                double ry = (r < 2 ? -1 : 1) * hgt * 0.80;
                s.box(rx - 0.018, ry - 0.018, 0.040, rx + 0.018, ry + 0.018, 0.055, 0.30f, 0.31f, 0.34f);
            }
            s.pop();
        }
    }

    // ------------------------------------------------------------------ embedded mutations

    private void features(Gfx s, Gfx g, MachineBody.Form b, float[] acc, float time, boolean on, float flick, int limit) {
        for (int i = 0; i < limit; i++) {
            MachineBody.Feature f = b.features[i];
            double[] p = b.surfacePoint(f.theta, f.t);
            double[] n = b.normalAt(f.theta, f.t);
            double yaw = Math.toDegrees(Math.atan2(n[0], n[2]));
            s.push();
            g.push();
            s.translate(p[0], p[1], p[2]);
            g.translate(p[0], p[1], p[2]);
            s.rotate(yaw, 0, 1, 0);
            g.rotate(yaw, 0, 1, 0);
            s.rotate(Math.toDegrees(f.tilt), 1, 0, 0);
            g.rotate(Math.toDegrees(f.tilt), 1, 0, 0);

            float base = (float) (0.86 - 0.20 * b.grime);
            float ar = acc[0] * 0.55f + base * 0.45f;
            float ag = acc[1] * 0.55f + base * 0.45f;
            float ab = acc[2] * 0.55f + base * 0.45f;

            switch (f.kind) {
                case BULGE:
                    s.ell(0, 0, f.size * 0.20, f.size * f.aspect, f.size * 0.88, f.size * 0.78, 10, 6, base, base * 0.98f, base * 0.94f, false);
                    s.box(-f.size * f.aspect * 0.62, -f.size * 0.12, f.size * 0.66,
                            f.size * f.aspect * 0.62, f.size * 0.12, f.size * 0.72,
                            ar, ag, ab);
                    s.box(-f.size * 0.18, -f.size * 0.72, f.size * 0.42,
                            f.size * 0.18, f.size * 0.72, f.size * 0.50,
                            0.28f, 0.29f, 0.32f);
                    break;
                case CHAMBER:
                    s.ell(0, 0, f.size * 0.24, f.size * f.aspect * 1.05, f.size * 0.96, f.size * 0.86, 12, 7, base * 0.94f, base * 0.92f, base * 0.88f, false);
                    s.box(-f.size * f.aspect * 0.85, -f.size * 0.22, f.size * 0.52,
                            f.size * f.aspect * 0.85, f.size * 0.22, f.size * 0.90,
                            ar, ag, ab);
                    s.box(-f.size * f.aspect * 0.55, -f.size * 0.50, f.size * 0.70,
                            f.size * f.aspect * 0.55, f.size * 0.50, f.size * 0.74,
                            0.10f, 0.11f, 0.13f);
                    if (on) {
                        g.alpha = 0.55f * flick;
                        g.box(-f.size * f.aspect * 0.42, -f.size * 0.28, f.size * 0.745,
                                f.size * f.aspect * 0.42, f.size * 0.28, f.size * 0.765,
                                acc[0], acc[1], acc[2]);
                        g.alpha = 0.85f;
                    }
                    break;
                case PLATE:
                    s.box(-f.size * f.aspect, -f.size, 0.0, f.size * f.aspect, f.size, 0.040, ar, ag, ab);
                    s.box(-f.size * f.aspect * 0.9, -f.size * 0.9, 0.040, f.size * f.aspect * 0.9, -f.size * 0.68, 0.055, 0.24f, 0.25f, 0.28f);
                    s.box(-f.size * 0.10, -f.size, 0.0, f.size * 0.10, f.size * 1.15, 0.055, 0.30f, 0.31f, 0.34f);
                    break;
                case PIPE: {
                    double z = f.size * 0.75;
                    s.beam(0, -f.size * 0.72, 0.01, 0, -f.size * 0.30, z, 0.045, 0.44f, 0.45f, 0.48f);
                    s.beam(0, -f.size * 0.30, z, 0, f.size * 0.34, z, 0.045, 0.50f, 0.51f, 0.54f);
                    s.beam(0, f.size * 0.34, z, 0, f.size * 0.76, 0.01, 0.045, 0.44f, 0.45f, 0.48f);
                    s.sphere(0, f.size * 0.34, z, 0.055, 6, ar, ag, ab);
                    break;
                }
                case VENT:
                    s.box(-f.size * f.aspect, -f.size * 0.72, 0.0, f.size * f.aspect, f.size * 0.72, 0.045, 0.10f, 0.11f, 0.13f);
                    for (int k = 0; k < 4; k++) {
                        double yy = -f.size * 0.48 + k * f.size * 0.32;
                        s.box(-f.size * f.aspect * 0.82, yy - 0.025, 0.045, f.size * f.aspect * 0.82, yy + 0.025, 0.065, 0.34f, 0.35f, 0.38f);
                    }
                    if (on) {
                        g.alpha = 0.35f * flick;
                        g.box(-f.size * f.aspect * 0.55, -f.size * 0.42, 0.066, f.size * f.aspect * 0.55, f.size * 0.42, 0.070, acc[0], acc[1], acc[2]);
                        g.alpha = 0.85f;
                    }
                    break;
                case CAVITY: {
                    double w = f.size * f.aspect, h = f.size * 0.82;
                    // dark back plate is recessed; the exposed mechanism is drawn in front
                    // of it and framed by the rim, so it reads as an opening, not a decal.
                    s.box(-w, -h, 0.0, w, h, 0.020, 0.035f, 0.04f, 0.05f);
                    s.box(-w, h, 0.020, w, h + 0.055, 0.080, 0.30f, 0.31f, 0.34f);
                    s.box(-w, -h - 0.055, 0.020, w, -h, 0.080, 0.30f, 0.31f, 0.34f);
                    s.box(-w - 0.055, -h, 0.020, -w, h, 0.080, 0.30f, 0.31f, 0.34f);
                    s.box(w, -h, 0.020, w + 0.055, h, 0.080, 0.30f, 0.31f, 0.34f);
                    // exposed internal mechanism, moving inside the opening
                    double bob = Math.sin(time * 0.8 + f.phase) * h * 0.18;
                    s.cyl(0, -h * 0.55 + bob, 0.028, 0.045, 0.045, h * 0.9, 8, 0.42f, 0.43f, 0.46f, true, false);
                    s.sphere(0, h * 0.35 + bob, 0.035, 0.075, 7, 0.28f, 0.29f, 0.32f);
                    s.box(-w * 0.8, -h * 0.12, 0.050, w * 0.8, -h * 0.06, 0.070, 0.18f, 0.19f, 0.22f);
                    if (on) {
                        float gl = (0.55f + 0.45f * (float) Math.abs(Math.sin(time * 0.6 + f.phase))) * flick;
                        g.alpha = 0.70f * gl;
                        g.ell(0, bob, 0.032, w * 0.55, h * 0.42, 0.028, 10, 5, acc[0], acc[1], acc[2], false);
                        g.alpha = 0.85f;
                    }
                    break;
                }
                case FIN:
                    s.box(-f.size * 0.14, -f.size, 0.0, f.size * 0.14, f.size, f.size * 0.70, 0.46f, 0.47f, 0.50f);
                    s.box(-f.size * 0.16, -f.size, f.size * 0.70, f.size * 0.16, f.size, f.size * 0.76, ar, ag, ab);
                    break;
                default:
                    break;
            }
            s.pop();
            g.pop();
        }
    }

    // ------------------------------------------------------------------ original front panel

    private void screen(Gfx s, Gfx g, MachineBody.Form b, float[] acc, float time, boolean on, float flick) {
        float ar = acc[0], ag = acc[1], ab = acc[2];
        double t = 0.57;
        double sx = b.centerXAt(t);
        double y = MachineBody.LIFT + t * b.height;
        double z = b.frontZ(t) + 0.018;

        // bezel and dark screen plate, centred on the deformed front surface
        s.box(sx - 0.37, y - 0.32, z, sx + 0.37, y + 0.34, z + 0.035, 0.12f, 0.12f, 0.14f);
        s.box(sx - 0.32, y - 0.27, z + 0.035, sx + 0.32, y + 0.28, z + 0.050, 0.03f, 0.03f, 0.04f);
        float rr = (float) (0.30 + 0.70 * b.grime);
        float gg = (float) (0.60 * (1.0 - b.grime) + 0.10);
        float bb = (float) (1.0 - 0.80 * b.grime);
        s.discZ(sx - 0.08, y + 0.02, z + 0.052, 0.19, 20, rr * flick, gg * flick + 0.10f, bb * flick, 0.75f * flick, 0.88f * flick, 1.0f * flick);
        g.alpha = 0.32f * flick;
        g.discZ(sx - 0.08, y + 0.02, z + 0.060, 0.22, 20, ar, ag, ab, ar, ag, ab);
        g.alpha = 0.85f;

        // keypad
        double tk = 0.30;
        double sk = b.centerXAt(tk);
        double yk = MachineBody.LIFT + tk * b.height;
        double zk = b.frontZ(tk) + 0.018;
        s.box(sk - 0.30, yk - 0.15, zk, sk + 0.30, yk + 0.15, zk + 0.035, 0.10f, 0.11f, 0.13f);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float gl = on ? (0.45f + 0.55f * (float) Math.abs(Math.sin(time * 0.15 + r * 2 + c))) * flick : 0.10f;
                double kx = sk - 0.22 + c * 0.16, ky = yk - 0.09 + r * 0.09;
                s.box(kx, ky, zk + 0.035, kx + 0.10, ky + 0.055, zk + 0.045, 0.10f * gl, 0.95f * gl, 0.20f * gl);
            }
        }

        // original button strip
        double tb = 0.17;
        double sb = b.centerXAt(tb);
        double yb = MachineBody.LIFT + tb * b.height;
        double zb = b.frontZ(tb) + 0.018;
        s.box(sb - 0.44, yb - 0.08, zb, sb + 0.44, yb + 0.08, zb + 0.035, 0.12f, 0.12f, 0.14f);
        for (int i = 0; i < 10; i++) {
            double bx = sb - 0.38 + i * 0.083;
            float k = (float) i / 9f;
            float lit = on ? (i < 2 ? flick : 1f) : 0.3f;
            s.box(bx, yb - 0.045, zb + 0.035, bx + 0.060, yb + 0.045, zb + 0.050,
                    (0.85f - 0.3f * k) * lit, (0.2f + 0.55f * k) * lit, (0.15f + 0.4f * k) * lit);
        }

        // two side handles, attached to the shell
        for (int side = -1; side <= 1; side += 2) {
            double hx = sx + side * (b.ringHalfW(t) * 0.98 + 0.04);
            double hz = b.centerZAt(t);
            s.box(hx - 0.035, y - 0.10, hz - 0.10, hx + 0.035, y + 0.10, hz + 0.10, 0.40f, 0.41f, 0.44f);
            s.box(hx - 0.020, y - 0.07, hz - 0.07, hx + 0.020, y + 0.07, hz + 0.07, ar, ag, ab);
        }
    }

    // ------------------------------------------------------------------ bottom discharge

    private void aperture(Gfx s, Gfx g, MachineBody.Form b, float[] acc, float time, double sat, boolean on, int surge) {
        double bob = MachineBody.apertureBob(time, sat);
        double apW = b.apertureHalfW;
        double apD = b.apertureHalfD;
        double y = MachineBody.LIFT + bob;

        // dark throat, short and integrated under the shell
        s.ellipseTube(0, y - 0.12, 0, apW * 0.94, apD * 0.94, apW * 0.86, apD * 0.86, 0.14, 20,
                0.075f, 0.08f, 0.10f, false, false);
        // rim around the mouth
        s.annulusY(0, y + 0.005, 0, apW * 0.96, apD * 0.96, apW * 1.10, apD * 1.10, 24,
                0.34f, 0.35f, 0.38f, false);
        s.annulusY(0, y + 0.010, 0, apW * 1.00, apD * 1.00, apW * 1.06, apD * 1.06, 24,
                acc[0] * 0.90f, acc[1] * 0.90f, acc[2] * 0.90f, false);

        double period = MachineBody.dischargePeriod(b.tier, sat, surge);
        double open = MachineBody.dischargeOpen(time, period);

        // shutters hinge on the rim and open downward; they never leave the body
        int flaps = b.tier > 12.0 ? 6 : 4;
        for (int i = 0; i < flaps; i++) {
            double a = Math.PI * 2.0 * i / flaps + Math.PI / flaps;
            double hx = Math.cos(a) * apW * 0.98;
            double hz = Math.sin(a) * apD * 0.98;
            double len = Math.min(apW, apD) * 0.92;
            double wid = (float) (Math.PI * apW / flaps) * 0.90;
            s.push();
            s.translate(hx, y, hz);
            s.rotate(Math.toDegrees(a), 0, 1, 0);
            s.rotate(-open * 74.0, 0, 0, 1);
            s.box(-len, -0.022, -wid * 0.5, 0.0, 0.022, wid * 0.5, 0.20f, 0.21f, 0.24f);
            s.box(-len, 0.022, -wid * 0.5, -len + 0.045, 0.032, wid * 0.5, acc[0], acc[1], acc[2]);
            s.pop();
        }

        // internal pushers visible through the mouth
        int pistons = 3;
        for (int i = 0; i < pistons; i++) {
            double a = Math.PI * 2.0 * i / pistons + 0.4;
            double px = Math.cos(a) * apW * 0.44;
            double pz = Math.sin(a) * apD * 0.44;
            double push = 0.5 + 0.5 * Math.sin(time * 0.55 + i * 2.1);
            double top = y - 0.07 - 0.06 * push;
            s.cyl(px, top - 0.11, pz, 0.020, 0.020, 0.13, 6, 0.42f, 0.43f, 0.46f, true, false);
            s.cyl(px, top + 0.02, pz, 0.042, 0.042, 0.045, 6, 0.22f, 0.23f, 0.26f, true, true);
            if (on) {
                g.alpha = 0.35f + 0.35f * (float) push;
                g.box(px - 0.025, top + 0.065, pz - 0.025, px + 0.025, top + 0.080, pz + 0.025,
                        acc[0], acc[1], acc[2]);
                g.alpha = 0.85f;
            }
        }

        // glowing interior of the production core, always at the bottom
        if (on) {
            float gl = 0.35f + 0.65f * (float) open;
            g.alpha = 0.55f * gl;
            g.discY(0, y - 0.105, 0, apW * 0.72, apD * 0.72, 20, acc[0], acc[1], acc[2], false);
            g.alpha = 0.30f * gl;
            g.torus(0, y - 0.08, 0, apW * 0.80, 0.018, 20, 3, acc[0], acc[1], acc[2], 0, 360);
            g.alpha = 0.85f;
        }
    }

    // ------------------------------------------------------------------ instability arcs (body surface only)

    private void arcs(Gfx g, MachineBody.Form b, float[] acc, float time, double sat, boolean on) {
        if (!on || sat < 0.15) return;
        int count = 1 + (int) (sat * 2.0);
        long q = (long) (time / 3.0);
        for (int i = 0; i < count; i++) {
            long h = Hash.mix(q * 977L + i * 131L + b.seed);
            double t0 = 0.25 + 0.55 * MachineBody.unit(h, 1);
            double t1 = 0.25 + 0.55 * MachineBody.unit(h, 2);
            double th0 = MachineBody.unit(h, 3) * Math.PI * 2.0;
            double th1 = th0 + 0.5 + MachineBody.unit(h, 4) * 1.6;
            double[] a = b.surfacePoint(th0, t0);
            double[] c = b.surfacePoint(th1, t1);
            double px = a[0], py = a[1], pz = a[2];
            for (int j = 1; j <= 4; j++) {
                double f = j / 4.0;
                double nx = a[0] + (c[0] - a[0]) * f + (MachineBody.unit(h, 10 + j) - 0.5) * 0.18;
                double ny = a[1] + (c[1] - a[1]) * f + (MachineBody.unit(h, 20 + j) - 0.5) * 0.18;
                double nz = a[2] + (c[2] - a[2]) * f + (MachineBody.unit(h, 30 + j) - 0.5) * 0.18;
                g.beam(px, py, pz, nx, ny, nz, 0.022, 0.70f, 0.85f, 1.0f);
                px = nx; py = ny; pz = nz;
            }
        }
    }
}

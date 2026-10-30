package com.fldsmdfr.core;

/**
 * Deterministic shape of the ONE central machine body. There are no pods, arms, rings,
 * satellites or any other object orbiting the machine: every field here describes a
 * deformation of the body shell itself or of a volume embedded in that shell.
 *
 * <p>The form is a pure function of (seed, tier). The body grows smoothly with the tier,
 * while the mutation genes (asymmetry, dents, lean, twist, plates, cavities...) are taken
 * from a quantised mutation index. That makes the geometry change when a new mutation is
 * reached instead of being re-randomised on every rendered frame, and it is reproduced
 * identically after saving and reloading the world.</p>
 *
 * <p>Units are model units; the renderer and the launch system multiply them by
 * {@link MachineModel#scale(double)}. The body bottom (and therefore the discharge
 * aperture) is at {@link #LIFT} model units above the block origin. Food always leaves
 * through that bottom aperture.</p>
 */
public final class MachineBody {
    /** Height of the body bottom above the block origin. The legs occupy this space. */
    public static final double LIFT = 1.10;
    public static final double BASE_HALF_WIDTH = 0.90;
    public static final double BASE_HALF_DEPTH = 0.70;
    public static final double BASE_HEIGHT = 2.20;

    /** The body receives a new deterministic mutation every this many tiers. */
    public static final double MUTATION_STEP = 0.35;

    /** Hard cap only on the number of drawn detail volumes, never on the evolution itself. */
    public static final int MAX_FEATURES = 20;
    public static final int SHELL_RINGS = 16;
    public static final int SHELL_SEGMENTS = 20;

    public enum Kind { BULGE, CHAMBER, PLATE, PIPE, VENT, CAVITY, FIN }

    /** One mechanical volume attached to (and partially embedded in) the central shell. */
    public static final class Feature {
        public final int index;
        public final Kind kind;
        public final double theta;   // angle around the body
        public final double t;       // 0..1 height fraction
        public final double size;    // model units
        public final double aspect;
        public final double tilt;
        public final double phase;
        public final boolean major;

        Feature(int index, Kind kind, double theta, double t, double size, double aspect, double tilt, double phase, boolean major) {
            this.index = index;
            this.kind = kind;
            this.theta = theta;
            this.t = t;
            this.size = size;
            this.aspect = aspect;
            this.tilt = tilt;
            this.phase = phase;
            this.major = major;
        }
    }

    private MachineBody() {}

    public static Form form(long seed, double tier) {
        return new Form(seed, tier);
    }

    /** Deterministic uniform value in [0,1). */
    public static double unit(long h, int salt) {
        long x = Hash.mix(h + salt * 0x9E3779B97F4A7C15L);
        return (x >>> 11) / (double) (1L << 53);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double smooth(double v) {
        v = clamp01(v);
        return v * v * (3.0 - 2.0 * v);
    }

    /**
     * The lower opening follows the production cycle. The same function is used by the
     * renderer (shutters/pistons) and by the server (launch cadence), so the visible
     * mechanism and the food bursts stay in the same rhythm.
     */
    public static double dischargePeriod(double tier, double sat, int surgeTicks) {
        double p = 44.0 / (1.0 + 3.0 * clamp01(sat) + 0.18 * Math.log1p(Math.max(0.0, tier)));
        if (surgeTicks > 0) p *= 0.55;
        return Math.max(8.0, p);
    }

    /** 0 = closed, 1 = fully open. */
    public static double dischargeOpen(double time, double period) {
        if (period <= 0) return 1.0;
        double phase = (time % period) / period;
        if (phase < 0.08) return smooth(phase / 0.08);
        if (phase < 0.48) return 1.0;
        if (phase < 0.78) return 1.0 - smooth((phase - 0.48) / 0.30);
        return 0.0;
    }

    /** Small vertical breathing of the discharge mouth (model units). */
    public static double apertureBob(double time, double sat) {
        return 0.006 * Math.sin(time * 1.3) * (0.3 + clamp01(sat));
    }

    /** Vertical speed of a discharged food. Negative by construction: food always falls. */
    public static double dischargeVelocityY(double scale, double unit) {
        return -(0.10 + 0.18 * clamp01(unit)) * (1.0 + 0.15 * Math.min(4.0, Math.max(1.0, scale)));
    }

    /** Horizontal speed for the desired spread; always positive and bounded by 0.60 blocks/tick. */
    public static double dischargeSpeedXZ(double desiredSpread, double fallTicks, double unit) {
        double hs = Math.min(0.60, desiredSpread / Math.max(4.0, fallTicks));
        return hs * (0.35 + 0.65 * clamp01(unit));
    }

    /** Half-extent used by the render bounding box (already scaled). */
    public static double horizontalExtent(long seed, double tier, double scale) {
        Form f = form(seed, tier);
        return Math.max(f.maxHalfWidth(), f.maxHalfDepth()) * scale * 1.25 + 0.6;
    }

    /** Top of the body + low top assembly (already scaled). */
    public static double topY(long seed, double tier, double scale) {
        Form f = form(seed, tier);
        return (LIFT + f.height) * scale + 0.55 * scale;
    }

    /** All geometry parameters of one body state. */
    public static final class Form {
        public final long seed;
        public final double tier;
        public final long mutation;
        public final double widthMul;
        public final double depthMul;
        public final double heightMul;
        public final double height;

        public final double asym;
        public final double rough;
        public final double twist;
        public final double leanX;
        public final double leanZ;
        public final double bulge;
        public final double grime;

        public final double apertureHalfW;
        public final double apertureHalfD;

        public final int featureCount;
        public final Feature[] features;

        private Form(long seed, double tier) {
            this.seed = seed;
            this.tier = Math.max(0.0, tier);
            this.mutation = (long) Math.floor(this.tier / MUTATION_STEP);

            double lg = Math.log1p(this.tier);
            this.widthMul = 1.0 + 0.080 * lg;
            this.depthMul = 1.0 + 0.060 * lg;
            this.heightMul = 1.0 + 0.050 * lg;
            this.height = BASE_HEIGHT * heightMul;

            long mh = Hash.mix(seed ^ (mutation * 0x9E3779B97F4A7C15L) ^ 0x51ED270B5A5A5A5AL);
            double phase = unit(mh, 1) * Math.PI * 2.0;
            double phase2 = unit(mh, 2) * Math.PI * 2.0;
            double phase3 = unit(mh, 3) * Math.PI * 2.0;

            double emerge = 1.0 - Math.exp(-this.tier / 4.0);
            this.asym = (0.38 + 0.17 * Math.sin(mutation * 0.73 + phase)) * emerge;
            this.rough = (0.13 + 0.05 * Math.sin(mutation * 0.51 + phase2)) * (1.0 - Math.exp(-this.tier / 3.0));
            this.twist = 0.45 * (1.0 - Math.exp(-this.tier / 5.0)) * Math.sin(mutation * 0.37 + phase3);
            this.leanX = 0.22 * (1.0 - Math.exp(-this.tier / 6.0)) * Math.sin(mutation * 0.29 + phase);
            this.leanZ = 0.22 * (1.0 - Math.exp(-this.tier / 6.0)) * Math.sin(mutation * 0.31 + phase2);
            this.bulge = (0.12 + 0.08 * Math.sin(mutation * 0.43 + phase3)) * emerge;
            this.grime = 1.0 - Math.exp(-this.tier / 15.0);

            double open = 1.0 - Math.exp(-this.tier / 8.0);
            double desiredW = (0.36 + 0.55 * open) * (1.0 + 0.10 * Math.sin(mutation * 0.61 + 1.3));
            double desiredD = (0.27 + 0.42 * open) * (1.0 + 0.08 * Math.sin(mutation * 0.53 + 2.1));
            // The mouth must remain inside the most dented part of the deformed bottom ring,
            // otherwise the rim would fold through the shell on strongly asymmetric bodies.
            double minBottom = 1.0;
            for (int i = 0; i < 24; i++) {
                minBottom = Math.min(minBottom, radialNoise(i * Math.PI * 2.0 / 24.0, 0.0));
            }
            double fit = Math.max(0.35, minBottom) * profile(0.0) * 0.82;
            this.apertureHalfW = Math.min(desiredW, BASE_HALF_WIDTH * widthMul * fit);
            this.apertureHalfD = Math.min(desiredD, BASE_HALF_DEPTH * depthMul * fit);

            int count = (int) Math.min(MAX_FEATURES, Math.max(0, Math.floor(this.tier * 1.25) - 1));
            if (this.tier < 0.6) count = 0;
            int majors = (int) Math.min(3, Math.max(0, mutation / 14));
            this.featureCount = count;
            this.features = new Feature[count];
            for (int k = 0; k < count; k++) {
                long h = Hash.mix(seed ^ (k * 0x632BE59BD9B4E019L) ^ 0xA24BAED4963EE407L);
                double theta = k * 2.399963229728653 + (unit(h, 1) - 0.5) * 0.55;
                double t = 0.20 + 0.58 * unit(h, 2);
                double size = (0.075 + 0.13 * unit(h, 4)) * (1.0 + 0.04 * Math.min(12.0, this.tier));
                boolean major = k < majors;
                if (major) size *= 2.0;
                size = Math.min(0.55, size);
                double aspect = 0.75 + 1.6 * unit(h, 5);
                double tilt = (unit(h, 6) - 0.5) * 1.1;
                double fphase = unit(h, 7) * Math.PI * 2.0;
                int r = Hash.bounded(Hash.mix(h ^ (0x9E3779B97F4A7C15L * (k + 1))), 100);
                Kind kind;
                if (major) {
                    kind = r < 50 ? Kind.CHAMBER : Kind.BULGE;
                    t = 0.34 + 0.34 * unit(h, 8);
                } else if (r < 24) {
                    kind = Kind.BULGE;
                } else if (r < 42) {
                    kind = Kind.PLATE;
                } else if (r < 58) {
                    kind = Kind.PIPE;
                } else if (r < 72) {
                    kind = Kind.VENT;
                } else if (r < 88) {
                    kind = Kind.CAVITY;
                } else {
                    kind = Kind.FIN;
                }
                this.features[k] = new Feature(k, kind, theta, t, size, aspect, tilt, fphase, major);
            }
        }

        /** Silhouette profile: compact base, straight industrial mid-section, rounded cap. */
        public double profile(double t) {
            t = clamp01(t);
            if (t < 0.06) return 0.78 + 0.22 * (t / 0.06);
            if (t < 0.72) return 1.0;
            double u = (t - 0.72) / 0.28;
            return 0.28 + 0.72 * Math.cos(u * Math.PI * 0.5);
        }

        public double centerXAt(double t) {
            t = clamp01(t);
            return leanX * t * t + 0.045 * asym * Math.sin(t * 5.0 + mutation * 0.61) * t;
        }

        public double centerZAt(double t) {
            t = clamp01(t);
            return leanZ * t * t + 0.045 * asym * Math.sin(t * 4.3 - mutation * 0.47) * t;
        }

        public double ringHalfW(double t) {
            return BASE_HALF_WIDTH * widthMul * profile(t);
        }

        public double ringHalfD(double t) {
            return BASE_HALF_DEPTH * depthMul * profile(t);
        }

        private double radialNoise(double theta, double t) {
            double n = 0.55 * Math.sin(theta * 3.0 + mutation * 1.7 + t * 7.0)
                    + 0.30 * Math.sin(theta * 5.0 - mutation * 0.9 + t * 13.0)
                    + 0.15 * Math.sin(theta * 7.0 + mutation * 0.4 + t * 21.0);
            double a = 0.65 * Math.sin(theta * 2.0 + mutation * 0.6)
                    + 0.35 * Math.sin(theta * 3.0 - mutation * 1.3 + t * 4.0);
            return 1.0 + rough * n + asym * a;
        }

        public double[] surfacePoint(double theta, double t) {
            return surfacePoint(theta, t, 1.0);
        }

        /** A point on the deformed shell. `pulse` is a small breathing multiplier. */
        public double[] surfacePoint(double theta, double t, double pulse) {
            t = clamp01(t);
            double a = theta + twist * t;
            double m = radialNoise(theta, t) * pulse;
            double rw = ringHalfW(t) * m;
            double rd = ringHalfD(t) * m;
            return new double[]{
                    centerXAt(t) + Math.cos(a) * rw,
                    LIFT + t * height,
                    centerZAt(t) + Math.sin(a) * rd
            };
        }

        /** Outward unit normal at a shell point. */
        public double[] normalAt(double theta, double t) {
            double[] p = surfacePoint(theta, t);
            double[] pt = surfacePoint(theta + 0.012, t);
            double[] py = surfacePoint(theta, Math.min(1.0, t + 0.012));
            double ux = pt[0] - p[0], uy = pt[1] - p[1], uz = pt[2] - p[2];
            double vx = py[0] - p[0], vy = py[1] - p[1], vz = py[2] - p[2];
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9) return new double[]{Math.cos(theta), 0.0, Math.sin(theta)};
            nx /= l; ny /= l; nz /= l;
            double rx = p[0] - centerXAt(t), rz = p[2] - centerZAt(t);
            if (nx * rx + nz * rz < 0.0) { nx = -nx; ny = -ny; nz = -nz; }
            return new double[]{nx, ny, nz};
        }

        /** Front (+Z) surface Z at a height fraction (used by the screen/keypad). */
        public double frontZ(double t) {
            t = clamp01(t);
            return surfacePoint(Math.PI * 0.5 - twist * t, t)[2];
        }

        public double maxHalfWidth() {
            double m = 0;
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                m = Math.max(m, ringHalfW(t) * (1.0 + rough + asym));
            }
            return m;
        }

        public double maxHalfDepth() {
            double m = 0;
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                m = Math.max(m, ringHalfD(t) * (1.0 + rough + asym));
            }
            return m;
        }

        /**
         * Largest horizontal distance from the block centre reached by the deformed shell,
         * including the lean of the body axis. Used by the block collision box.
         */
        public double maxHalfExtent() {
            double m = 0;
            double k = 1.0 + rough + asym;
            for (int i = 0; i <= 24; i++) {
                double t = i / 24.0;
                m = Math.max(m, Math.abs(centerXAt(t)) + ringHalfW(t) * k);
                m = Math.max(m, Math.abs(centerZAt(t)) + ringHalfD(t) * k);
            }
            return m;
        }

        public double topY() {
            return LIFT + height;
        }

        /** Center of the lower opening in model units (before facing and scale). */
        public double[] apertureCenter() {
            return new double[]{centerXAt(0.0), LIFT, centerZAt(0.0)};
        }

        /**
         * A point inside the lower opening. `radial` is 0 (center) .. 1 (rim).
         * The Y is slightly below the shell so food is already leaving the mouth.
         */
        public double[] aperturePoint(double angle, double radial) {
            radial = clamp01(radial);
            double[] c = apertureCenter();
            return new double[]{
                    c[0] + Math.cos(angle) * apertureHalfW * radial,
                    c[1] - 0.10,
                    c[2] + Math.sin(angle) * apertureHalfD * radial
            };
        }
    }
}

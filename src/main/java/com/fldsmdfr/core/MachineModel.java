package com.fldsmdfr.core;

/**
 * Pure appearance rules of the machine, shared by the client renderer and the server.
 * There are no independent pods or orbital structures: the whole machine is one central
 * body whose shape is described by {@link MachineBody}. This class only provides the
 * unbounded tier/scale/era/colour and the conversion between local and world offsets.
 *
 * <p>Units: "model units" = blocks at scale 1. The whole model is multiplied by
 * {@link #scale(double)}.</p>
 */
public final class MachineModel {
    public static final double ERA_SPAN = 6.0; // decades of evolution per era

    private MachineModel() {}

    /** Evolution tier T = log10(1 + E): unbounded, grows with every power of ten of evolution. */
    public static double tier(BigNum evolution) { return evolution.log10OnePlus(); }

    public static int era(double tier) { return (int) Math.floor(Math.max(0.0, tier) / ERA_SPAN); }

    /**
     * Overall body scale. It grows without a hard cap, but logarithmically, so the machine
     * can become colossal over thousands of cycles without exploding the world.
     */
    public static double scale(double tier) { return 1.0 + 0.50 * Math.log(1.0 + Math.max(0.0, tier)); }

    /**
     * Number of integrated mutation volumes drawn on the body (level of detail). The
     * logical component count is still unbounded and still drives production; this only
     * caps the amount of geometry used to show the mutations.
     */
    public static int renderCount(double tier, int cfgMax, BigNum components) {
        int cap = Math.min(MachineBody.MAX_FEATURES, Math.max(0, cfgMax));
        int byTier = (int) Math.min(cap, Math.max(0, Math.floor(Math.max(0.0, tier) * 1.25) - 1));
        int c = Math.min(cap, byTier);
        double n = components.toDouble();
        if (n < c) c = (int) Math.max(0, Math.floor(n));
        return Math.max(0, c);
    }

    /** Physical size (in blocks) of the food launched at this tier. Unbounded, grows with evolution. */
    public static double foodSize(double tier, double multiplier) {
        return 0.7 * Math.pow(1.0 + Math.max(0.0, tier), 0.35) * multiplier;
    }

    /** Hue (0..1) of the accent colour of an era; every era gets a new colour scheme. */
    public static float eraHue(long seed, int era) {
        if (era <= 0) return 0.065f; // original orange
        long h = Hash.mix(seed ^ (era * 0xD1B54A32D192ED03L));
        double jitter = ((h >>> 11) / (double) (1L << 53)) * 0.12;
        return (float) (((0.065 + 0.137 * era + jitter) % 1.0 + 1.0) % 1.0);
    }

    /** Approximate half-extent (blocks) of the body, for the render bounding box. */
    public static double extent(long seed, double tier, double scale) {
        return MachineBody.horizontalExtent(seed, tier, scale);
    }

    /** Unit random in [0,1) from a hash and a salt. */
    public static double unit(long h, int salt) {
        long x = Hash.mix(h + salt * 0x9E3779B97F4A7C15L);
        return (x >>> 11) / (double) (1L << 53);
    }

    /** Rotates a model-space point by the machine facing (same convention as GL rotate about +Y) and scales it. */
    public static double[] toWorldOffset(double[] local, double scale, double facingDeg) {
        double a = Math.toRadians(facingDeg);
        double c = Math.cos(a), s = Math.sin(a);
        double x = local[0] * scale, y = local[1] * scale, z = local[2] * scale;
        return new double[]{x * c + z * s, y, -x * s + z * c};
    }
}

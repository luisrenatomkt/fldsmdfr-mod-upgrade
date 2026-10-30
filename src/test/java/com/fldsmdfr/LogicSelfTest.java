package com.fldsmdfr;

import java.util.HashSet;
import java.util.Set;

import com.fldsmdfr.core.BigNum;
import com.fldsmdfr.core.MachineBody;
import com.fldsmdfr.core.MachineModel;
import com.fldsmdfr.core.MachineState;
import com.fldsmdfr.core.MachineStats;

/** Plain-Java self test of the Minecraft-independent logic (see docs/TESTING.md). */
public class LogicSelfTest {
    static int fails = 0;

    static void check(boolean ok, String msg) {
        if (!ok) { fails++; System.out.println("FAIL: " + msg); } else System.out.println("ok:   " + msg);
    }

    public static void main(String[] a) {
        // ---- BigNum
        BigNum x = BigNum.ONE;
        for (int i = 0; i < 1000; i++) x = x.mul(1e10);
        check(x.log10() > 9990 && x.log10() < 10010, "BigNum: 1e10000 without overflow (log10=" + x.log10() + ")");
        check(x.add(BigNum.ONE).compareTo(x) >= 0, "BigNum: adding 1 to 1e10000 stays ordered");
        check(BigNum.of(Double.NaN).isZero() && BigNum.of(Double.POSITIVE_INFINITY).toDouble() > 1e300, "BigNum: NaN/Infinity safe");
        check(BigNum.of(1234567).format().equals("1.23M"), "BigNum: abbreviation " + BigNum.of(1234567).format());
        check(Math.abs(BigNum.fromLog10(400).lnOnePlus() - 400 * Math.log(10)) < 1e-6, "BigNum: lnOnePlus of 1e400");

        // ---- stats: production depends on the number of components; bonus bounded
        MachineStats s = new MachineStats();
        s.recompute(BigNum.of(4), 99L, 0);
        BigNum c4 = s.capacityPerSec;
        s.recompute(BigNum.of(400), 99L, 0);
        BigNum c400 = s.capacityPerSec;
        s.recompute(BigNum.fromLog10(500), 99L, 0);
        check(c400.compareTo(c4) > 0, "stats: more components -> more production (" + c4 + " -> " + c400 + ")");
        check(s.capacityPerSec.log10() > 495 && s.capacityPerSec.log10() < 510, "stats: 1e500 components -> finite capacity " + s.capacityPerSec);
        check(s.integration >= 1.0 && s.integration <= 1.75 + 1e-9, "stats: integration bonus within [1,1.75]: " + s.integration);
        double sum = 0;
        for (double v : s.share) sum += v;
        check(Math.abs(sum - 1.0) < 1e-9, "stats: type shares sum to 1");
        MachineStats s2 = new MachineStats();
        s2.recompute(BigNum.of(400), 99L, 5);
        check(Math.abs(s2.integration - s.integration) > 1e-9 || s2.share[0] != s.share[0], "stats: composition changes with the era");

        // ---- visual evolution has no limit and keeps changing
        check(MachineModel.scale(1000) > MachineModel.scale(100) && MachineModel.scale(100) > MachineModel.scale(10), "model: body scale keeps growing");
        check(!Double.isInfinite(MachineModel.scale(1e9)), "model: scale finite for tier 1e9");
        check(MachineModel.foodSize(1000, 1) > MachineModel.foodSize(100, 1) && MachineModel.foodSize(100, 1) > MachineModel.foodSize(10, 1)
                && MachineModel.foodSize(10, 1) > MachineModel.foodSize(0, 1), "model: food size grows with evolution (0:" + MachineModel.foodSize(0, 1)
                + " 10:" + MachineModel.foodSize(10, 1) + " 100:" + MachineModel.foodSize(100, 1) + " 1000:" + MachineModel.foodSize(1000, 1) + ")");
        Set<Float> hues = new HashSet<Float>();
        for (int e = 0; e < 200; e++) hues.add(MachineModel.eraHue(12345L, e));
        check(hues.size() > 150, "model: every era has its own colour scheme (" + hues.size() + " distinct of 200)");
        check(MachineModel.eraHue(12345L, 7) == MachineModel.eraHue(12345L, 7), "model: era style deterministic");
        check(MachineModel.renderCount(1e6, 220, BigNum.fromLog10(300)) == MachineBody.MAX_FEATURES, "model: detail capped by the body's hard limit");
        check(MachineModel.renderCount(0, 20, BigNum.of(4)) == 0, "model: no mutation volumes at tier 0");
        check(MachineModel.renderCount(10, 20, BigNum.of(4)) == 4, "model: few mutation volumes at low component counts");
        check(MachineModel.era(5.99) == 0 && MachineModel.era(6.0) == 1 && MachineModel.era(61) == 10, "model: era boundaries");

        // ---- one central body: deterministic, quantised mutations, aperture at the bottom
        MachineBody.Form b0 = MachineBody.form(777L, 0.0);
        MachineBody.Form b0b = MachineBody.form(777L, 0.0);
        check(b0.height == b0b.height && b0.widthMul == b0b.widthMul && b0.featureCount == b0b.featureCount,
                "body: same seed and tier produce exactly the same form");
        MachineBody.Form b10 = MachineBody.form(777L, 10.0);
        check(b10.height > b0.height && b10.widthMul >= b0.widthMul && b10.maxHalfExtent() > b0.maxHalfExtent(),
                "body: central shell grows with evolution");

        double baseTier = 10.0 * MachineBody.MUTATION_STEP;
        MachineBody.Form m1 = MachineBody.form(777L, baseTier);
        MachineBody.Form m2 = MachineBody.form(777L, baseTier + MachineBody.MUTATION_STEP * 0.99);
        MachineBody.Form m3 = MachineBody.form(777L, baseTier + MachineBody.MUTATION_STEP);
        check(m1.mutation == m2.mutation, "body: mutation index is quantised, not re-rolled every frame");
        check(m3.mutation == m1.mutation + 1, "body: a new mutation step advances the index by one");
        double[] s1 = m1.surfacePoint(0.4, 0.5), s3 = m3.surfacePoint(0.4, 0.5);
        check(s1[0] != s3[0] || s1[2] != s3[2], "body: a new mutation actually deforms the shell");

        MachineBody.Form huge = MachineBody.form(42L, 1.0e6);
        check(huge.featureCount == MachineBody.MAX_FEATURES, "body: drawn details capped at " + MachineBody.MAX_FEATURES + " (evolution itself is not capped)");
        check(huge.height > b0.height * 1.5 && MachineModel.scale(1e6) > MachineModel.scale(1e3),
                "body: no artificial maximum stage (tier 1e6 is much larger)");

        double[] ap = b10.apertureCenter();
        double[] mouth = b10.aperturePoint(0.7, 1.0);
        check(ap[1] >= MachineBody.LIFT - 1e-9 && ap[1] < b10.topY(), "body: aperture is at the bottom of the body");
        check(mouth[1] < MachineBody.LIFT, "body: food sample is below the shell, never above the body");
        check(b10.apertureHalfW > 0 && b10.apertureHalfD > 0, "body: aperture has a positive opening");

        boolean downward = true, bounded = true;
        for (int i = 0; i <= 20; i++) {
            double u = i / 20.0;
            if (MachineBody.dischargeVelocityY(1.0, u) >= 0.0 || MachineBody.dischargeVelocityY(8.0, u) >= 0.0) downward = false;
            double hs = MachineBody.dischargeSpeedXZ(20.0, 10.0, u);
            if (hs < 0.0 || hs > 0.60) bounded = false;
        }
        check(downward, "discharge: vertical speed is always downward");
        check(bounded, "discharge: horizontal dispersion is bounded");
        check(MachineBody.dischargePeriod(0, 0, 0) >= 8.0, "discharge: period has a safe minimum");
        double open = MachineBody.dischargeOpen(0.0, 40.0);
        check(open >= 0.0 && open <= 1.0, "discharge: aperture opening stays in [0,1]");

        double[] w = MachineModel.toWorldOffset(new double[]{1, 0, 0}, 2.0, 90);
        check(Math.abs(w[0]) < 1e-9 && Math.abs(w[2] + 2.0) < 1e-9, "model: facing rotation matches GL rotate about +Y");

        // ---- full simulation: no NaN/Infinity over years, accelerating, persistent growth
        MachineState st = new MachineState();
        st.seed = 4242L;
        double t5 = 0, t60 = 0, tDay = 0;
        boolean finite = true, monotonic = true;
        BigNum prevE = st.evolution;
        long ticks = 0;
        double step = 200;           // 10 seconds
        for (long i = 0; i < 20L * 365 * 24 * 360; i++) {
            if (i > 2_000_000) step = 36000; // switch to 30 min steps after a while
            st.advance(step);
            ticks += (long) step;
            if (st.evolution.compareTo(prevE) < 0) monotonic = false;
            prevE = st.evolution;
            double T = st.tier();
            if (Double.isNaN(T) || Double.isInfinite(T) || Double.isNaN(st.instability)) finite = false;
            if (t5 == 0 && ticks >= 6000) t5 = T;
            if (t60 == 0 && ticks >= 72000) t60 = T;
            if (tDay == 0 && ticks >= 1728000) tDay = T;
            if (ticks > 20L * 365 * 24 * 72000) break;
            if (i > 2_000_000 && ticks > 20L * 365 * 24 * 72000) break;
        }
        System.out.println("      tier after 5 min=" + String.format("%.2f", t5) + ", 1 h=" + String.format("%.2f", t60)
                + ", 1 day=" + String.format("%.1f", tDay) + ", end=" + String.format("%.1f", st.tier())
                + " (era " + st.era() + "), instability=" + String.format("%.3g", st.instability)
                + ", produced=" + st.produced + ", scale=" + String.format("%.2f", MachineModel.scale(st.tier())));
        check(finite, "simulation: no NaN/Infinity over ~20 years of play");
        check(monotonic, "simulation: evolution never decreases");
        check(t60 > t5 && tDay > t60, "simulation: evolution keeps growing (5min " + t5 + " < 1h " + t60 + " < 1day " + tDay + ")");
        check(st.era() > 20, "simulation: keeps producing new eras (era " + st.era() + ")");

        System.out.println(fails == 0 ? "ALL TESTS PASSED" : (fails + " FAILURES"));
        if (fails != 0) System.exit(1);
    }
}

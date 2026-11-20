package thurnos.brake_simulation.engine;

/**
 * Longitudinal tire behaviour: a friction-versus-slip curve in the "magic formula" shape
 * {@code mu(k) = sin(C · atan(B·k − E·(B·k − atan(B·k))))}, scaled so its highest point is exactly {@code peakMu}.
 *
 * <p>Slip {@code k} is 0 for a freely rolling wheel and 1 for a fully locked one. Friction climbs to a peak at
 * moderate slip (roughly 10–25%) and then falls toward a lower sliding value. That drop is what makes locked
 * wheels stop a car later than wheels held just short of locking.
 *
 * <p>The shape numbers B, C and E are typical illustrative values per surface family, not fitted to any
 * particular tire.
 *
 * @param peakMu          highest friction coefficient on the curve (tire grip × road × wear)
 * @param loadSensitivity how much friction drops as an axle gets more loaded than at rest
 *                        (0 = none; about 0.1 is typical)
 * @param slipB           stiffness: how steeply friction rises at low slip
 * @param slipC           shape: above 1 gives a peak followed by a drop
 * @param slipE           curvature around the peak
 * @param water           water film on the road and what the tread does about it (hydroplaning)
 */
public record TireSpec(double peakMu, double loadSensitivity, double slipB, double slipC, double slipE,
                       WaterFilm water) {

    public TireSpec {
        VehicleSpec.require(peakMu > 0, "peakMu must be positive");
        VehicleSpec.require(loadSensitivity >= 0 && loadSensitivity < 1, "loadSensitivity must be in [0,1)");
        VehicleSpec.require(slipB > 0, "slipB must be positive");
        VehicleSpec.require(slipC > 1, "slipC must be above 1 so the curve has a peak");
        VehicleSpec.require(slipE <= 1, "slipE must not exceed 1");
        VehicleSpec.require(water != null, "water is required (use WaterFilm.NONE for a dry road)");
    }

    /** Tire on a road with no water. */
    public TireSpec(double peakMu, double loadSensitivity, double slipB, double slipC, double slipE) {
        this(peakMu, loadSensitivity, slipB, slipC, slipE, WaterFilm.NONE);
    }

    public static TireSpec forSurface(double peakMu, SurfaceKind kind) {
        return new TireSpec(peakMu, 0.10, kind.stiffness, kind.shape, kind.curvature, WaterFilm.NONE);
    }

    /** Dry-asphalt curve shape with the given peak friction. */
    public static TireSpec of(double peakMu) {
        return forSurface(peakMu, SurfaceKind.DRY);
    }

    public TireSpec withLoadSensitivity(double k) {
        return new TireSpec(peakMu, k, slipB, slipC, slipE, water);
    }

    public TireSpec withPeakMu(double mu) {
        return new TireSpec(mu, loadSensitivity, slipB, slipC, slipE, water);
    }

    public TireSpec withWater(WaterFilm film) {
        return new TireSpec(peakMu, loadSensitivity, slipB, slipC, slipE, film);
    }

    /** Builds the lookup used by the engine; do this once per run, it scans the curve to find its peak. */
    public SlipCurve curve() {
        return new SlipCurve(this);
    }

    /** Friction versus slip for one tire. */
    public static final class SlipCurve {

        private final double peakMu;
        private final double b, c, e;
        private final double norm;
        private final double peakSlip;

        private SlipCurve(TireSpec t) {
            this.peakMu = t.peakMu;
            this.b = t.slipB;
            this.c = t.slipC;
            this.e = t.slipE;
            double best = 0, at = 0;
            for (int i = 0; i <= 2000; i++) {
                double k = i / 2000.0;
                double r = raw(k);
                if (r > best) {
                    best = r;
                    at = k;
                }
            }
            this.norm = best;
            this.peakSlip = at;
        }

        private double raw(double k) {
            double bk = b * k;
            return Math.sin(c * Math.atan(bk - e * (bk - Math.atan(bk))));
        }

        /** Friction coefficient at slip {@code k} (clamped to 0..1). */
        public double mu(double k) {
            double kk = Math.min(1, Math.max(0, k));
            return Math.max(0, peakMu * raw(kk) / norm);
        }

        /** d(mu)/d(slip), by central difference. */
        public double slope(double k) {
            double h = 1e-4;
            double lo = Math.max(0, k - h), hi = Math.min(1, k + h);
            return (mu(hi) - mu(lo)) / (hi - lo);
        }

        /** Slip at which friction is highest. */
        public double peakSlip() {
            return peakSlip;
        }

        /** Friction of a fully locked, sliding wheel. */
        public double slidingMu() {
            return mu(1.0);
        }
    }
}

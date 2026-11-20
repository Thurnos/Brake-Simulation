package thurnos.brake_simulation.engine;

import java.util.List;

/**
 * Fits the engine to measured stops. The one number it adjusts is a grip scale: every tire in the given
 * scenarios gets {@code peakMu × scale}. If the engine stops too long on this surface, the scale comes out above 1
 * (the real surface grips better than the table says), and the other way round.
 *
 * <p>Use one fit per surface family, so a wrong dry figure cannot be hidden by a wrong wet one. The fit minimises
 * the sum of squared relative errors of braking distance (reaction distance excluded), so a 5 m error on a
 * 20 m stop counts as much as 25 m on a 100 m stop.
 */
public final class Calibrator {

    private static final double MIN_SCALE = 0.3;
    private static final double MAX_SCALE = 2.5;
    private static final double GOLDEN = 0.6180339887498949;

    private Calibrator() {
    }

    /** One real stop: the scenario that describes it and the braking distance that was measured. */
    public record Measurement(Scenario scenario, double measuredBrakingM) {
        public Measurement {
            VehicleSpec.require(scenario != null, "scenario is required");
            VehicleSpec.require(measuredBrakingM > 0, "measured braking distance must be positive");
        }
    }

    /**
     * @param gripScale          the factor to multiply peak friction with
     * @param rmsRelErrorBefore  root-mean-square relative error with scale 1
     * @param rmsRelErrorAfter   the same at the fitted scale
     * @param n                  number of measurements
     * @param atBound            true if the fit ran into the search limits, so it should not be trusted
     */
    public record Fit(double gripScale, double rmsRelErrorBefore, double rmsRelErrorAfter, int n, boolean atBound) {
    }

    public static Fit fitGripScale(BrakingEngine engine, List<Measurement> ms) {
        VehicleSpec.require(ms != null && !ms.isEmpty(), "at least one measurement is needed");
        double before = rmsRelError(engine, ms, 1.0);

        // coarse scan to find the bracket, then golden-section search inside it (log scale)
        int steps = 40;
        double lo0 = Math.log(MIN_SCALE), hi0 = Math.log(MAX_SCALE);
        int best = 0;
        double bestErr = Double.MAX_VALUE;
        for (int i = 0; i <= steps; i++) {
            double e = rmsRelError(engine, ms, Math.exp(lo0 + (hi0 - lo0) * i / steps));
            if (e < bestErr) {
                bestErr = e;
                best = i;
            }
        }
        double a = lo0 + (hi0 - lo0) * Math.max(0, best - 1) / steps;
        double b = lo0 + (hi0 - lo0) * Math.min(steps, best + 1) / steps;
        double c = b - GOLDEN * (b - a), d = a + GOLDEN * (b - a);
        double fc = rmsRelError(engine, ms, Math.exp(c)), fd = rmsRelError(engine, ms, Math.exp(d));
        for (int i = 0; i < 40; i++) {
            if (fc < fd) {
                b = d; d = c; fd = fc;
                c = b - GOLDEN * (b - a);
                fc = rmsRelError(engine, ms, Math.exp(c));
            } else {
                a = c; c = d; fc = fd;
                d = a + GOLDEN * (b - a);
                fd = rmsRelError(engine, ms, Math.exp(d));
            }
        }
        double scale = Math.exp(0.5 * (a + b));
        boolean atBound = scale < MIN_SCALE * 1.02 || scale > MAX_SCALE / 1.02;
        return new Fit(scale, before, rmsRelError(engine, ms, scale), ms.size(), atBound);
    }

    /** Root-mean-square relative error of the engine's braking distance at the given grip scale. */
    public static double rmsRelError(BrakingEngine engine, List<Measurement> ms, double scale) {
        double sum = 0;
        for (Measurement m : ms) {
            Scenario s = m.scenario();
            Scenario scaled = s.withTires(s.tires().withPeakMu(s.tires().peakMu() * scale));
            SimulationResult r = engine.simulate(scaled).withoutTrace();
            // a car that fails to stop counts as a very long stop, so the search is pushed away from it
            double predicted = r.brakingDistanceM() != null ? r.brakingDistanceM() : 10 * r.distanceTravelledM();
            double rel = (predicted - m.measuredBrakingM()) / m.measuredBrakingM();
            sum += rel * rel;
        }
        return Math.sqrt(sum / ms.size());
    }
}

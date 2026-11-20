package thurnos.brake_simulation.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Time-stepped straight-line braking simulation.
 *
 * <p>Each step the engine works out the brake force on each axle, limited by what the tires can hold,
 * adds drag, rolling resistance and slope, and advances speed and position. Pure Java: no Spring,
 * no database, no randomness, so the same {@link Scenario} always gives the same result.
 *
 * <p>What is modelled in Phase 1:
 * <ul>
 *   <li>driver delay, then brake force building up over the pedal rise time</li>
 *   <li>weight transfer from the rear to the front axle under braking (and on downhill slopes)</li>
 *   <li>per-axle grip limits with a mild load sensitivity</li>
 *   <li>locked wheels (sliding friction) versus ABS (holds close to peak friction)</li>
 *   <li>aerodynamic drag, rolling resistance, slope with sin/cos</li>
 * </ul>
 *
 * <p>Not yet modelled: tire slip curves, an ABS control loop, wet-road effects, brake heat.
 * Those arrive in later phases behind the same {@code Scenario} / {@code SimulationResult} contract.
 *
 * <p>Within one step the deceleration is held constant and position is advanced exactly
 * ({@code x += v·dt − ½·a·dt²}), so a constant-deceleration case reproduces v²/2a to rounding error.
 */
public final class BrakingEngine {

    static final double G = 9.81;
    static final double AIR_DENSITY = 1.225;

    private static final int MAX_ITERATIONS = 30;
    private static final double MIN_MU_FRACTION = 0.3;

    public SimulationResult simulate(Scenario scenario) {
        return simulate(scenario, EngineConfig.defaults());
    }

    public SimulationResult simulate(Scenario s, EngineConfig cfg) {
        final VehicleSpec veh = s.vehicle();
        final BrakeSpec brk = s.brakes();
        final TireSpec tire = s.tires();

        final double m = veh.massKg();
        final double wheelbase = veh.wheelbaseM();
        final double cgHeight = veh.cgHeightM();

        final double theta = Math.atan(s.slopePercent() / 100.0);
        final double normalLoad = m * G * Math.cos(theta);          // total load pressing on the road
        final double frontLoadStatic = normalLoad * veh.frontWeightFraction();
        final double rearLoadStatic = normalLoad - frontLoadStatic;
        final double slopeForce = m * G * Math.sin(theta);          // pulls the car forward when downhill
        final double rollingForce = veh.rollingResistance() * normalLoad;
        final double dragFactor = 0.5 * AIR_DENSITY * veh.dragCoefficient() * veh.frontalAreaM2();

        final double delay = s.driver().totalDelayS();
        final double rise = brk.pedalRiseTimeS();
        final double dt = cfg.dtS();

        double t = 0;
        double x = 0;
        double v = s.initialSpeedMs();

        double brakeForcePrev = 0;
        boolean frontLatched = false;
        boolean rearLatched = false;

        double peakDecel = 0;
        double reactionDistance = Double.NaN;
        Double impactSpeedMs = null;
        boolean stopped = false;
        double stopTime = 0;

        List<TracePoint> trace = new ArrayList<>();
        double nextTrace = 0;

        Axles last = new Axles();

        while (t < cfg.maxTimeS()) {
            if (v <= 0) {
                stopped = true;
                stopTime = t;
                break;
            }

            // Sampled at the middle of the step: while force is building, sampling at the start
            // would under-brake by half a step every step (a small but systematic error).
            double pedal = pedalPosition(t + 0.5 * dt, delay, rise);
            double drag = dragFactor * v * v;

            double demandTotal = pedal * brk.maxForceFactor() * m * G;
            double demandFront = demandTotal * brk.frontBias();
            double demandRear = demandTotal - demandFront;

            // Axle loads depend on the brake force, and the brake force depends on the axle loads.
            // Fixed-point iteration settles in a few passes because the coupling is weak.
            double brakeForce = brakeForcePrev;
            Axles axles = last;
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                double transfer = cgHeight * (brakeForce + drag) / wheelbase;
                double loadFront = Math.max(0, frontLoadStatic + transfer);
                double loadRear = Math.max(0, rearLoadStatic - transfer);

                axles = new Axles();
                axles.loadFront = loadFront;
                axles.loadRear = loadRear;
                axles.lockedFront = applyAxle(demandFront, loadFront, frontLoadStatic, tire, s.absEnabled(),
                        cfg.absEfficiency(), frontLatched, true, axles);
                axles.lockedRear = applyAxle(demandRear, loadRear, rearLoadStatic, tire, s.absEnabled(),
                        cfg.absEfficiency(), rearLatched, false, axles);

                double updated = axles.forceFront + axles.forceRear;
                boolean settled = Math.abs(updated - brakeForce) < 1e-7 * m;
                brakeForce = updated;
                if (settled) break;
            }
            last = axles;
            brakeForcePrev = brakeForce;

            if (!s.absEnabled()) {
                frontLatched |= axles.lockedFront;
                rearLatched |= axles.lockedRear;
            }

            double decel = (brakeForce + drag + rollingForce - slopeForce) / m;
            peakDecel = Math.max(peakDecel, decel);

            if (t >= nextTrace) {
                trace.add(point(t, x, v, decel, pedal, axles));
                nextTrace += cfg.traceIntervalS();
            }

            double vNext = v - decel * dt;
            double step = dt;
            boolean reachesRest = decel > 0 && vNext <= 0;
            if (reachesRest) {
                step = v / decel;
            }
            double xNext = x + v * step - 0.5 * decel * step * step;

            if (Double.isNaN(reactionDistance) && t + step >= delay) {
                double tau = Math.max(0, delay - t);
                reactionDistance = x + v * tau - 0.5 * decel * tau * tau;
            }
            if (s.obstacleDistanceM() != null && impactSpeedMs == null && xNext >= s.obstacleDistanceM()) {
                double d = s.obstacleDistanceM() - x;
                impactSpeedMs = Math.sqrt(Math.max(0, v * v - 2 * (decel) * d));
            }

            t += step;
            x = xNext;
            if (reachesRest) {
                v = 0;
                stopped = true;
                stopTime = t;
                trace.add(point(t, x, 0, decel, pedal, axles));
                break;
            }
            v = vNext;
        }

        if (!stopped && trace.isEmpty()) {
            trace.add(point(t, x, v, 0, 0, last));
        }
        if (Double.isNaN(reactionDistance)) {
            reactionDistance = x;
        }
        if (s.obstacleDistanceM() != null && impactSpeedMs == null) {
            impactSpeedMs = 0.0;
        }

        Double impactKmh = impactSpeedMs == null ? null : impactSpeedMs * 3.6;
        if (stopped) {
            return new SimulationResult(true, stopTime, x, reactionDistance, x - reactionDistance,
                    x, stopTime, peakDecel, impactKmh, List.copyOf(trace));
        }
        return new SimulationResult(false, null, null, reactionDistance, null,
                x, t, peakDecel, impactKmh, List.copyOf(trace));
    }

    /** 0 until the foot reaches the pedal, then a linear build-up to 1 over {@code rise} seconds. */
    static double pedalPosition(double t, double delay, double rise) {
        if (t < delay) return 0;
        if (rise <= 0) return 1;
        return Math.min(1, (t - delay) / rise);
    }

    /**
     * Works out one axle's brake force and stores it in {@code out}.
     *
     * @return true if the axle is locked (or, with ABS, being held at its limit)
     */
    private static boolean applyAxle(double demand, double load, double staticLoad, TireSpec tire,
                                     boolean abs, double absEfficiency, boolean latched,
                                     boolean front, Axles out) {
        double relativeLoad = staticLoad > 0 ? load / staticLoad : 1;
        double mu = tire.peakMu() * (1 - tire.loadSensitivity() * (relativeLoad - 1));
        mu = Math.max(mu, MIN_MU_FRACTION * tire.peakMu());
        double limit = mu * load;

        boolean overLimit = demand > limit || latched;
        double force;
        if (!overLimit) {
            force = demand;
        } else if (abs) {
            force = Math.min(demand, absEfficiency * limit);
        } else {
            force = Math.min(demand, tire.lockedFrictionRatio() * limit);
        }
        if (front) out.forceFront = force; else out.forceRear = force;
        return overLimit && demand > 0;
    }

    private static TracePoint point(double t, double x, double v, double decel, double pedal, Axles a) {
        return new TracePoint(t, x, v, decel, pedal, a.forceFront, a.forceRear,
                a.loadFront, a.loadRear, a.lockedFront, a.lockedRear);
    }

    /** Mutable scratch values for one step. */
    private static final class Axles {
        double forceFront, forceRear, loadFront, loadRear;
        boolean lockedFront, lockedRear;
    }
}

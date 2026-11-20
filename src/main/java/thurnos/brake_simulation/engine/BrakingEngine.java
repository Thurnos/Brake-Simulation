package thurnos.brake_simulation.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Time-stepped straight-line braking simulation.
 *
 * <p>The car is two axles (a "bicycle" model). Each axle has a spinning wheel with its own inertia. The brake
 * pushes the wheel back with a torque, the road pushes it forward with a tire force that depends on how much the
 * wheel is slipping, and that tire force is also what slows the car:
 *
 * <pre>
 *   wheel:  I · dω/dt = Fx · R  −  brakeTorque
 *   tire:   Fx        = mu(slip) · Fz          slip = (v − ω·R) / v
 *   car:    m · a     = ΣFx + drag + rolling resistance − slope pull
 * </pre>
 *
 * <p>Because friction falls past its peak, a wheel pushed past the limit runs away toward lock-up. Without ABS
 * that is what happens; with ABS a controller watches the wheel and releases and re-applies pressure to keep it
 * near the peak. Nothing here is a fixed "ABS makes it 95% as good" factor: stopping distances come out of the
 * mechanism.
 *
 * <p>On a wet road the tires lose friction as the car approaches and passes the hydroplaning speed of the
 * {@link WaterFilm}, and regain it as the car slows.
 *
 * <p>Also modelled: driver delay and pedal rise, weight transfer between the axles, a mild load sensitivity of
 * grip, aerodynamic drag, rolling resistance, slope with sin/cos, and the energy the brakes absorb.
 *
 * <p>Pure Java: no Spring, no database, no randomness, so a {@link Scenario} always gives the same result.
 * Not yet modelled: brake heating and fade (Phase 4), cornering.
 *
 * <p>Numerics: the wheel equation is stiff at low speed, so it is advanced with a linearly implicit step that
 * stays stable at the default 1 ms. Car speed and position are advanced exactly for the step's constant
 * deceleration.
 */
public final class BrakingEngine {

    static final double G = 9.81;
    static final double AIR_DENSITY = 1.225;

    /**
     * Slip is computed against at least this speed so it stays finite as the car comes to rest.
     */
    private static final double MIN_SLIP_SPEED = 0.05;
    private static final double MIN_MU_FRACTION = 0.3;
    private static final double LOCKED_SLIP = 0.95;

    public SimulationResult simulate(Scenario scenario) {
        return simulate(scenario, EngineConfig.defaults());
    }

    public SimulationResult simulate(Scenario s, EngineConfig cfg) {
        final VehicleSpec veh = s.vehicle();
        final BrakeSpec brk = s.brakes();
        final AbsSettings abs = cfg.abs();
        final TireSpec.SlipCurve curve = s.tires().curve();
        final WaterFilm water = s.tires().water();

        final double m = veh.massKg();
        final double wheelbase = veh.wheelbaseM();
        final double cgHeight = veh.cgHeightM();
        final double radius = veh.wheelRadiusM();
        final double inertia = veh.axleInertiaKgM2();

        final double theta = Math.atan(s.slopePercent() / 100.0);
        final double normalLoad = m * G * Math.cos(theta);
        final double frontLoadStatic = normalLoad * veh.frontWeightFraction();
        final double rearLoadStatic = normalLoad - frontLoadStatic;
        final double slopeForce = m * G * Math.sin(theta);
        final double rollingForce = veh.rollingResistance() * normalLoad;
        final double dragFactor = 0.5 * AIR_DENSITY * veh.dragCoefficient() * veh.frontalAreaM2();

        final double delay = s.driver().totalDelayS();
        final double dt = cfg.dtS();

        double t = 0;
        double x = 0;
        double v = s.initialSpeedMs();

        Axle front = new Axle(v / radius, frontLoadStatic, v);
        Axle rear = new Axle(v / radius, rearLoadStatic, v);

        double tireForcePrev = 0;
        double decelPrev = 0;
        double peakDecel = 0;
        double reactionDistance = Double.NaN;
        Double impactSpeedMs = null;
        boolean stopped = false;
        double stopTime = 0;
        double brakeEnergy = 0;
        double slipEnergy = 0;

        List<TracePoint> trace = new ArrayList<>();
        double nextTrace = 0;

        while (t < cfg.maxTimeS()) {
            if (v <= 0) {
                stopped = true;
                stopTime = t;
                break;
            }

            // Sampled at mid-step: while force is building, sampling at the start would under-brake.
            double pedal = pedalPosition(t + 0.5 * dt, delay, brk.pedalRiseTimeS());
            double drag = dragFactor * v * v;

            double demandTotal = pedal * brk.maxForceFactor() * m * G;
            double demandFront = demandTotal * brk.frontBias();
            double demandRear = demandTotal - demandFront;

            // Axle loads follow the braking force of the previous step (one step of lag, 1 ms).
            double transfer = cgHeight * (tireForcePrev + drag) / wheelbase;
            double loadFront = Math.max(0, frontLoadStatic + transfer);
            double loadRear = Math.max(0, rearLoadStatic - transfer);

            boolean absOn = s.absEnabled() && v > abs.minSpeedMs();
            double speedAtEnd = Math.max(0, v - decelPrev * dt);   // where the car will be by the end of this step
            double waterGrip = water.gripFactor(speedAtEnd);       // 1 unless the tires are hydroplaning

            step(front, demandFront, loadFront, frontLoadStatic, v, speedAtEnd, waterGrip, pedal, absOn, abs, curve,
                    s.tires().loadSensitivity(), radius, inertia, dt);
            step(rear, demandRear, loadRear, rearLoadStatic, v, speedAtEnd, waterGrip, pedal, absOn, abs, curve,
                    s.tires().loadSensitivity(), radius, inertia, dt);

            double tireForce = front.fx + rear.fx;
            double decel = (tireForce + drag + rollingForce - slopeForce) / m;
            peakDecel = Math.max(peakDecel, decel);
            brakeEnergy += front.brakeEnergy + rear.brakeEnergy;
            slipEnergy += front.slipPower + rear.slipPower;

            if (t >= nextTrace) {
                trace.add(point(t, x, v, decel, pedal, front, rear, waterGrip));
                nextTrace += cfg.traceIntervalS();
            }

            double vNext = v - decel * dt;
            double stepLength = dt;
            boolean reachesRest = decel > 0 && vNext <= 0;
            if (reachesRest) {
                stepLength = v / decel;
            }
            double xNext = x + v * stepLength - 0.5 * decel * stepLength * stepLength;

            if (Double.isNaN(reactionDistance) && t + stepLength >= delay) {
                double tau = Math.max(0, delay - t);
                reactionDistance = x + v * tau - 0.5 * decel * tau * tau;
            }
            if (s.obstacleDistanceM() != null && impactSpeedMs == null && xNext >= s.obstacleDistanceM()) {
                double d = s.obstacleDistanceM() - x;
                impactSpeedMs = Math.sqrt(Math.max(0, v * v - 2 * decel * d));
            }

            t += stepLength;
            x = xNext;
            tireForcePrev = tireForce;
            decelPrev = decel;
            if (reachesRest) {
                v = 0;
                front.omega = 0;
                rear.omega = 0;
                stopped = true;
                stopTime = t;
                trace.add(point(t, x, 0, decel, pedal, front, rear, 1.0));
                break;
            }
            v = vNext;
        }

        if (!stopped && trace.isEmpty()) {
            trace.add(point(t, x, v, 0, 0, front, rear, water.gripFactor(v)));
        }
        if (Double.isNaN(reactionDistance)) {
            reactionDistance = x;
        }
        if (s.obstacleDistanceM() != null && impactSpeedMs == null) {
            impactSpeedMs = 0.0;
        }

        Double impactKmh = impactSpeedMs == null ? null : impactSpeedMs * 3.6;
        Double onsetKmh = water.isWet() ? water.onsetSpeedMs() * 3.6 : null;
        if (stopped) {
            return new SimulationResult(true, stopTime, x, reactionDistance, x - reactionDistance,
                    x, stopTime, peakDecel, impactKmh, brakeEnergy, slipEnergy, onsetKmh, List.copyOf(trace));
        }
        return new SimulationResult(false, null, null, reactionDistance, null,
                x, t, peakDecel, impactKmh, brakeEnergy, slipEnergy, onsetKmh, List.copyOf(trace));
    }

    /** 0 until the foot reaches the pedal, then a linear build-up to 1 over {@code rise} seconds. */
    static double pedalPosition(double t, double delay, double rise) {
        if (t < delay) return 0;
        if (rise <= 0) return 1;
        return Math.min(1, (t - delay) / rise);
    }

    /** Advances one axle's wheel by one step and works out the tire force it puts on the road. */
    private static void step(Axle ax, double demandForce, double load, double loadStatic, double v,
                             double vEnd, double waterGrip, double pedal, boolean absOn, AbsSettings abs,
                             TireSpec.SlipCurve curve, double loadSensitivity,
                             double radius, double inertia, double dt) {

        double speedForSlip = Math.max(v, MIN_SLIP_SPEED);
        // A wheel that has stopped turning is fully locked whatever the car's speed; without this the
        // slip of a locked wheel would shrink with the car and friction would fade away at crawling speed.
        double slip = ax.omega <= 1e-9 && v > 0
                ? 1.0
                : clamp((v - ax.omega * radius) / speedForSlip, 0, 1);

        // --- ABS: every control period, judge how the wheel behaved over the window. Release while it
        //     runs away from the car, re-apply once it recovers; ramp pressure in between.
        if (absOn) {
            ax.sinceSample += dt;
            if (ax.sinceSample >= abs.controlPeriodS()) {
                double wheelDecelAtTire = -(ax.omega - ax.sampleOmega) / ax.sinceSample * radius;
                double carDecelWindow = (ax.sampleSpeed - v) / ax.sinceSample;
                double divergence = wheelDecelAtTire - carDecelWindow;
                if (!ax.releasing) {
                    if (divergence > abs.divergenceMs2() || slip > abs.hardSlip()) ax.releasing = true;
                } else if (divergence < 0 && slip < abs.exitSlip()) {
                    ax.releasing = false;
                }
                ax.sampleOmega = ax.omega;
                ax.sampleSpeed = v;
                ax.sinceSample = 0;
            }
            ax.pressure += (ax.releasing ? -abs.releaseRate() : abs.applyRate()) * dt;
            ax.pressure = clamp(ax.pressure, 0, 1);
        } else {
            ax.pressure = 1;
            ax.releasing = false;
            ax.sampleOmega = ax.omega;
            ax.sampleSpeed = v;
            ax.sinceSample = 0;
        }

        double brakeTorque = demandForce * ax.pressure * radius;

        // --- Tire force at the END of the step. The wheel is advanced to match the car's speed at that
        //     moment; matching the speed at the start would leave it one step behind and shave a little
        //     off the braking force every step (about a·dt/v of slip, which grows as the car slows).
        double speedEnd = Math.max(vEnd, MIN_SLIP_SPEED);
        double slipEnd = ax.omega <= 1e-9 && vEnd > 0
                ? 1.0
                : clamp((vEnd - ax.omega * radius) / speedEnd, 0, 1);
        double relativeLoad = loadStatic > 0 ? load / loadStatic : 1;
        double muScale = Math.max(MIN_MU_FRACTION, 1 - loadSensitivity * (relativeLoad - 1)) * waterGrip;
        double fx0 = curve.mu(slipEnd) * muScale * load;
        double dFxdOmega = -load * curve.slope(slipEnd) * muScale * radius / speedEnd;

        // --- Wheel: linearly implicit step, stable even where the tire is very stiff.
        double accel = (fx0 * radius - brakeTorque) / inertia;
        double denominator = Math.max(0.2, 1 - dt * (radius / inertia) * dFxdOmega);
        double dOmega = dt * accel / denominator;
        double omegaNew = ax.omega + dOmega;
        if (omegaNew < 0) {          // brake torque beyond what the tire can give: the wheel is locked
            omegaNew = 0;
            dOmega = -ax.omega;
        }
        double fxEnd = Math.max(0, fx0 + dFxdOmega * dOmega);
        double fxMean = 0.5 * (ax.fxEnd + fxEnd);     // trapezoid between the previous and this step's end force
        ax.fxEnd = fxEnd;

        double omegaMean = 0.5 * (ax.omega + omegaNew);
        ax.brakeEnergy = brakeTorque * omegaMean * dt;
        ax.slipPower = fxMean * Math.max(0, v - omegaMean * radius) * dt;

        ax.omegaDot = dOmega / dt;
        ax.omega = omegaNew;
        ax.fx = fxMean;
        ax.load = load;
        ax.slip = slip;
        ax.locked = slip > LOCKED_SLIP && demandForce > 0;
        ax.pedal = pedal;
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.min(hi, Math.max(lo, value));
    }

    private static TracePoint point(double t, double x, double v, double decel, double pedal, Axle f, Axle r,
                                    double waterGrip) {
        return new TracePoint(t, x, v, decel, pedal, f.fx, r.fx, f.load, r.load, f.locked, r.locked,
                f.slip, r.slip, pedal * f.pressure, pedal * r.pressure, waterGrip);
    }

    /** State of one axle's wheel and its ABS channel. */
    private static final class Axle {
        double omega;               // wheel speed, rad/s
        double omegaDot;            // wheel acceleration from the last step
        double sampleOmega;         // wheel speed at the last ABS control sample
        double sampleSpeed;         // car speed at the last ABS control sample
        double sinceSample;         // time since the last ABS control sample
        double pressure = 1;        // share of the driver's brake demand that reaches the brake
        boolean releasing;
        double fx;                  // tire force on the road (mean over the last step)
        double fxEnd;               // tire force at the end of the last step
        double load;
        double slip;
        boolean locked;
        double pedal;
        double brakeEnergy;         // this step only
        double slipPower;           // slip energy lost this step

        Axle(double omega, double load, double speed) {
            this.omega = omega;
            this.sampleOmega = omega;
            this.sampleSpeed = speed;
            this.load = load;
        }
    }
}

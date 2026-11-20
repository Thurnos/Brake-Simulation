package thurnos.brake_simulation.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation of the time-stepped engine. Where possible the expected value comes from a closed-form
 * result or a conservation law, so a failure points at the engine and not at a made-up reference number.
 */
class BrakingEngineTest {

    private static final double G = 9.81;
    private static final double V = Scenario.kmhToMs(100);

    private final BrakingEngine engine = new BrakingEngine();

    /**
     * Gentle braking well inside the grip limit; nothing slips much, nothing locks. ABS is left off so the
     * closed-form checks see the brake system alone; an instant pedal step would make any ABS react to the
     * wheel's initial spin-down, which no real pedal produces.
     */
    private static Scenario ideal() {
        return new Scenario(
                VehicleSpec.estimate(1500).withoutResistances(),
                new BrakeSpec(0.5, 0.70, 0.0),
                TireSpec.of(1.5).withLoadSensitivity(0),
                new DriverSpec(0.5, 0.2),
                V, 0, false, null);
    }

    /** Brakes much stronger than the tires: the tires, and the ABS if fitted, decide. */
    private static Scenario gripLimited(double mu, SurfaceKind kind) {
        return new Scenario(
                VehicleSpec.estimate(1500).withoutResistances(),
                new BrakeSpec(2.0, 0.70, 0.15),
                TireSpec.forSurface(mu, kind).withLoadSensitivity(0),
                new DriverSpec(0.5, 0.2),
                V, 0, true, null);
    }

    private static Scenario dry() {
        return gripLimited(1.1, SurfaceKind.DRY);
    }

    private static EngineConfig fineTrace() {
        return new EngineConfig(0.001, 120.0, 0.005, AbsSettings.defaults());
    }

    /** Average deceleration while the car slows from 80% to 20% of its start speed, after the transients. */
    private static double steadyDecel(SimulationResult r, double v0) {
        double xa = Double.NaN, va = 0, xb = Double.NaN, vb = 0;
        for (TracePoint p : r.trace()) {
            if (Double.isNaN(xa) && p.speedMs() <= 0.8 * v0) {
                xa = p.positionM();
                va = p.speedMs();
            }
            if (Double.isNaN(xb) && p.speedMs() <= 0.2 * v0) {
                xb = p.positionM();
                vb = p.speedMs();
                break;
            }
        }
        assertFalse(Double.isNaN(xa) || Double.isNaN(xb), "run never covered the 80%..20% speed window");
        return (va * va - vb * vb) / (2 * (xb - xa));
    }

    // ------------------------------------------------------------------ closed-form checks

    @Test
    void gentleBrakingMatchesVSquaredOverTwoAWithTheSpinningWheelsCounted() {
        Scenario s = ideal();
        SimulationResult r = engine.simulate(s);

        // Brake force D = 0.5 m g has to slow the car and the spinning wheels together.
        double a = 0.5 * s.vehicle().massKg() * G / s.vehicle().effectiveMassKg();
        double reaction = V * 0.7;
        double braking = V * V / (2 * a);

        assertTrue(r.stopped());
        assertEquals(reaction, r.reactionDistanceM(), 1e-6);
        // 0.04 m of the gap to the closed form is real: the tires need a little slip to transmit force.
        assertEquals(braking, r.brakingDistanceM(), 0.1);
        assertEquals(0.7 + V / a, r.stopTimeS(), 0.01);
    }

    @Test
    void pedalRampAddsTheDistanceTheMathPredicts() {
        double rise = 0.3;
        Scenario instantScenario = ideal();
        double a = 0.5 * instantScenario.vehicle().massKg() * G / instantScenario.vehicle().effectiveMassKg();
        Scenario ramped = instantScenario.withBrakes(new BrakeSpec(0.5, 0.70, rise));

        double instant = engine.simulate(instantScenario).requireStopDistanceM();
        double withRamp = engine.simulate(ramped).requireStopDistanceM();

        // Linear force ramp over T costs v*T/2 - a*T^2/24 extra metres.
        double expectedExtra = V * rise / 2 - a * rise * rise / 24;
        assertEquals(expectedExtra, withRamp - instant, 0.02);
    }

    @Test
    void brakeAndTireSlipEnergyAddUpToTheKineticEnergyThatWasThere() {
        for (boolean abs : new boolean[]{true, false}) {
            Scenario s = dry().withAbs(abs);
            SimulationResult r = engine.simulate(s);
            VehicleSpec v = s.vehicle();

            double omega = V / v.wheelRadiusM();
            double initial = 0.5 * v.massKg() * V * V + 2 * 0.5 * v.axleInertiaKgM2() * omega * omega;
            double absorbed = r.brakeEnergyJ() + r.tireSlipEnergyJ();

            assertEquals(1.0, absorbed / initial, 0.02, "energy balance with abs=" + abs);
            if (abs) {
                assertTrue(r.brakeEnergyJ() > 0.6 * initial, "with ABS the brakes absorb most of the energy");
            } else {
                assertTrue(r.tireSlipEnergyJ() > 0.5 * initial, "locked tires skid away most of the energy");
            }
        }
    }

    // ------------------------------------------------------------------ the slip curve

    @Test
    void slipCurveStartsAtZeroPeaksAtPeakMuAndFallsWhenTheWheelLocks() {
        for (SurfaceKind kind : SurfaceKind.values()) {
            TireSpec.SlipCurve c = TireSpec.forSurface(0.9, kind).curve();

            assertEquals(0.0, c.mu(0), 1e-9);
            assertEquals(0.9, c.mu(c.peakSlip()), 1e-3);
            assertTrue(c.peakSlip() > 0.05 && c.peakSlip() < 0.4, kind + " peak slip " + c.peakSlip());
            assertTrue(c.slidingMu() <= c.mu(c.peakSlip()), kind + " sliding friction above peak");
            for (double k = 0.0; k < c.peakSlip() - 0.01; k += 0.01) {
                assertTrue(c.mu(k + 0.01) > c.mu(k), kind + " friction should rise up to the peak, slip " + k);
            }
        }
    }

    @Test
    void lockingTheWheelsCostsMostOnDryAndWetRoads() {
        TireSpec.SlipCurve dry = TireSpec.forSurface(1.0, SurfaceKind.DRY).curve();
        TireSpec.SlipCurve wet = TireSpec.forSurface(1.0, SurfaceKind.WET).curve();

        assertTrue(dry.slidingMu() < 0.95, "dry sliding friction " + dry.slidingMu());
        assertTrue(wet.slidingMu() < 0.85, "wet sliding friction " + wet.slidingMu());
    }

    @Test
    void roadGripModifierPicksTheSurfaceFamily() {
        assertEquals(SurfaceKind.DRY, SurfaceKind.fromGripModifier(1.0));
        assertEquals(SurfaceKind.WET, SurfaceKind.fromGripModifier(0.7));
        assertEquals(SurfaceKind.WET, SurfaceKind.fromGripModifier(0.6));
        assertEquals(SurfaceKind.SNOW, SurfaceKind.fromGripModifier(0.4));
        assertEquals(SurfaceKind.ICE, SurfaceKind.fromGripModifier(0.2));
    }

    // ------------------------------------------------------------------ locked wheels and ABS

    @Test
    void lockedWheelsDecelerateAtTheSlidingFrictionTimesG() {
        Scenario s = dry().withAbs(false).withBrakes(new BrakeSpec(2.0, 0.70, 0.0));
        double sliding = s.tires().curve().slidingMu();

        double decel = steadyDecel(engine.simulate(s, fineTrace()), V);

        assertEquals(sliding * G, decel, 0.02 * sliding * G);
    }

    @Test
    void slopeUsesSineAndCosineWhenSliding() {
        double slopePercent = 8;
        double theta = Math.atan(slopePercent / 100);
        Scenario s = dry().withAbs(false).withBrakes(new BrakeSpec(2.0, 0.70, 0.0)).withSlope(slopePercent);
        double sliding = s.tires().curve().slidingMu();

        double decel = steadyDecel(engine.simulate(s, fineTrace()), V);

        double expected = sliding * G * Math.cos(theta) - G * Math.sin(theta);
        assertEquals(expected, decel, 0.02 * G);
    }

    @Test
    void absStopsShorterThanLockedWheelsOnDryAndMuchShorterOnWet() {
        // Compare braking distance only: the reaction distance is the same either way.
        double dryAbs = engine.simulate(dry()).brakingDistanceM();
        double dryLocked = engine.simulate(dry().withAbs(false)).brakingDistanceM();
        Scenario wet = gripLimited(0.8, SurfaceKind.WET);
        double wetAbs = engine.simulate(wet).brakingDistanceM();
        double wetLocked = engine.simulate(wet.withAbs(false)).brakingDistanceM();

        assertTrue(dryAbs < 0.95 * dryLocked, "dry: ABS " + dryAbs + " m, locked " + dryLocked + " m");
        assertTrue(wetAbs < 0.90 * wetLocked, "wet: ABS " + wetAbs + " m, locked " + wetLocked + " m");
    }

    @Test
    void absKeepsTheTiresCloseToTheirPeakOnEverySurface() {
        Object[][] surfaces = {
                {1.1, SurfaceKind.DRY}, {0.8, SurfaceKind.WET}, {0.4, SurfaceKind.SNOW}, {0.2, SurfaceKind.ICE}};
        for (Object[] row : surfaces) {
            double mu = (Double) row[0];
            Scenario s = gripLimited(mu, (SurfaceKind) row[1]);

            double ratio = steadyDecel(engine.simulate(s, fineTrace()), V) / (mu * G);

            assertTrue(ratio > 0.88 && ratio < 1.01, row[1] + ": ABS reached " + ratio + " of the best possible");
        }
    }

    @Test
    void absPulsesAtARealisticRateAndNeverLetsTheWheelsRunAway() {
        SimulationResult r = engine.simulate(dry(), fineTrace());

        int cycles = 0;
        boolean rising = false;
        double previous = 1, first = -1, last = 0, maxSlip = 0;
        for (TracePoint p : r.trace()) {
            if (p.speedMs() > 0.8 * V || p.speedMs() < 0.2 * V) {
                previous = p.frontPressure();
                continue;
            }
            if (first < 0) first = p.timeS();
            last = p.timeS();
            maxSlip = Math.max(maxSlip, p.frontSlip());
            double d = p.frontPressure() - previous;
            if (d > 1e-4) rising = true;
            if (d < -1e-4 && rising) {
                cycles++;
                rising = false;
            }
            previous = p.frontPressure();
        }
        double hz = cycles / (last - first);

        assertTrue(hz > 5 && hz < 25, "ABS pulsed at " + hz + " Hz");
        assertTrue(maxSlip < 0.6, "front slip reached " + maxSlip);
    }

    @Test
    void absStaysOutOfTheWayWhenTheBrakesAreTheLimit() {
        Scenario realisticPedal = ideal().withBrakes(new BrakeSpec(0.5, 0.70, 0.15));
        SimulationResult withAbs = engine.simulate(realisticPedal.withAbs(true), fineTrace());
        SimulationResult without = engine.simulate(realisticPedal, fineTrace());

        assertEquals(without.requireStopDistanceM(), withAbs.requireStopDistanceM(), 0.02);
        // The controller never had a reason to release pressure while the car was braking above 1.5 m/s.
        for (TracePoint p : withAbs.trace()) {
            if (p.pedal() > 0.99 && p.speedMs() > 1.5) {
                assertTrue(p.frontPressure() > 0.99 && p.rearPressure() > 0.99,
                        "ABS released pressure at t=" + p.timeS());
            }
        }
    }

    // ------------------------------------------------------------------ properties of real braking

    @Test
    void whenWheelsAreLockedMassDoesNotChangeBrakingDistance() {
        Scenario base = dry().withAbs(false).withBrakes(new BrakeSpec(2.0, 0.70, 0.0));
        double light = engine.simulate(base.withVehicle(VehicleSpec.estimate(1000).withoutResistances()))
                .requireStopDistanceM();
        double heavy = engine.simulate(base.withVehicle(VehicleSpec.estimate(2500).withoutResistances()))
                .requireStopDistanceM();

        assertEquals(light, heavy, 0.05);
    }

    @Test
    void whenWheelsAreLockedCgHeightDoesNotChangeBrakingDistance() {
        Scenario base = dry().withAbs(false).withBrakes(new BrakeSpec(2.0, 0.70, 0.0));
        VehicleSpec car = VehicleSpec.estimate(1500).withoutResistances();
        double low = engine.simulate(base.withVehicle(car.withCgHeight(0.40))).requireStopDistanceM();
        double high = engine.simulate(base.withVehicle(car.withCgHeight(0.75))).requireStopDistanceM();

        assertEquals(low, high, 0.1);
    }

    @Test
    void downhillIsLongerThanFlatAndUphillIsShorter() {
        Scenario base = dry();
        double uphill = engine.simulate(base.withSlope(-8)).requireStopDistanceM();
        double flat = engine.simulate(base).requireStopDistanceM();
        double downhill = engine.simulate(base.withSlope(8)).requireStopDistanceM();

        assertTrue(uphill < flat, "uphill " + uphill + " vs flat " + flat);
        assertTrue(flat < downhill, "flat " + flat + " vs downhill " + downhill);
    }

    @Test
    void carThatCannotHoldItselfOnTheSlopeIsReportedAsNotStopped() {
        Scenario ice = gripLimited(0.1, SurfaceKind.ICE).withSlope(30);
        SimulationResult r = engine.simulate(ice);

        assertFalse(r.stopped());
        assertNull(r.stopDistanceM());
        assertNull(r.stopTimeS());
        assertNull(r.brakingDistanceM());
        assertTrue(r.distanceTravelledM() > 0);
    }

    @Test
    void carsOnIceComeToRestInsteadOfCreepingForever() {
        for (boolean abs : new boolean[]{true, false}) {
            SimulationResult r = engine.simulate(gripLimited(0.2, SurfaceKind.ICE).withAbs(abs));
            assertTrue(r.stopped(), "ice, abs=" + abs);
            assertTrue(r.stopTimeS() < 30, "stop took " + r.stopTimeS() + " s");
        }
    }

    @Test
    void dragAndRollingResistanceShortenAHighSpeedStop() {
        Scenario withResistances = dry().withSpeedMs(Scenario.kmhToMs(200))
                .withVehicle(VehicleSpec.estimate(1500));
        Scenario without = withResistances.withVehicle(VehicleSpec.estimate(1500).withoutResistances());

        assertTrue(engine.simulate(withResistances).requireStopDistanceM()
                < engine.simulate(without).requireStopDistanceM());
    }

    @Test
    void hardBrakingMovesLoadForwardAndKeepsTheTotal() {
        SimulationResult r = engine.simulate(dry().withAbs(false));
        VehicleSpec v = VehicleSpec.estimate(1500);
        double weight = v.massKg() * G;
        double frontStatic = weight * v.frontWeightFraction();

        TracePoint mid = r.trace().get(r.trace().size() / 2);
        assertTrue(mid.pedal() > 0.99);
        assertTrue(mid.frontLoadN() > frontStatic, "front axle should carry more than at rest");
        assertTrue(mid.rearLoadN() < weight - frontStatic, "rear axle should carry less than at rest");
        assertEquals(weight, mid.frontLoadN() + mid.rearLoadN(), 1e-3 * weight);
    }

    @Test
    void whichAxleLocksFirstDependsOnBrakeBias() {
        Scenario frontHeavyBias = dry().withAbs(false).withBrakes(new BrakeSpec(0.9, 0.95, 0.15));
        Scenario rearHeavyBias = dry().withAbs(false).withBrakes(new BrakeSpec(0.9, 0.45, 0.15));

        assertTrue(lockedAnywhere(engine.simulate(frontHeavyBias), true));
        assertFalse(lockedAnywhere(engine.simulate(frontHeavyBias), false));
        assertTrue(lockedAnywhere(engine.simulate(rearHeavyBias), false));
        assertFalse(lockedAnywhere(engine.simulate(rearHeavyBias), true));
    }

    private static boolean lockedAnywhere(SimulationResult r, boolean front) {
        return r.trace().stream().anyMatch(p -> front ? p.frontLocked() : p.rearLocked());
    }

    // ------------------------------------------------------------------ reaction, obstacles, trace

    @Test
    void reactionDistanceIsSpeedTimesDelayWhenNothingSlowsTheCar() {
        assertEquals(V * 0.7, engine.simulate(ideal()).reactionDistanceM(), 1e-6);
    }

    @Test
    void obstacleInsideTheReactionPhaseIsHitAtFullSpeed() {
        SimulationResult r = engine.simulate(ideal().withObstacle(10.0));
        assertEquals(100.0, r.impactSpeedKmh(), 1e-6);
    }

    @Test
    void obstacleInsideTheBrakingPhaseIsHitSlowerThanStartSpeed() {
        SimulationResult r = engine.simulate(ideal().withObstacle(40.0));
        assertTrue(r.impactSpeedKmh() > 0 && r.impactSpeedKmh() < 100, "impact " + r.impactSpeedKmh());
    }

    @Test
    void obstacleBeyondTheStoppingPointIsNotHit() {
        SimulationResult r = engine.simulate(ideal().withObstacle(500.0));
        assertEquals(0.0, r.impactSpeedKmh(), 1e-9);
    }

    @Test
    void noObstacleMeansNoImpactSpeed() {
        assertNull(engine.simulate(ideal()).impactSpeedKmh());
    }

    @Test
    void traceStartsAtTheInitialSpeedEndsAtRestAndNeverGoesBackwards() {
        List<TracePoint> trace = engine.simulate(dry()).trace();

        assertEquals(0.0, trace.get(0).timeS(), 0);
        assertEquals(V, trace.get(0).speedMs(), 1e-9);
        assertEquals(0.0, trace.get(trace.size() - 1).speedMs(), 1e-9);
        for (int i = 1; i < trace.size(); i++) {
            assertTrue(trace.get(i).timeS() >= trace.get(i - 1).timeS());
            assertTrue(trace.get(i).positionM() >= trace.get(i - 1).positionM());
            assertTrue(trace.get(i).speedMs() <= trace.get(i - 1).speedMs() + 1e-9);
        }
    }

    // ------------------------------------------------------------------ numerics

    @Test
    void lockedWheelResultDoesNotDependOnTheStepSize() {
        Scenario s = dry().withAbs(false);

        double fine = engine.simulate(s, EngineConfig.defaults().withDt(0.0005)).requireStopDistanceM();
        double normal = engine.simulate(s).requireStopDistanceM();
        double coarse = engine.simulate(s, EngineConfig.defaults().withDt(0.005)).requireStopDistanceM();

        assertEquals(fine, normal, 0.05);
        assertEquals(fine, coarse, 0.3);
    }

    @Test
    void absResultDependsOnlyWeaklyOnTheStepSize() {
        Scenario s = dry();

        double fine = engine.simulate(s, EngineConfig.defaults().withDt(0.0005)).requireStopDistanceM();
        double normal = engine.simulate(s).requireStopDistanceM();

        assertEquals(fine, normal, 0.01 * fine);
    }

    @Test
    void sameScenarioGivesTheSameResult() {
        Scenario s = ScenarioFactory.fromTableValues(1400, 1.1, 0.7, 0.85, 4, -2, true, 0.9, 90, 60.0);
        assertEquals(engine.simulate(s), engine.simulate(s));
    }

    // ------------------------------------------------------------------ realism of the table-driven setup

    @Test
    void hundredToZeroOnDryAsphaltWithGoodTiresLandsInTheRangeTyreTestsReport() {
        // Published 100-0 km/h tests for good road cars on dry asphalt are roughly 34-40 m.
        Scenario s = ScenarioFactory.fromTableValues(1400, 1.1, 1.0, 1.0, 8, 0, true, 0, 100, null);
        double braking = engine.simulate(s).requireStopDistanceM();
        assertTrue(braking > 33 && braking < 42, "100-0 km/h took " + braking + " m");
    }

    @Test
    void wetRoadNeedsClearlyMoreDistanceThanDry() {
        Scenario dryRoad = ScenarioFactory.fromTableValues(1400, 1.1, 1.0, 1.0, 8, 0, true, 0, 100, null);
        Scenario wetRoad = ScenarioFactory.fromTableValues(1400, 1.1, 0.7, 1.0, 8, 0, true, 0, 100, null);

        assertTrue(engine.simulate(wetRoad).requireStopDistanceM()
                > 1.3 * engine.simulate(dryRoad).requireStopDistanceM());
    }

    @Test
    void tableRowsPickTheMatchingSlipCurve() {
        Scenario wet = ScenarioFactory.fromTableValues(1400, 1.1, 0.7, 1.0, 8, 0, true, 1.3, 100, null);
        Scenario snow = ScenarioFactory.fromTableValues(1400, 1.1, 0.4, 1.0, 8, 0, true, 1.3, 100, null);

        assertEquals(SurfaceKind.WET.stiffness, wet.tires().slipB(), 0);
        assertEquals(SurfaceKind.SNOW.stiffness, snow.tires().slipB(), 0);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new VehicleSpec(-1, 2.7, 0.5, 0.5, 0.3, 2, 0.01, 0.32, 2.4));
        assertThrows(IllegalArgumentException.class, () -> new BrakeSpec(1, 1.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new TireSpec(1.0, 0.1, 10, 0.9, 1));
        assertThrows(IllegalArgumentException.class, () -> new AbsSettings(10, 0.3, 0.4, 1, 1, 1, 0.005));
        assertThrows(IllegalArgumentException.class, () -> ideal().withObstacle(-5.0));
    }
}

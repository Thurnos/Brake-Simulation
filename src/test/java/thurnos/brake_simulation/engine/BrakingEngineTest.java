package thurnos.brake_simulation.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation of the time-stepped engine. Where possible the expected value comes from a closed-form
 * result, so a failure points at the engine and not at a made-up reference number.
 */
class BrakingEngineTest {

    private static final double G = 9.81;
    private static final double V = Scenario.kmhToMs(100);

    private final BrakingEngine engine = new BrakingEngine();

    /** Everything idealised: no drag, no rolling resistance, no ramp, grip far above what the brakes need. */
    private static Scenario ideal() {
        return new Scenario(
                VehicleSpec.estimate(1500).withoutResistances(),
                new BrakeSpec(0.5, 0.70, 0.0),
                new TireSpec(1.5, 0.0, 0.8),
                new DriverSpec(0.5, 0.2),
                V, 0, true, null);
    }

    /** Brakes far stronger than the tires: the tires decide. No ramp so the limit is reached at once. */
    private static Scenario gripLimited(double mu) {
        return new Scenario(
                VehicleSpec.estimate(1500).withoutResistances(),
                new BrakeSpec(2.0, 0.70, 0.0),
                new TireSpec(mu, 0.0, 0.8),
                new DriverSpec(0.5, 0.2),
                V, 0, true, null);
    }

    // ------------------------------------------------------------------ closed-form checks

    @Test
    void constantDecelerationMatchesVSquaredOverTwoA() {
        SimulationResult r = engine.simulate(ideal());

        double a = 0.5 * G;
        double reaction = V * 0.7;
        double braking = V * V / (2 * a);

        assertTrue(r.stopped());
        assertEquals(reaction, r.reactionDistanceM(), 1e-6);
        assertEquals(braking, r.brakingDistanceM(), 1e-4);
        assertEquals(reaction + braking, r.requireStopDistanceM(), 1e-4);
        assertEquals(0.7 + V / a, r.stopTimeS(), 1e-4);
        assertEquals(a, r.peakDecelerationMs2(), 1e-6);
    }

    @Test
    void pedalRampAddsTheDistanceTheMathPredicts() {
        double rise = 0.3;
        double a = 0.5 * G;
        Scenario ramped = ideal().withBrakes(new BrakeSpec(0.5, 0.70, rise));

        double instant = engine.simulate(ideal()).requireStopDistanceM();
        double withRamp = engine.simulate(ramped).requireStopDistanceM();

        // Linear force ramp over T costs v*T/2 - a*T^2/24 extra metres.
        double expectedExtra = V * rise / 2 - a * rise * rise / 24;
        assertEquals(expectedExtra, withRamp - instant, 0.002);
    }

    @Test
    void gripLimitedWithAbsGivesAbsEfficiencyTimesMuTimesG() {
        double mu = 1.0;
        SimulationResult r = engine.simulate(gripLimited(mu));

        double a = EngineConfig.defaults().absEfficiency() * mu * G;
        assertEquals(a, r.peakDecelerationMs2(), 0.01);
        assertEquals(V * V / (2 * a), r.brakingDistanceM(), 0.05);
    }

    @Test
    void lockedWheelsGiveSlidingFrictionTimesG() {
        double mu = 1.0;
        SimulationResult r = engine.simulate(gripLimited(mu).withAbs(false));

        assertEquals(0.8 * mu * G, r.peakDecelerationMs2(), 0.01);
    }

    @Test
    void slopeUsesSineAndCosine() {
        double mu = 1.0;
        double slopePercent = 8;
        double theta = Math.atan(slopePercent / 100);
        SimulationResult downhill = engine.simulate(gripLimited(mu).withSlope(slopePercent));

        double expected = EngineConfig.defaults().absEfficiency() * mu * G * Math.cos(theta) - G * Math.sin(theta);
        assertEquals(expected, downhill.peakDecelerationMs2(), 0.02);
    }

    // ------------------------------------------------------------------ properties of real braking

    @Test
    void whenTiresAreTheLimitMassDoesNotChangeBrakingDistance() {
        double light = engine.simulate(gripLimited(1.0).withVehicle(
                VehicleSpec.estimate(1000).withoutResistances())).requireStopDistanceM();
        double heavy = engine.simulate(gripLimited(1.0).withVehicle(
                VehicleSpec.estimate(2500).withoutResistances())).requireStopDistanceM();

        assertEquals(light, heavy, 1e-3);
    }

    @Test
    void whenBothAxlesAreAtTheLimitCgHeightDoesNotChangeBrakingDistance() {
        VehicleSpec base = VehicleSpec.estimate(1500).withoutResistances();
        double low = engine.simulate(gripLimited(1.0).withVehicle(base.withCgHeight(0.40))).requireStopDistanceM();
        double high = engine.simulate(gripLimited(1.0).withVehicle(base.withCgHeight(0.75))).requireStopDistanceM();

        assertEquals(low, high, 1e-3);
    }

    @Test
    void absStopsShorterThanLockedWheelsWhenBrakingHarderThanGripAllows() {
        double withAbs = engine.simulate(gripLimited(0.7)).requireStopDistanceM();
        double locked = engine.simulate(gripLimited(0.7).withAbs(false)).requireStopDistanceM();

        assertTrue(withAbs < locked, "ABS " + withAbs + " m should beat locked wheels " + locked + " m");
    }

    @Test
    void absMakesNoDifferenceWhenBrakesAreTheLimit() {
        double withAbs = engine.simulate(ideal()).requireStopDistanceM();
        double without = engine.simulate(ideal().withAbs(false)).requireStopDistanceM();

        assertEquals(withAbs, without, 1e-6);
    }

    @Test
    void downhillIsLongerThanFlatAndUphillIsShorter() {
        Scenario base = gripLimited(1.0);
        double uphill = engine.simulate(base.withSlope(-8)).requireStopDistanceM();
        double flat = engine.simulate(base).requireStopDistanceM();
        double downhill = engine.simulate(base.withSlope(8)).requireStopDistanceM();

        assertTrue(uphill < flat, "uphill " + uphill + " vs flat " + flat);
        assertTrue(flat < downhill, "flat " + flat + " vs downhill " + downhill);
    }

    @Test
    void carThatCannotHoldItselfOnTheSlopeIsReportedAsNotStopped() {
        Scenario ice = gripLimited(0.1).withSlope(30);
        SimulationResult r = engine.simulate(ice);

        assertFalse(r.stopped());
        assertNull(r.stopDistanceM());
        assertNull(r.stopTimeS());
        assertNull(r.brakingDistanceM());
        assertTrue(r.distanceTravelledM() > 0);
    }

    @Test
    void dragAndRollingResistanceShortenAHighSpeedStop() {
        Scenario withResistances = gripLimited(1.0).withSpeedMs(Scenario.kmhToMs(200))
                .withVehicle(VehicleSpec.estimate(1500));
        Scenario without = withResistances.withVehicle(VehicleSpec.estimate(1500).withoutResistances());

        assertTrue(engine.simulate(withResistances).requireStopDistanceM()
                < engine.simulate(without).requireStopDistanceM());
    }

    @Test
    void hardBrakingMovesLoadForwardAndKeepsTheTotal() {
        SimulationResult r = engine.simulate(gripLimited(1.0));
        VehicleSpec v = VehicleSpec.estimate(1500);
        double weight = v.massKg() * G;
        double frontStatic = weight * v.frontWeightFraction();

        TracePoint mid = r.trace().get(r.trace().size() / 2 + 20);
        assertTrue(mid.pedal() > 0.99);
        assertTrue(mid.frontLoadN() > frontStatic, "front axle should carry more than at rest");
        assertTrue(mid.rearLoadN() < weight - frontStatic, "rear axle should carry less than at rest");
        assertEquals(weight, mid.frontLoadN() + mid.rearLoadN(), 1e-3);
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
        List<TracePoint> trace = engine.simulate(ideal()).trace();

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
    void resultDoesNotDependOnTheStepSize() {
        Scenario s = ScenarioFactory.fromTableValues(1500, 1.0, 1.0, 0.9, 6, 3, true, 1.3, 120, null);

        double fine = engine.simulate(s, EngineConfig.defaults().withDt(0.0005)).requireStopDistanceM();
        double normal = engine.simulate(s).requireStopDistanceM();
        double coarse = engine.simulate(s, EngineConfig.defaults().withDt(0.005)).requireStopDistanceM();

        assertEquals(fine, normal, 0.02);
        assertEquals(fine, coarse, 0.15);
    }

    @Test
    void sameScenarioGivesTheSameResult() {
        Scenario s = ScenarioFactory.fromTableValues(1400, 1.1, 0.7, 0.85, 4, -2, false, 0.9, 90, 60.0);
        assertEquals(engine.simulate(s), engine.simulate(s));
    }

    // ------------------------------------------------------------------ realism of the table-driven setup

    @Test
    void hundredToZeroOnDryAsphaltWithGoodTiresLandsInTheRangeTyreTestsReport() {
        // Published 100-0 km/h tests for good road cars on dry asphalt are roughly 34-40 m.
        Scenario s = ScenarioFactory.fromTableValues(1400, 1.1, 1.0, 1.0, 8, 0, true, 0, 100, null);
        double braking = engine.simulate(s).requireStopDistanceM() - 0;
        assertTrue(braking > 33 && braking < 42, "100-0 km/h took " + braking + " m");
    }

    @Test
    void wetRoadNeedsClearlyMoreDistanceThanDry() {
        Scenario dry = ScenarioFactory.fromTableValues(1400, 1.1, 1.0, 1.0, 8, 0, true, 0, 100, null);
        Scenario wet = ScenarioFactory.fromTableValues(1400, 1.1, 0.7, 1.0, 8, 0, true, 0, 100, null);

        assertTrue(engine.simulate(wet).requireStopDistanceM() > 1.3 * engine.simulate(dry).requireStopDistanceM());
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new VehicleSpec(-1, 2.7, 0.5, 0.5, 0.3, 2, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BrakeSpec(1, 1.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> ideal().withObstacle(-5.0));
    }
}

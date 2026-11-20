package thurnos.brake_simulation.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrakeThermalTest {

    private static final BrakingEngine ENGINE = new BrakingEngine();

    private static Scenario scenario(double efficiency, double speedKmh, Double startC) {
        return ScenarioFactory.fromTableValues(1400, 1.1, 1.0, efficiency, 8, 0, true, 1.3, speedKmh, 0.0,
                startC, null);
    }

    private static double brakingDistance(double efficiency, double startC) {
        return ENGINE.simulate(scenario(efficiency, 100, startC)).brakingDistanceM();
    }

    @Test
    void fadeFactorShape() {
        BrakeThermal t = BrakeThermal.estimate(1400);
        assertEquals(1.0, t.fadeFactor(-20), 0);
        assertEquals(1.0, t.fadeFactor(t.fadeStartC()), 1e-12);
        assertEquals(t.fadeFloor(), t.fadeFactor(t.fadeFullC()), 1e-12);
        assertEquals(t.fadeFloor(), t.fadeFactor(1100), 1e-12);
        double prev = 2;
        for (int c = 0; c <= 1000; c += 10) {
            double f = t.fadeFactor(c);
            assertTrue(f <= prev + 1e-12, "fade factor must not rise with temperature");
            prev = f;
        }
    }

    @Test
    void coolingFollowsTheExactExponential() {
        BrakeThermal t = BrakeThermal.estimate(1400);
        double[] c = t.cooled(600, 400, 60, 15);
        double fTau = t.frontHeatCapacityJK() / BrakeThermal.coolingWK(t.frontRotorKg(), 15);
        assertEquals(20 + 580 * Math.exp(-60 / fTau), c[0], 1e-9);
        assertTrue(c[1] > 20 && c[1] < 400);
        double[] faster = t.cooled(600, 400, 60, 30);
        assertTrue(faster[0] < c[0], "more airflow cools more");
        double[] same = t.cooled(600, 400, 0, 15);
        assertEquals(600, same[0], 1e-12);
    }

    @Test
    void temperatureRiseMatchesBrakeEnergy() {
        Scenario s = scenario(1.0, 100, null);
        SimulationResult r = ENGINE.simulate(s).withoutTrace();
        BrakeThermal t = s.brakes().thermal();
        SimulationResult.ThermalSummary th = r.thermal();
        double stored = (th.finalFrontC() - 20) * t.frontHeatCapacityJK()
                + (th.finalRearC() - 20) * t.rearHeatCapacityJK();
        double expected = t.heatToRotor() * r.brakeEnergyJ();
        // some heat has already gone to the air during the stop
        assertTrue(stored <= expected * 1.001);
        assertTrue(stored > expected * 0.97, "stored " + stored + " expected " + expected);
    }

    @Test
    void frontAxleRunsHotterBecauseItDoesMostOfTheBraking() {
        SimulationResult.ThermalSummary th = ENGINE.simulate(scenario(1.0, 120, null)).thermal();
        assertTrue(th.peakFrontC() > th.peakRearC() * 1.3);
        assertTrue(th.peakFrontC() > 20);
    }

    @Test
    void switchedOffModelHasNoThermalResultAndNoFade() {
        Scenario s = scenario(1.0, 100, null);
        Scenario off = s.withBrakes(s.brakes().withThermal(BrakeThermal.NONE));
        SimulationResult r = ENGINE.simulate(off);
        assertNull(r.thermal());
        // a single normal stop does not heat the brakes enough to fade, so on/off agree
        assertEquals(ENGINE.simulate(s).requireStopDistanceM(), r.requireStopDistanceM(), 1e-9);
    }

    @Test
    void oneNormalStopDoesNotFade() {
        SimulationResult.ThermalSummary th = ENGINE.simulate(scenario(0.8, 160, null)).thermal();
        assertEquals(1.0, th.minFadeFactor(), 1e-12);
    }

    @Test
    void hotBrakesLengthenTheStopWhenTheBrakesAreTheLimit() {
        double cold = brakingDistance(0.65, 20);
        double hot = brakingDistance(0.65, 650);
        assertTrue(hot > cold * 1.3, "hot " + hot + " cold " + cold);
    }

    @Test
    void strongBrakesHideModerateFadeBecauseTheTiresAreTheLimit() {
        // 1.3 x 1.0 = 1.3 g of brake force against about 1.1 g of tire grip: fade to 0.9 changes nothing
        double cold = brakingDistance(1.0, 20);
        double warm = brakingDistance(1.0, 400);
        assertEquals(cold, warm, 0.2);
        // but severe fade eventually shows even on strong brakes
        assertTrue(brakingDistance(1.0, 750) > cold * 1.3);
    }

    @Test
    void repeatedStopsGetLongerAndHotter() {
        Scenario s = scenario(0.8, 120, null);
        List<SimulationResult> runs = BrakeSequence.repeatedStops(ENGINE, s, 8, 15, 15);
        assertEquals(8, runs.size());
        for (int i = 1; i < runs.size(); i++) {
            assertTrue(runs.get(i).thermal().peakFrontC() > runs.get(i - 1).thermal().peakFrontC());
            assertTrue(runs.get(i).brakingDistanceM() >= runs.get(i - 1).brakingDistanceM() - 1e-9);
        }
        assertEquals(runs.get(0).brakingDistanceM(), runs.get(1).brakingDistanceM(), 0.1);
        assertTrue(runs.get(7).brakingDistanceM() > runs.get(0).brakingDistanceM() * 1.1);
        assertTrue(runs.get(7).thermal().minFadeFactor() < 0.8);
    }

    @Test
    void longerRestMeansCoolerBrakesAndShorterStops() {
        Scenario s = scenario(0.8, 120, null);
        List<SimulationResult> quick = BrakeSequence.repeatedStops(ENGINE, s, 8, 5, 15);
        List<SimulationResult> slow = BrakeSequence.repeatedStops(ENGINE, s, 8, 120, 15);
        assertTrue(quick.get(7).thermal().peakFrontC() > slow.get(7).thermal().peakFrontC());
        assertTrue(quick.get(7).brakingDistanceM() > slow.get(7).brakingDistanceM());
    }

    @Test
    void energyBalanceIsNotAffectedByTheHeatModel() {
        Scenario base = scenario(1.0, 120, 300.0);
        Scenario s = base.withVehicle(base.vehicle().withoutResistances());
        SimulationResult r = ENGINE.simulate(s).withoutTrace();
        VehicleSpec v = s.vehicle();
        double v0 = s.initialSpeedMs();
        double ke = 0.5 * v.massKg() * v0 * v0 + 2 * 0.5 * v.axleInertiaKgM2() * Math.pow(v0 / v.wheelRadiusM(), 2);
        assertEquals(1.0, (r.brakeEnergyJ() + r.tireSlipEnergyJ()) / ke, 0.02);
    }

    @Test
    void traceCarriesTemperatureAndFade() {
        SimulationResult r = ENGINE.simulate(scenario(0.8, 120, 500.0));
        TracePoint first = r.trace().get(0);
        TracePoint last = r.trace().get(r.trace().size() - 1);
        assertEquals(500.0, first.frontBrakeTempC(), 1.0);
        assertTrue(last.frontBrakeTempC() > first.frontBrakeTempC());
        assertTrue(first.frontFadeFactor() < 1.0);
    }

    @Test
    void invalidInputsAreRejected() {
        BrakeThermal t = BrakeThermal.estimate(1400);
        assertThrows(IllegalArgumentException.class, () -> t.withInitialTemps(2000, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new BrakeThermal(5, 3, 20, 20, 20, 0.9, 700, 600, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> BrakeSequence.repeatedStops(ENGINE, scenario(1.0, 100, null), 0, 10, 10));
        Scenario off = scenario(1.0, 100, null);
        assertThrows(IllegalArgumentException.class, () -> BrakeSequence.repeatedStops(ENGINE,
                off.withBrakes(off.brakes().withThermal(BrakeThermal.NONE)), 2, 10, 10));
    }

    @Test
    void deterministic() {
        assertEquals(brakingDistance(0.8, 500), brakingDistance(0.8, 500), 0.0);
    }
}

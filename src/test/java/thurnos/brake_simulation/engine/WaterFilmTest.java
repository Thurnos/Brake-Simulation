package thurnos.brake_simulation.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WaterFilmTest {

    private static final BrakingEngine ENGINE = new BrakingEngine();

    private static Scenario scenario(double speedKmh, double waterMm, double treadMm, boolean abs) {
        return ScenarioFactory.fromTableValues(1400, 1.0, waterMm > 0 ? 0.7 : 1.0, 1.0, treadMm, 0, abs, 0.0,
                speedKmh, waterMm, null);
    }

    private static double brakingDistance(double speedKmh, double waterMm, double treadMm, boolean abs) {
        return ENGINE.simulate(scenario(speedKmh, waterMm, treadMm, abs), EngineConfig.defaults())
                .withoutTrace().requireStopDistanceM();
    }

    @Test
    void onsetMatchesHorneForSmoothTire() {
        // 9*sqrt(32 psi) knots = 50.9 kn = 94.3 km/h at 220.6 kPa
        WaterFilm f = new WaterFilm(10, 0, 220.6);
        assertEquals(94.3, f.onsetSpeedMs() * 3.6, 0.5);
    }

    @Test
    void onsetRisesWithTreadAndPressureAndFallsWithDepth() {
        assertTrue(WaterFilm.of(2, 8).onsetSpeedMs() > WaterFilm.of(2, 3).onsetSpeedMs());
        assertTrue(WaterFilm.of(2, 3).onsetSpeedMs() > WaterFilm.of(2, 1.6).onsetSpeedMs());
        assertTrue(WaterFilm.of(2, 4).onsetSpeedMs() > WaterFilm.of(6, 4).onsetSpeedMs());
        assertTrue(new WaterFilm(2, 4, 260).onsetSpeedMs() > new WaterFilm(2, 4, 180).onsetSpeedMs());
        assertEquals(Double.POSITIVE_INFINITY, WaterFilm.NONE.onsetSpeedMs());
    }

    @Test
    void gripFactorShape() {
        WaterFilm f = WaterFilm.of(3, 2);
        double onset = f.onsetSpeedMs();
        assertEquals(1.0, f.gripFactor(0.2 * onset), 0.01);
        assertEquals(0.55, f.gripFactor(0.85 * onset), 0.01);
        assertEquals(WaterFilm.FRICTION_ON_WATER, f.gripFactor(2 * onset), 0.01);
        double prev = 2;
        for (int i = 0; i <= 40; i++) {
            double g = f.gripFactor(i * onset / 20);
            assertTrue(g <= prev + 1e-12, "grip factor must not rise with speed");
            prev = g;
        }
        assertEquals(1.0, WaterFilm.NONE.gripFactor(80));
    }

    @Test
    void treadOnlyMattersOnWetRoad() {
        assertEquals(1.0, WaterFilm.of(0, 1.6).treadGripFactor(), 1e-12);
        assertEquals(1.0, WaterFilm.of(2, 8).treadGripFactor(), 1e-12);
        assertEquals(WaterFilm.WORN_WET_GRIP, WaterFilm.of(2, 1.6).treadGripFactor(), 1e-12);
        double dryNew = brakingDistance(50, 0, 8, false);
        double dryWorn = brakingDistance(50, 0, 1.6, false);
        assertEquals(dryNew, dryWorn, 1e-9);
        double wetNew = brakingDistance(50, 2, 8, false);
        double wetWorn = brakingDistance(50, 2, 1.6, false);
        assertTrue(wetWorn > wetNew * 1.2, "worn tread should lengthen a wet stop at low speed");
    }

    @Test
    void lowSpeedWetStopIsNotHydroplaning() {
        // well below onset, the water grip factor stays ~1: the stop equals the plain wet-grip stop
        Scenario s = scenario(40, 2, 8, false);
        SimulationResult r = ENGINE.simulate(s, EngineConfig.defaults());
        assertTrue(r.trace().stream().allMatch(p -> p.waterGripFactor() > 0.98));
        Scenario noFilm = s.withTires(s.tires().withWater(WaterFilm.NONE));
        double plain = ENGINE.simulate(noFilm, EngineConfig.defaults()).requireStopDistanceM();
        assertEquals(plain, r.requireStopDistanceM(), 0.05);
    }

    @Test
    void hydroplaningLengthensHighSpeedStopAndWornTiresSufferMore() {
        Scenario worn = scenario(130, 3, 1.6, false);
        Scenario fresh = scenario(130, 3, 8, false);
        double wornD = ENGINE.simulate(worn, EngineConfig.defaults()).requireStopDistanceM();
        double freshD = ENGINE.simulate(fresh, EngineConfig.defaults()).requireStopDistanceM();
        double wornNoFilmD = ENGINE.simulate(worn.withTires(worn.tires().withWater(WaterFilm.NONE)),
                EngineConfig.defaults()).requireStopDistanceM();
        assertTrue(wornD > wornNoFilmD * 1.15, "hydroplaning should clearly lengthen the stop");
        assertTrue(wornD > freshD, "worn tires hydroplane earlier");
    }

    @Test
    void gripIsRegainedAsCarSlowsAndBrakingContinues() {
        SimulationResult r = ENGINE.simulate(scenario(140, 4, 1.6, false), EngineConfig.defaults());
        double minGrip = r.trace().stream().mapToDouble(TracePoint::waterGripFactor).min().orElse(1);
        TracePoint last = r.trace().get(r.trace().size() - 1);
        assertTrue(minGrip < 0.3, "should hydroplane at the start");
        assertTrue(r.trace().get(0).waterGripFactor() < 0.3);
        assertTrue(r.trace().stream().anyMatch(p -> p.waterGripFactor() > 0.95), "grip returns later in the stop");
        assertTrue(r.stopped());
        assertNotNull(last);
    }

    @Test
    void energyStillBalancesWhileHydroplaning() {
        // without air drag and rolling resistance, brakes + tire slip must account for all the kinetic energy
        Scenario base = scenario(140, 4, 1.6, true);
        Scenario s = base.withVehicle(base.vehicle().withoutResistances());
        SimulationResult r = ENGINE.simulate(s, EngineConfig.defaults());
        VehicleSpec v = s.vehicle();
        double v0 = s.initialSpeedMs();
        double ke = 0.5 * v.massKg() * v0 * v0
                + 2 * 0.5 * v.axleInertiaKgM2() * Math.pow(v0 / v.wheelRadiusM(), 2);
        double dissipated = r.brakeEnergyJ() + r.tireSlipEnergyJ();
        assertEquals(1.0, dissipated / ke, 0.02);
        assertTrue(r.requireStopDistanceM() > 200, "this run should really be a long hydroplaning stop");
    }

    @Test
    void resultReportsOnsetOnlyOnWetRoad() {
        assertNull(ENGINE.simulate(scenario(80, 0, 8, true), EngineConfig.defaults()).hydroplaningOnsetKmh());
        Double onset = ENGINE.simulate(scenario(80, 2, 8, true), EngineConfig.defaults())
                .hydroplaningOnsetKmh();
        assertNotNull(onset);
        assertEquals(WaterFilm.of(2, 8).onsetSpeedMs() * 3.6, onset, 1e-9);
    }

    @Test
    void factoryInfersWaterFromRoadFamily() {
        Scenario dry = ScenarioFactory.fromTableValues(1400, 1, 1.0, 1, 1.6, 0, true, 1.0, 100, null);
        Scenario wet = ScenarioFactory.fromTableValues(1400, 1, 0.7, 1, 8, 0, true, 1.0, 100, null);
        Scenario snow = ScenarioFactory.fromTableValues(1400, 1, 0.4, 1, 1.6, 0, true, 1.0, 100, null);
        assertFalse(dry.tires().water().isWet());
        assertEquals(ScenarioFactory.TYPICAL_WET_FILM_MM, wet.tires().water().waterDepthMm(), 1e-12);
        assertFalse(snow.tires().water().isWet());
        // tread no longer costs dry or snow grip
        assertEquals(1.0, dry.tires().peakMu(), 1e-12);
        assertEquals(0.4, snow.tires().peakMu(), 1e-12);
    }

    @Test
    void invalidWaterInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterFilm(-1, 5, 220));
        assertThrows(IllegalArgumentException.class, () -> new WaterFilm(1, -5, 220));
        assertThrows(IllegalArgumentException.class, () -> new WaterFilm(1, 5, 50));
    }

    @Test
    void deterministic() {
        assertEquals(brakingDistance(120, 3, 3, true), brakingDistance(120, 3, 3, true), 0.0);
    }
}

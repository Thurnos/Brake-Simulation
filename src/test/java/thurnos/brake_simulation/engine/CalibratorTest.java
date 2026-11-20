package thurnos.brake_simulation.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CalibratorTest {

    private static final BrakingEngine ENGINE = new BrakingEngine();

    private static Scenario base(double weight, double speedKmh, double roadGrip, double tread) {
        return ScenarioFactory.fromTableValues(weight, 1.0, roadGrip, 1.0, tread, 0, true, 0.0, speedKmh, null);
    }

    /** Pretends the real surface grips {@code trueScale} times the table value and measures stops on it. */
    private static List<Calibrator.Measurement> measure(double trueScale, double roadGrip, double noise, long seed) {
        Random rnd = new Random(seed);
        List<Calibrator.Measurement> ms = new ArrayList<>();
        double[] speeds = {50, 80, 100, 120};
        double[] weights = {1200, 1500, 1800};
        for (double speed : speeds) {
            for (double weight : weights) {
                Scenario s = base(weight, speed, roadGrip, 8);
                Scenario real = s.withTires(s.tires().withPeakMu(s.tires().peakMu() * trueScale));
                double d = ENGINE.simulate(real).withoutTrace().brakingDistanceM();
                ms.add(new Calibrator.Measurement(s, d * (1 + noise * rnd.nextGaussian())));
            }
        }
        return ms;
    }

    @Test
    void recoversTheTrueGripScaleFromCleanMeasurements() {
        for (double truth : new double[]{0.8, 0.9, 1.15, 1.4}) {
            Calibrator.Fit f = Calibrator.fitGripScale(ENGINE, measure(truth, 1.0, 0, 1));
            assertEquals(truth, f.gripScale(), 0.01, "truth " + truth);
            assertTrue(f.rmsRelErrorAfter() < 0.01);
            assertFalse(f.atBound());
        }
    }

    @Test
    void worksOnWetAndIceToo() {
        assertEquals(0.85, Calibrator.fitGripScale(ENGINE, measure(0.85, 0.7, 0, 2)).gripScale(), 0.015);
        assertEquals(1.2, Calibrator.fitGripScale(ENGINE, measure(1.2, 0.2, 0, 3)).gripScale(), 0.02);
    }

    @Test
    void measurementNoiseMovesTheFitOnlyALittle() {
        Calibrator.Fit f = Calibrator.fitGripScale(ENGINE, measure(0.9, 1.0, 0.04, 7));
        assertEquals(0.9, f.gripScale(), 0.04);
        assertTrue(f.rmsRelErrorAfter() < 0.07);
        assertTrue(f.rmsRelErrorAfter() <= f.rmsRelErrorBefore());
    }

    @Test
    void fitDoesNotMakeTheErrorWorseBeyondNumericalJitter() {
        // when the table is already right the fit stays at 1; ABS cycling makes the error curve very slightly
        // bumpy, so allow a 2% relative slack
        Calibrator.Fit f = Calibrator.fitGripScale(ENGINE, measure(1.0, 1.0, 0.03, 11));
        assertTrue(f.rmsRelErrorAfter() <= f.rmsRelErrorBefore() * 1.02 + 1e-4);
        assertEquals(1.0, f.gripScale(), 0.03);
    }

    @Test
    void absurdDataHitsTheSearchLimit() {
        // measured stops far shorter than any tire could give
        List<Calibrator.Measurement> ms = new ArrayList<>();
        for (double speed : new double[]{60, 100}) {
            ms.add(new Calibrator.Measurement(base(1400, speed, 1.0, 8), 3.0));
        }
        assertTrue(Calibrator.fitGripScale(ENGINE, ms).atBound());
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Calibrator.fitGripScale(ENGINE, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Calibrator.Measurement(base(1400, 100, 1.0, 8), 0));
    }

    @Test
    void csvReaderHandlesQuotesNullsAndComments() throws Exception {
        Path f = Files.createTempFile("calib", ".csv");
        Files.writeString(f, "# comment\nid,name,value\n1,\"Golf, GTI\",2.5\n2,Plain,NULL\n\n");
        CsvTable t = CsvTable.read(f);
        assertEquals(2, t.size());
        assertEquals("Golf, GTI", t.text(0, "name"));
        assertEquals(2.5, t.number(0, "value"), 0);
        assertNull(t.number(1, "value"));
        assertNull(t.text(0, "missing"));
        Files.delete(f);
    }
}

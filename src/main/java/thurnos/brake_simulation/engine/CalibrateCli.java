package thurnos.brake_simulation.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command line: fits the engine's grip to measured stops.
 *
 * <pre>
 * java -cp target/classes thurnos.brake_simulation.engine.CalibrateCli measurements.csv
 * </pre>
 *
 * Columns: {@code surface} (dry, wet, snow, ice), {@code weight_kg}, {@code speed_kmh} and
 * {@code measured_braking_m} (without reaction distance) are required. Optional: {@code tread_mm} (8),
 * {@code abs} (1), {@code base_grip} (1.0, the tire's table value), {@code efficiency} (1.0, brake table value).
 * Prints, per surface, the factor to multiply that road's {@code grip_modifier} with.
 */
public final class CalibrateCli {

    private CalibrateCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: CalibrateCli measurements.csv");
            System.exit(2);
        }
        CsvTable t = CsvTable.read(Path.of(args[0]));
        Map<SurfaceKind, List<Calibrator.Measurement>> bySurface = new LinkedHashMap<>();
        Map<SurfaceKind, Double> modifier = Map.of(SurfaceKind.DRY, 1.0, SurfaceKind.WET, 0.7,
                SurfaceKind.SNOW, 0.4, SurfaceKind.ICE, 0.2);
        for (int i = 0; i < t.size(); i++) {
            SurfaceKind kind = SurfaceKind.valueOf(require(t, i, "surface").toUpperCase(Locale.ROOT));
            double grip = modifier.get(kind);
            double tread = or(t.number(i, "tread_mm"), 8.0);
            Scenario s = ScenarioFactory.fromTableValues(
                    require(t.number(i, "weight_kg"), "weight_kg", i),
                    or(t.number(i, "base_grip"), 1.0),
                    grip,
                    or(t.number(i, "efficiency"), 1.0),
                    tread, 0,
                    or(t.number(i, "abs"), 1.0) != 0,
                    0.0,
                    require(t.number(i, "speed_kmh"), "speed_kmh", i),
                    null);
            bySurface.computeIfAbsent(kind, k -> new ArrayList<>())
                    .add(new Calibrator.Measurement(s, require(t.number(i, "measured_braking_m"),
                            "measured_braking_m", i)));
        }
        BrakingEngine engine = new BrakingEngine();
        for (var e : bySurface.entrySet()) {
            Calibrator.Fit f = Calibrator.fitGripScale(engine, e.getValue());
            System.out.printf(Locale.ROOT,
                    "%-5s n=%d  multiply grip_modifier by %.3f (%.3f -> %.3f)  rms error %.1f%% -> %.1f%%%s%n",
                    e.getKey(), f.n(), f.gripScale(), modifier.get(e.getKey()),
                    modifier.get(e.getKey()) * f.gripScale(),
                    100 * f.rmsRelErrorBefore(), 100 * f.rmsRelErrorAfter(),
                    f.atBound() ? "  WARNING: hit the search limit, check the data" : "");
        }
    }

    private static String require(CsvTable t, int row, String column) {
        String v = t.text(row, column);
        if (v == null) throw new IllegalArgumentException("row " + (row + 1) + ": missing " + column);
        return v;
    }

    private static double require(Double v, String column, int row) {
        if (v == null) throw new IllegalArgumentException("row " + (row + 1) + ": missing " + column);
        return v;
    }

    private static double or(Double v, double fallback) {
        return v == null ? fallback : v;
    }
}

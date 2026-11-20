package thurnos.brake_simulation.engine;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Runs the engine over every row of {@code simulations.csv} (joined with cars, tires, brakes and roads) and writes
 * a training table for the surrogate model, so the surrogate learns the engine instead of the old formula.
 *
 * <pre>
 * java -cp target/classes thurnos.brake_simulation.engine.DatasetGenerator . engine_dataset.csv [extra-random-rows=0]
 * </pre>
 *
 * The first argument is the folder holding the CSV files. The optional third argument adds that many random
 * scenarios (random car, tire, brake and road rows; speed 30–160 km/h, tread 1.6–9 mm, slope -10..10%,
 * reaction times and ABS as in the existing rows) so the surrogate has more than the original ~1000 points. Brake start temperature is not in the old data, so it is
 * drawn with a fixed seed: 70% cold (20 °C), the rest uniform between 100 and 700 °C.
 */
public final class DatasetGenerator {

    private DatasetGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: DatasetGenerator <csv-folder> <output.csv>");
            System.exit(2);
        }
        Path dir = Path.of(args[0]);
        Map<Integer, Integer> idx = new HashMap<>();
        CsvTable sim = CsvTable.read(dir.resolve("simulations.csv"));
        CsvTable cars = CsvTable.read(dir.resolve("cars.csv"));
        CsvTable tires = CsvTable.read(dir.resolve("tires.csv"));
        CsvTable brakes = CsvTable.read(dir.resolve("brakes.csv"));
        CsvTable roads = CsvTable.read(dir.resolve("roads.csv"));

        Random rnd = new Random(42);
        BrakingEngine engine = new BrakingEngine();
        int written = 0, skipped = 0;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
            out.println("speed_kmh,tire_tread_mm,slope_percent,reaction_time,has_abs,weight_kg,base_grip,efficiency,"
                    + "road_grip,water_depth_mm,brake_start_temp_c,stopped,total_distance,total_time,braking_distance");
            for (int i = 0; i < sim.size(); i++) {
                Double speed = sim.number(i, "speed_kmh"), tread = sim.number(i, "tire_tread_mm");
                Double slope = sim.number(i, "slope_percent"), reaction = sim.number(i, "reaction_time");
                Double abs = sim.number(i, "has_abs");
                Double weight = lookup(cars, sim.number(i, "car_id"), "weight_kg");
                Double grip = lookup(tires, sim.number(i, "tire_id"), "base_grip");
                Double eff = lookup(brakes, sim.number(i, "brake_id"), "efficiency");
                Double road = lookup(roads, sim.number(i, "road_condition_id"), "grip_modifier");
                Double water = lookup(roads, sim.number(i, "road_condition_id"), "water_depth_mm");
                double temp = rnd.nextDouble() < 0.7 ? 20.0 : 100 + 600 * rnd.nextDouble();
                if (speed == null || tread == null || slope == null || reaction == null || abs == null
                        || weight == null || grip == null || eff == null || road == null) {
                    skipped++;
                    continue;
                }
                Scenario s = ScenarioFactory.fromTableValues(weight, grip, road, eff, tread, slope, abs != 0,
                        reaction, speed, water, temp, null);
                SimulationResult r = engine.simulate(s).withoutTrace();
                out.printf(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f,%d,%.1f,%.3f,%.3f,%.3f,%.2f,%.1f,%d,%s,%s,%s%n",
                        speed, tread, slope, reaction, abs != 0 ? 1 : 0, weight, grip, eff, road,
                        water == null ? ScenarioFactory.defaultWaterDepthMm(road) : water, temp,
                        r.stopped() ? 1 : 0,
                        r.stopped() ? String.format(Locale.ROOT, "%.3f", r.stopDistanceM()) : "",
                        r.stopped() ? String.format(Locale.ROOT, "%.3f", r.stopTimeS()) : "",
                        r.stopped() ? String.format(Locale.ROOT, "%.3f", r.brakingDistanceM()) : "");
                written++;
            }
            int extra = args.length > 2 ? Integer.parseInt(args[2]) : 0;
            double[] reactions = distinct(sim, "reaction_time");
            for (int n = 0; n < extra; n++) {
                int car = rnd.nextInt(cars.size()), tire = rnd.nextInt(tires.size());
                int brake = rnd.nextInt(brakes.size()), road = rnd.nextInt(roads.size());
                double speed = 30 + 130 * rnd.nextDouble();
                double tread = 1.6 + 7.4 * rnd.nextDouble();
                double slope = -10 + 20 * rnd.nextDouble();
                double reaction = reactions[rnd.nextInt(reactions.length)];
                boolean abs = rnd.nextBoolean();
                double temp = rnd.nextDouble() < 0.7 ? 20.0 : 100 + 600 * rnd.nextDouble();
                double weight = cars.number(car, "weight_kg"), grip = tires.number(tire, "base_grip");
                double eff = brakes.number(brake, "efficiency"), roadGrip = roads.number(road, "grip_modifier");
                Double wd = roads.number(road, "water_depth_mm");
                double water = wd == null ? ScenarioFactory.defaultWaterDepthMm(roadGrip) : wd;
                Scenario s = ScenarioFactory.fromTableValues(weight, grip, roadGrip, eff, tread, slope, abs,
                        reaction, speed, water, temp, null);
                SimulationResult r = engine.simulate(s).withoutTrace();
                out.printf(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f,%d,%.1f,%.3f,%.3f,%.3f,%.2f,%.1f,%d,%s,%s,%s%n",
                        speed, tread, slope, reaction, abs ? 1 : 0, weight, grip, eff, roadGrip, water, temp,
                        r.stopped() ? 1 : 0,
                        r.stopped() ? String.format(Locale.ROOT, "%.3f", r.stopDistanceM()) : "",
                        r.stopped() ? String.format(Locale.ROOT, "%.3f", r.stopTimeS()) : "",
                        r.stopped() ? String.format(Locale.ROOT, "%.3f", r.brakingDistanceM()) : "");
                written++;
            }
        }
        System.out.println("wrote " + written + " rows, skipped " + skipped + " incomplete rows");
    }

    private static double[] distinct(CsvTable t, String column) {
        return java.util.stream.IntStream.range(0, t.size()).mapToObj(i -> t.number(i, column))
                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).distinct().sorted().toArray();
    }

    private static Double lookup(CsvTable table, Double id, String column) {
        if (id == null) return null;
        for (int i = 0; i < table.size(); i++) {
            Double v = table.number(i, "id");
            if (v != null && v.doubleValue() == id) return table.number(i, column);
        }
        return null;
    }
}

package thurnos.brake_simulation.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Repeated stops with the brakes carrying their heat from one stop to the next: stop, drive off and cruise
 * for a while (the brakes cool in the airflow), stop again. This is where brake fade shows up.
 */
public final class BrakeSequence {

    private BrakeSequence() {
    }

    /**
     * @param base      the first stop; its brake temperatures at the start are used for stop 1
     * @param stops     number of stops
     * @param restS     time between the end of one stop and the start of the next
     * @param cruiseMs  speed driven during the rest (sets how well the brakes cool)
     * @return one summary result per stop (no trace)
     */
    public static List<SimulationResult> repeatedStops(BrakingEngine engine, Scenario base, int stops,
                                                       double restS, double cruiseMs) {
        VehicleSpec.require(stops >= 1 && stops <= 100, "stops must be between 1 and 100");
        VehicleSpec.require(restS >= 0, "restS must not be negative");
        VehicleSpec.require(cruiseMs >= 0, "cruiseMs must not be negative");
        BrakeThermal thermal = base.brakes().thermal();
        VehicleSpec.require(thermal.enabled(), "repeated stops need the brake thermal model switched on");

        List<SimulationResult> results = new ArrayList<>();
        Scenario current = base;
        for (int i = 0; i < stops; i++) {
            SimulationResult r = engine.simulate(current).withoutTrace();
            results.add(r);
            SimulationResult.ThermalSummary t = r.thermal();
            double[] cooled = thermal.cooled(t.finalFrontC(), t.finalRearC(), restS, cruiseMs);
            current = current.withBrakes(current.brakes()
                    .withThermal(thermal.withInitialTemps(cooled[0], cooled[1])));
        }
        return results;
    }
}

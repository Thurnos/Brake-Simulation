package thurnos.brake_simulation.engine;

/**
 * Bridges the existing data model (cars, tires, brakes, roads, driver condition) to the engine.
 *
 * <p>Takes plain numbers so it can be used and tested without Spring or JPA. The service layer
 * reads the entities and passes their values in.
 */
public final class ScenarioFactory {

    private ScenarioFactory() {
    }

    /**
     * @param weightKg        cars.weight_kg
     * @param tireBaseGrip    tires.base_grip
     * @param roadModifier    roads.grip_modifier
     * @param brakeEfficiency brakes.efficiency
     * @param treadDepthMm    remaining tread depth
     * @param slopePercent    positive = downhill
     * @param hasAbs          ABS fitted and working
     * @param reactionTimeS   total driver delay (DriverCondition.getReactionTime())
     * @param speedKmh        speed when the hazard appears
     * @param obstacleM       distance to an obstacle, or null
     */
    public static Scenario fromTableValues(double weightKg,
                                           double tireBaseGrip,
                                           double roadModifier,
                                           double brakeEfficiency,
                                           double treadDepthMm,
                                           double slopePercent,
                                           boolean hasAbs,
                                           double reactionTimeS,
                                           double speedKmh,
                                           Double obstacleM) {
        double peakMu = tireBaseGrip * roadModifier * treadFactor(treadDepthMm);
        return new Scenario(
                VehicleSpec.estimate(weightKg),
                BrakeSpec.fromEfficiency(brakeEfficiency),
                TireSpec.forSurface(peakMu, SurfaceKind.fromGripModifier(roadModifier)),
                DriverSpec.fromReactionTime(reactionTimeS),
                Scenario.kmhToMs(speedKmh),
                slopePercent,
                hasAbs,
                obstacleM);
    }

    /**
     * Smooth stand-in for the old stepped wear table: 1.0 at 7 mm and above, falling to 0.5 at 1.6 mm
     * (the legal limit in most countries). Phase 3 replaces this with a wet/dry-aware model, because
     * tread depth matters mostly on wet roads.
     */
    static double treadFactor(double treadDepthMm) {
        double fraction = Math.min(1, Math.max(0, (treadDepthMm - 1.6) / (7.0 - 1.6)));
        return 0.5 + 0.5 * fraction;
    }
}

package thurnos.brake_simulation.engine;

/**
 * Bridges the existing data model (cars, tires, brakes, roads, driver condition) to the engine.
 *
 * <p>Takes plain numbers so it can be used and tested without Spring or JPA. The service layer
 * reads the entities and passes their values in.
 */
public final class ScenarioFactory {

    /** Typical film on a road that is simply "wet": light to moderate rain. */
    public static final double TYPICAL_WET_FILM_MM = 2.0;

    private ScenarioFactory() {
    }

    /**
     * Convenience overload: the water depth is inferred from the road family (a wet-grip road is given a typical
     * {@value #TYPICAL_WET_FILM_MM} mm film, everything else is dry). Prefer the overload with an explicit depth
     * when the road row says how deep the water is.
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
        return fromTableValues(weightKg, tireBaseGrip, roadModifier, brakeEfficiency, treadDepthMm, slopePercent,
                hasAbs, reactionTimeS, speedKmh, defaultWaterDepthMm(roadModifier), obstacleM);
    }

    /** Water film assumed for a road row that does not say: typical for wet-grip roads, none otherwise. */
    public static double defaultWaterDepthMm(double roadModifier) {
        return SurfaceKind.fromGripModifier(roadModifier) == SurfaceKind.WET ? TYPICAL_WET_FILM_MM : 0.0;
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
     * @param waterDepthMm    depth of the water film on the road, or null for none
     * @param initialBrakeTempC brake temperature at the start, or null for air temperature (20 °C)
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
                                           Double waterDepthMm,
                                           Double obstacleM) {
        return fromTableValues(weightKg, tireBaseGrip, roadModifier, brakeEfficiency, treadDepthMm, slopePercent,
                hasAbs, reactionTimeS, speedKmh, waterDepthMm, null, obstacleM);
    }

    /**
     * Same, with the brake temperature at the start of the run (both axles, °C; null = brakes at air temperature).
     * Hot brakes fade, which matters when the brakes rather than the tires limit braking.
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
                                           Double waterDepthMm,
                                           Double initialBrakeTempC,
                                           Double obstacleM) {
        BrakeThermal thermal = BrakeThermal.estimate(weightKg);
        if (initialBrakeTempC != null) {
            thermal = thermal.withInitialTemps(initialBrakeTempC, initialBrakeTempC);
        }
        WaterFilm film = WaterFilm.of(waterDepthMm == null ? 0.0 : waterDepthMm, treadDepthMm);
        double peakMu = tireBaseGrip * roadModifier * film.treadGripFactor();
        return new Scenario(
                VehicleSpec.estimate(weightKg),
                BrakeSpec.fromEfficiency(brakeEfficiency).withThermal(thermal),
                TireSpec.forSurface(peakMu, SurfaceKind.fromGripModifier(roadModifier)).withWater(film),
                DriverSpec.fromReactionTime(reactionTimeS),
                Scenario.kmhToMs(speedKmh),
                slopePercent,
                hasAbs,
                obstacleM);
    }
}

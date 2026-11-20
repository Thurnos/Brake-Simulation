package thurnos.brake_simulation.engine;

/**
 * Standing water between tire and road, and what the tire's tread can do about it.
 *
 * <p>Two effects, both only when the road is wet:
 * <ul>
 *   <li><b>Tread and wet grip.</b> Worn tread drains water less well, so peak friction on a wet road is lower.
 *       On a dry road tread depth does not matter in this model.</li>
 *   <li><b>Hydroplaning.</b> Above a speed that depends on tire pressure, tread and water depth, the tire starts
 *       riding on the water and friction collapses. The loss is gradual around the onset speed and reverses as
 *       the car slows, so a car can start a stop on water and regain grip before it stops.</li>
 * </ul>
 *
 * <p>The onset speed is anchored to the NASA/Horne result for complete dynamic hydroplaning of a smooth tire
 * on a flooded surface, {@code V = 9·√p} knots with p in psi (about 94 km/h at 220 kPa). Tread grooves raise it
 * while the water is shallow compared with the groove depth; the benefit fades as the water deepens and
 * disappears when the tread is worn out. How much the grooves raise it ({@link #DRAINAGE}) is a simplification
 * of this model, not a published constant. Viscous hydroplaning on thin films, which can occur at lower speeds,
 * is not modelled.
 *
 * @param waterDepthMm    depth of the water film on the road (0 = dry)
 * @param treadDepthMm    remaining tread depth of the tires
 * @param tirePressureKPa tire inflation pressure
 */
public record WaterFilm(double waterDepthMm, double treadDepthMm, double tirePressureKPa) {

    public static final double DEFAULT_PRESSURE_KPA = 220.0;

    /** No water: tread depth is irrelevant and nothing hydroplanes. */
    public static final WaterFilm NONE = new WaterFilm(0, 8, DEFAULT_PRESSURE_KPA);

    /** Horne's constant: onset speed in knots per square root of tire pressure in psi. */
    static final double HORNE_KNOTS_PER_SQRT_PSI = 9.0;

    /** How strongly tread raises the onset speed: onset = smooth-tire onset × (1 + DRAINAGE × tread / water). */
    static final double DRAINAGE = 0.5;

    /** Share of normal friction left when the tire rides fully on water. */
    static final double FRICTION_ON_WATER = 0.10;

    /** Speed, as a fraction of the onset speed, where half of the friction loss has happened. */
    static final double TRANSITION_CENTRE = 0.85;
    static final double TRANSITION_SHARPNESS = 6.0;

    /** Peak friction on a wet road with fully worn tread (1.6 mm), relative to a new tire (8 mm). */
    static final double WORN_WET_GRIP = 0.70;
    static final double LEGAL_LIMIT_MM = 1.6;
    static final double NEW_TREAD_MM = 8.0;

    /** Water film depth at which a road counts as fully wet for the tread effect. */
    static final double FULLY_WET_MM = 0.5;

    private static final double KNOT_MS = 0.514444;
    private static final double PSI_PER_KPA = 1.0 / 6.89476;

    public WaterFilm {
        VehicleSpec.require(waterDepthMm >= 0, "waterDepthMm must not be negative");
        VehicleSpec.require(treadDepthMm >= 0 && treadDepthMm <= 20, "treadDepthMm must be between 0 and 20");
        VehicleSpec.require(tirePressureKPa >= 100 && tirePressureKPa <= 400,
                "tirePressureKPa must be between 100 and 400");
    }

    public static WaterFilm of(double waterDepthMm, double treadDepthMm) {
        return new WaterFilm(waterDepthMm, treadDepthMm, DEFAULT_PRESSURE_KPA);
    }

    public boolean isWet() {
        return waterDepthMm > 0;
    }

    /** NASA/Horne onset speed for a smooth tire on a flooded surface, in m/s. */
    public double smoothTireOnsetSpeedMs() {
        return HORNE_KNOTS_PER_SQRT_PSI * Math.sqrt(tirePressureKPa * PSI_PER_KPA) * KNOT_MS;
    }

    /** Speed at which hydroplaning sets in, in m/s; infinite on a dry road. */
    public double onsetSpeedMs() {
        if (!isWet()) return Double.POSITIVE_INFINITY;
        return smoothTireOnsetSpeedMs() * (1 + DRAINAGE * treadDepthMm / waterDepthMm);
    }

    /**
     * Share of normal friction that is left at this speed: 1 well below the onset speed, falling through
     * the onset speed to {@link #FRICTION_ON_WATER}.
     */
    public double gripFactor(double speedMs) {
        if (!isWet() || speedMs <= 0) return 1.0;
        double x = speedMs / onsetSpeedMs();
        double stillOnRoad = 1.0 / (1.0 + Math.pow(x / TRANSITION_CENTRE, TRANSITION_SHARPNESS));
        return FRICTION_ON_WATER + (1 - FRICTION_ON_WATER) * stillOnRoad;
    }

    /**
     * Peak-friction factor from tread wear. 1.0 on a dry road and for new tread; falls to 0.7 on a wet road
     * at the legal limit.
     */
    public double treadGripFactor() {
        double wetness = Math.min(1.0, waterDepthMm / FULLY_WET_MM);
        double wear = 1.0 - Math.min(1.0, Math.max(0.0,
                (treadDepthMm - LEGAL_LIMIT_MM) / (NEW_TREAD_MM - LEGAL_LIMIT_MM)));
        return 1.0 - (1.0 - WORN_WET_GRIP) * wear * wetness;
    }
}

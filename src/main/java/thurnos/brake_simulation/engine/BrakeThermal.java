package thurnos.brake_simulation.engine;

/**
 * Heating, cooling and fade of the brakes, one lumped temperature per axle.
 *
 * <p>Brake work heats the rotors; air flowing past cools them, more the faster the car goes. Hot pads lose
 * friction ("fade"): nothing happens up to {@code fadeStartC}, then friction falls smoothly to
 * {@code fadeFloor} of normal at {@code fadeFullC}. Fade only shortens the brake force the system can deliver,
 * so it only lengthens a stop when the brakes, not the tires, are what limits braking.
 *
 * <p>The numbers are typical illustrative values for steel rotors and organic pads, not fitted to any
 * particular brake. Rotor mass of 0 on both axles switches the model off ({@link #NONE}).
 *
 * @param frontRotorKg  effective heat-absorbing mass at the front axle (both rotors), kg
 * @param rearRotorKg   same at the rear axle
 * @param ambientC      air temperature
 * @param initialFrontC front brake temperature when the run starts
 * @param initialRearC  rear brake temperature when the run starts
 * @param heatToRotor   share of brake work that ends up in the rotor (the rest leaves with pads and air)
 * @param fadeStartC    temperature where friction starts to drop
 * @param fadeFullC     temperature where friction reaches its floor
 * @param fadeFloor     friction left at and above {@code fadeFullC}, as a share of normal
 */
public record BrakeThermal(double frontRotorKg, double rearRotorKg, double ambientC,
                           double initialFrontC, double initialRearC, double heatToRotor,
                           double fadeStartC, double fadeFullC, double fadeFloor) {

    /** Specific heat of cast iron, J/(kg·K). */
    static final double SPECIFIC_HEAT = 460.0;
    /** Cooling per kg of rotor at standstill, W/K, and extra per m/s of car speed. */
    static final double COOLING_STATIC_PER_KG = 1.1;
    static final double COOLING_PER_KG_PER_MS = 0.17;

    /** Thermal model off: brakes never heat and never fade. */
    public static final BrakeThermal NONE = new BrakeThermal(0, 0, 20, 20, 20, 0.9, 350, 750, 0.5);

    public BrakeThermal {
        VehicleSpec.require(frontRotorKg >= 0 && rearRotorKg >= 0, "rotor mass must not be negative");
        VehicleSpec.require(ambientC >= -60 && ambientC <= 60, "ambientC must be between -60 and 60");
        VehicleSpec.require(initialFrontC >= -60 && initialFrontC <= 1200, "initialFrontC out of range");
        VehicleSpec.require(initialRearC >= -60 && initialRearC <= 1200, "initialRearC out of range");
        VehicleSpec.require(heatToRotor > 0 && heatToRotor <= 1, "heatToRotor must be in (0,1]");
        VehicleSpec.require(fadeFullC > fadeStartC, "fadeFullC must be above fadeStartC");
        VehicleSpec.require(fadeFloor > 0 && fadeFloor <= 1, "fadeFloor must be in (0,1]");
    }

    /** Typical passenger car: rotor mass scales with vehicle mass (about 9 kg front, 5 kg rear at 1500 kg). */
    public static BrakeThermal estimate(double vehicleMassKg) {
        return new BrakeThermal(0.006 * vehicleMassKg, 0.0035 * vehicleMassKg, 20, 20, 20, 0.9, 350, 750, 0.5);
    }

    public boolean enabled() {
        return frontRotorKg > 0 && rearRotorKg > 0;
    }

    public BrakeThermal withAmbient(double c) {
        return new BrakeThermal(frontRotorKg, rearRotorKg, c, initialFrontC, initialRearC, heatToRotor,
                fadeStartC, fadeFullC, fadeFloor);
    }

    public BrakeThermal withInitialTemps(double frontC, double rearC) {
        return new BrakeThermal(frontRotorKg, rearRotorKg, ambientC, frontC, rearC, heatToRotor,
                fadeStartC, fadeFullC, fadeFloor);
    }

    double frontHeatCapacityJK() {
        return frontRotorKg * SPECIFIC_HEAT;
    }

    double rearHeatCapacityJK() {
        return rearRotorKg * SPECIFIC_HEAT;
    }

    /** Heat loss to the air in W/K for a rotor of the given mass at the given car speed. */
    static double coolingWK(double rotorKg, double speedMs) {
        return rotorKg * (COOLING_STATIC_PER_KG + COOLING_PER_KG_PER_MS * Math.max(0, speedMs));
    }

    /** Share of normal pad friction left at this temperature: 1 up to the fade start, then falling to the floor. */
    public double fadeFactor(double tempC) {
        double u = (tempC - fadeStartC) / (fadeFullC - fadeStartC);
        if (u <= 0) return 1.0;
        if (u >= 1) return fadeFloor;
        double smooth = u * u * (3 - 2 * u);
        return 1.0 - (1.0 - fadeFloor) * smooth;
    }

    /** Temperatures after the brakes have cooled for {@code seconds} while driving at {@code speedMs}. */
    public double[] cooled(double frontC, double rearC, double seconds, double speedMs) {
        VehicleSpec.require(seconds >= 0, "seconds must not be negative");
        if (!enabled()) return new double[]{frontC, rearC};
        double f = ambientC + (frontC - ambientC)
                * Math.exp(-coolingWK(frontRotorKg, speedMs) * seconds / frontHeatCapacityJK());
        double r = ambientC + (rearC - ambientC)
                * Math.exp(-coolingWK(rearRotorKg, speedMs) * seconds / rearHeatCapacityJK());
        return new double[]{f, r};
    }
}

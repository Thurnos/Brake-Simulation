package thurnos.brake_simulation.engine;

/**
 * Physical description of the car. Pure data, no framework dependencies.
 *
 * @param massKg              total mass including driver and load
 * @param wheelbaseM          distance between front and rear axle
 * @param cgHeightM           height of the centre of gravity above the road
 * @param frontWeightFraction share of static weight on the front axle (0.5 = evenly split)
 * @param dragCoefficient     aerodynamic Cd (0 switches drag off)
 * @param frontalAreaM2       frontal area used for drag
 * @param rollingResistance   rolling resistance coefficient Crr (0 switches it off)
 */
public record VehicleSpec(double massKg,
                          double wheelbaseM,
                          double cgHeightM,
                          double frontWeightFraction,
                          double dragCoefficient,
                          double frontalAreaM2,
                          double rollingResistance) {

    public VehicleSpec {
        require(massKg > 0, "massKg must be positive");
        require(wheelbaseM > 0, "wheelbaseM must be positive");
        require(cgHeightM >= 0, "cgHeightM must not be negative");
        require(frontWeightFraction > 0.2 && frontWeightFraction < 0.8,
                "frontWeightFraction must be between 0.2 and 0.8");
        require(dragCoefficient >= 0, "dragCoefficient must not be negative");
        require(frontalAreaM2 >= 0, "frontalAreaM2 must not be negative");
        require(rollingResistance >= 0, "rollingResistance must not be negative");
    }

    /**
     * Typical passenger-car geometry for a given mass. Use this until the cars table
     * stores real wheelbase / CoG height values.
     */
    public static VehicleSpec estimate(double massKg) {
        return new VehicleSpec(massKg, 2.70, 0.55, 0.57, 0.30, 2.2, 0.012);
    }

    /** Same car with drag and rolling resistance switched off (useful for validation). */
    public VehicleSpec withoutResistances() {
        return new VehicleSpec(massKg, wheelbaseM, cgHeightM, frontWeightFraction, 0, frontalAreaM2, 0);
    }

    public VehicleSpec withMass(double newMassKg) {
        return new VehicleSpec(newMassKg, wheelbaseM, cgHeightM, frontWeightFraction,
                dragCoefficient, frontalAreaM2, rollingResistance);
    }

    public VehicleSpec withCgHeight(double newCgHeightM) {
        return new VehicleSpec(massKg, wheelbaseM, newCgHeightM, frontWeightFraction,
                dragCoefficient, frontalAreaM2, rollingResistance);
    }

    static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}

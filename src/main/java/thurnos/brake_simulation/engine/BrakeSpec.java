package thurnos.brake_simulation.engine;

/**
 * The brake system as seen from the road.
 *
 * @param maxForceFactor   total brake force at full pedal as a multiple of vehicle weight
 *                         (1.2 = brakes could produce 1.2 g if the tires could hold it)
 * @param frontBias        share of brake force sent to the front axle (0..1)
 * @param pedalRiseTimeS   time for brake force to build from zero to full once the foot is on the pedal
 */
public record BrakeSpec(double maxForceFactor, double frontBias, double pedalRiseTimeS) {

    public BrakeSpec {
        VehicleSpec.require(maxForceFactor > 0, "maxForceFactor must be positive");
        VehicleSpec.require(frontBias > 0 && frontBias < 1, "frontBias must be between 0 and 1");
        VehicleSpec.require(pedalRiseTimeS >= 0, "pedalRiseTimeS must not be negative");
    }

    /** Typical hydraulic system: 70% front bias, force builds over 0.15 s. */
    public static BrakeSpec fromEfficiency(double efficiency) {
        return new BrakeSpec(1.3 * efficiency, 0.70, 0.15);
    }
}

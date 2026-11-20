package thurnos.brake_simulation.engine;

/**
 * Driver timing. Perception and foot movement are kept apart because a later phase
 * can model them separately (distraction changes the first, a brake-assist system the second).
 *
 * @param perceptionTimeS time from the hazard appearing to the foot starting to move
 * @param footMoveTimeS   time to move the foot to the brake pedal and touch it
 */
public record DriverSpec(double perceptionTimeS, double footMoveTimeS) {

    public DriverSpec {
        VehicleSpec.require(perceptionTimeS >= 0, "perceptionTimeS must not be negative");
        VehicleSpec.require(footMoveTimeS >= 0, "footMoveTimeS must not be negative");
    }

    /** Total time before any brake force starts to build. */
    public double totalDelayS() {
        return perceptionTimeS + footMoveTimeS;
    }

    /** Builds a driver from a single reaction time such as {@code DriverCondition.getReactionTime()}. */
    public static DriverSpec fromReactionTime(double reactionTimeS) {
        double foot = Math.min(0.2, reactionTimeS);
        return new DriverSpec(reactionTimeS - foot, foot);
    }
}

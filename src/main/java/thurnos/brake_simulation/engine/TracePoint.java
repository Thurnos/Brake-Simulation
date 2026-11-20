package thurnos.brake_simulation.engine;

/**
 * One recorded moment of a run. Forces are in newtons, decelerations in m/s² (positive = slowing down).
 * Brake forces are what the tires push on the road with, which is what actually slows the car.
 * Slip is 0 for a freely rolling wheel and 1 for a locked one; pressure is the fraction of the driver's
 * demand that reaches the brakes (below 1 while ABS is releasing). The water grip factor is the share of normal
 * friction left at the current speed: 1 on a dry road, lower while the tires hydroplane.
 */
public record TracePoint(double timeS,
                         double positionM,
                         double speedMs,
                         double decelerationMs2,
                         double pedal,
                         double frontBrakeForceN,
                         double rearBrakeForceN,
                         double frontLoadN,
                         double rearLoadN,
                         boolean frontLocked,
                         boolean rearLocked,
                         double frontSlip,
                         double rearSlip,
                         double frontPressure,
                         double rearPressure,
                         double waterGripFactor) {
}

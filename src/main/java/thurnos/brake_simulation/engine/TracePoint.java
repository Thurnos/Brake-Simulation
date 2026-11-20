package thurnos.brake_simulation.engine;

/**
 * One recorded moment of a run. Forces are in newtons, decelerations in m/s² (positive = slowing down).
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
                         boolean rearLocked) {
}

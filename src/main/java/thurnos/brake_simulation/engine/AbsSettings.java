package thurnos.brake_simulation.engine;

/**
 * Tuning of the ABS controller. It runs per axle and never looks at the road: it only sees how the wheel is
 * turning compared with the car. When a wheel slows down much faster than the car does, it is running away
 * toward lock-up, so the controller releases pressure until the wheel recovers, then builds pressure again.
 * That cycle is the pulsing felt through the pedal.
 *
 * @param divergenceMs2 how much faster than the car (m/s², measured at the tire) the wheel may slow
 *                      before the controller starts releasing
 * @param hardSlip      slip above which the controller releases regardless of wheel deceleration
 * @param exitSlip      slip below which, once the wheel is recovering, pressure is built again
 * @param releaseRate   pressure dumped per second while releasing (fraction of driver demand per second)
 * @param applyRate     pressure rebuilt per second while re-applying
 * @param minSpeedMs    below this speed ABS switches off, as real systems do near standstill
 * @param controlPeriodS how often the controller looks at the wheels (real units: about 5-10 ms).
 *                      It judges wheel behaviour over this window, not step by step.
 */
public record AbsSettings(double divergenceMs2,
                          double hardSlip,
                          double exitSlip,
                          double releaseRate,
                          double applyRate,
                          double minSpeedMs,
                          double controlPeriodS) {

    public AbsSettings {
        VehicleSpec.require(divergenceMs2 > 0, "divergenceMs2 must be positive");
        VehicleSpec.require(hardSlip > 0 && hardSlip <= 1, "hardSlip must be in (0,1]");
        VehicleSpec.require(exitSlip > 0 && exitSlip < hardSlip, "exitSlip must be between 0 and hardSlip");
        VehicleSpec.require(releaseRate > 0 && applyRate > 0, "release and apply rates must be positive");
        VehicleSpec.require(minSpeedMs >= 0, "minSpeedMs must not be negative");
        VehicleSpec.require(controlPeriodS >= 0.001 && controlPeriodS <= 0.05,
                "controlPeriodS must be between 0.001 and 0.05");
    }

    public static AbsSettings defaults() {
        return new AbsSettings(10.0, 0.35, 0.18, 1.5, 9.0, 1.5, 0.005);
    }
}


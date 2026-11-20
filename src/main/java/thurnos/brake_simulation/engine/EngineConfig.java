package thurnos.brake_simulation.engine;

/**
 * Numerical settings for a run.
 *
 * @param dtS             integration step in seconds (0.001 is plenty for braking)
 * @param maxTimeS        give up after this long; a car still moving then is reported as not stopped
 * @param traceIntervalS  spacing of recorded trace points
 * @param absEfficiency   share of peak friction an ABS-equipped axle keeps when demand exceeds grip.
 *                        Phase 1 stand-in for a real slip controller.
 */
public record EngineConfig(double dtS, double maxTimeS, double traceIntervalS, double absEfficiency) {

    public EngineConfig {
        VehicleSpec.require(dtS > 0 && dtS <= 0.01, "dtS must be in (0, 0.01]");
        VehicleSpec.require(maxTimeS > 0, "maxTimeS must be positive");
        VehicleSpec.require(traceIntervalS >= dtS, "traceIntervalS must be at least dtS");
        VehicleSpec.require(absEfficiency > 0 && absEfficiency <= 1, "absEfficiency must be in (0,1]");
    }

    public static EngineConfig defaults() {
        return new EngineConfig(0.001, 120.0, 0.02, 0.95);
    }

    public EngineConfig withDt(double newDtS) {
        return new EngineConfig(newDtS, maxTimeS, Math.max(traceIntervalS, newDtS), absEfficiency);
    }
}

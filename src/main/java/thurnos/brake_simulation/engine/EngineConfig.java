package thurnos.brake_simulation.engine;

/**
 * Numerical and controller settings for a run.
 *
 * @param dtS             integration step in seconds (0.001 is plenty for braking)
 * @param maxTimeS        give up after this long; a car still moving then is reported as not stopped
 * @param traceIntervalS  spacing of recorded trace points
 * @param abs             ABS controller tuning
 */
public record EngineConfig(double dtS, double maxTimeS, double traceIntervalS, AbsSettings abs) {

    public EngineConfig {
        VehicleSpec.require(dtS > 0 && dtS <= 0.01, "dtS must be in (0, 0.01]");
        VehicleSpec.require(maxTimeS > 0, "maxTimeS must be positive");
        VehicleSpec.require(traceIntervalS >= dtS, "traceIntervalS must be at least dtS");
        VehicleSpec.require(abs != null, "abs settings are required");
    }

    public static EngineConfig defaults() {
        return new EngineConfig(0.001, 120.0, 0.02, AbsSettings.defaults());
    }

    public EngineConfig withDt(double newDtS) {
        return new EngineConfig(newDtS, maxTimeS, Math.max(traceIntervalS, newDtS), abs);
    }

    public EngineConfig withAbs(AbsSettings settings) {
        return new EngineConfig(dtS, maxTimeS, traceIntervalS, settings);
    }
}

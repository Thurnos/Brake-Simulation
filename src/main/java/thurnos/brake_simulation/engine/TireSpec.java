package thurnos.brake_simulation.engine;

/**
 * Tire / road friction, Phase 1 version: one peak friction number per tire-on-surface.
 * Phase 2 replaces this with a slip curve.
 *
 * @param peakMu             peak friction coefficient of this tire on this surface
 * @param loadSensitivity    how much friction drops as an axle gets more loaded than at rest
 *                           (0 = none; about 0.1 is typical). Lightly loaded axles grip slightly better.
 * @param lockedFrictionRatio share of peak friction left when the wheels are locked and sliding (about 0.8)
 */
public record TireSpec(double peakMu, double loadSensitivity, double lockedFrictionRatio) {

    public TireSpec {
        VehicleSpec.require(peakMu > 0, "peakMu must be positive");
        VehicleSpec.require(loadSensitivity >= 0 && loadSensitivity < 1, "loadSensitivity must be in [0,1)");
        VehicleSpec.require(lockedFrictionRatio > 0 && lockedFrictionRatio <= 1,
                "lockedFrictionRatio must be in (0,1]");
    }

    public static TireSpec of(double peakMu) {
        return new TireSpec(peakMu, 0.10, 0.80);
    }

    public TireSpec withLoadSensitivity(double k) {
        return new TireSpec(peakMu, k, lockedFrictionRatio);
    }
}

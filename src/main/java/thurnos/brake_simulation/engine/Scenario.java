package thurnos.brake_simulation.engine;

/**
 * One braking situation: a car, its brakes and tires, a driver, and the road.
 *
 * @param initialSpeedMs    speed at the moment the hazard appears, in m/s
 * @param slopePercent      road gradient in percent; positive = downhill (same convention as the old engine)
 * @param absEnabled        whether the car has working ABS
 * @param obstacleDistanceM optional obstacle this far ahead of the car's start position, or null for none
 */
public record Scenario(VehicleSpec vehicle,
                       BrakeSpec brakes,
                       TireSpec tires,
                       DriverSpec driver,
                       double initialSpeedMs,
                       double slopePercent,
                       boolean absEnabled,
                       Double obstacleDistanceM) {

    public Scenario {
        VehicleSpec.require(vehicle != null && brakes != null && tires != null && driver != null,
                "vehicle, brakes, tires and driver are required");
        VehicleSpec.require(initialSpeedMs >= 0, "initialSpeedMs must not be negative");
        VehicleSpec.require(Math.abs(slopePercent) < 100, "slopePercent must be between -100 and 100");
        VehicleSpec.require(obstacleDistanceM == null || obstacleDistanceM > 0,
                "obstacleDistanceM must be positive");
    }

    public static double kmhToMs(double kmh) {
        return kmh / 3.6;
    }

    public Scenario withAbs(boolean abs) {
        return new Scenario(vehicle, brakes, tires, driver, initialSpeedMs, slopePercent, abs, obstacleDistanceM);
    }

    public Scenario withSlope(double percent) {
        return new Scenario(vehicle, brakes, tires, driver, initialSpeedMs, percent, absEnabled, obstacleDistanceM);
    }

    public Scenario withVehicle(VehicleSpec v) {
        return new Scenario(v, brakes, tires, driver, initialSpeedMs, slopePercent, absEnabled, obstacleDistanceM);
    }

    public Scenario withBrakes(BrakeSpec b) {
        return new Scenario(vehicle, b, tires, driver, initialSpeedMs, slopePercent, absEnabled, obstacleDistanceM);
    }

    public Scenario withTires(TireSpec t) {
        return new Scenario(vehicle, brakes, t, driver, initialSpeedMs, slopePercent, absEnabled, obstacleDistanceM);
    }

    public Scenario withDriver(DriverSpec d) {
        return new Scenario(vehicle, brakes, tires, d, initialSpeedMs, slopePercent, absEnabled, obstacleDistanceM);
    }

    public Scenario withSpeedMs(double v) {
        return new Scenario(vehicle, brakes, tires, driver, v, slopePercent, absEnabled, obstacleDistanceM);
    }

    public Scenario withObstacle(Double distanceM) {
        return new Scenario(vehicle, brakes, tires, driver, initialSpeedMs, slopePercent, absEnabled, distanceM);
    }
}

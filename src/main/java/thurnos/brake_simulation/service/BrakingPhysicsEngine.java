package thurnos.brake_simulation.service;
import org.springframework.stereotype.Component;

@Component
public class BrakingPhysicsEngine {

    private static final double G = 9.81;

    public double calculateStoppingDistance(double speedKmh,
                                            double tireGrip,
                                            double treadDepth,
                                            double roadModifier,
                                            double brakeEfficiency,
                                            double carWeight) {

        double speedMs = speedKmh / 3.6;

        double wearFactor = calculateWearFactor(treadDepth);

        double mu = tireGrip * wearFactor * roadModifier;

        double adjustedMu = applyLoadEffect(mu, carWeight);

        double adjustedBrakeEfficiency = applyBrakeFade(brakeEfficiency, carWeight);

        double deceleration = adjustedMu * G * adjustedBrakeEfficiency;

        return (speedMs * speedMs) / (2 * deceleration);
    }

    public double calculateStoppingTime(double speedKmh,
                                        double tireGrip,
                                        double treadDepth,
                                        double roadModifier,
                                        double brakeEfficiency,
                                        double carWeight) {

        double speedMs = speedKmh / 3.6;

        double wearFactor = calculateWearFactor(treadDepth);

        double mu = tireGrip * wearFactor * roadModifier;

        double adjustedMu = applyLoadEffect(mu, carWeight);

        double adjustedBrakeEfficiency = applyBrakeFade(brakeEfficiency, carWeight);

        double deceleration = adjustedMu * G * adjustedBrakeEfficiency;

        return speedMs / deceleration;
    }

    // =========================
    // HELPERS
    // =========================

    private double calculateWearFactor(double treadDepth) {
        if (treadDepth >= 7) return 1.0;
        if (treadDepth >= 5) return 0.9;
        if (treadDepth >= 3) return 0.75;
        if (treadDepth >= 2) return 0.6;
        return 0.5;
    }

    private double applyLoadEffect(double mu, double weight) {
        double factor = weight / 2000.0; // normalize
        return mu * (1 - 0.1 * factor);
    }

    private double applyBrakeFade(double efficiency, double weight) {
        double factor = weight / 2000.0;
        return efficiency * (1 - 0.05 * factor);
    }
}
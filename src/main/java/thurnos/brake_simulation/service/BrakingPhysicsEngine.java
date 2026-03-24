package thurnos.brake_simulation.service;

import org.springframework.stereotype.Component;
import thurnos.brake_simulation.models.Simulations;

@Component
public class BrakingPhysicsEngine {

    private static final double G = 9.81;

    public Simulations calculateFullStop(double speedKmh,
                                         double tireGrip,
                                         double treadDepth,
                                         double roadModifier,
                                         double brakeEfficiency,
                                         double carWeight,
                                         double reactionTime,
                                         boolean hasABS,
                                         double slopePercent) {

        double speedMs = speedKmh / 3.6;

        // =========================
        // REACTION PHASE
        // =========================
        double reactionDistance = speedMs * reactionTime;

        // =========================
        // GRIP CALCULATION
        // =========================
        double wearFactor = calculateWearFactor(treadDepth);
        double mu = tireGrip * wearFactor * roadModifier;

        double adjustedMu = applyLoadEffect(mu, carWeight);
        double adjustedBrakeEfficiency = applyBrakeFade(brakeEfficiency, carWeight);

        // =========================
        // ABS EFFECT
        // =========================
        double absFactor = hasABS ? 1.0 : 0.85;
        adjustedBrakeEfficiency *= absFactor;

        // =========================
        // SLOPE EFFECT
        // =========================
        double slopeFactor = slopePercent / 100.0;

        double deceleration = (adjustedMu * G * adjustedBrakeEfficiency) - (G * slopeFactor);

        // Edge case: cannot stop
        if (deceleration <= 0) {
            Simulations sim = new Simulations();
            sim.setReactionDistance(reactionDistance);
            sim.setBrakingDistance(Double.MAX_VALUE);
            sim.setTotalDistance(Double.MAX_VALUE);
            sim.setBrakingTime(Double.MAX_VALUE);
            sim.setTotalTime(Double.MAX_VALUE);
            return sim;
        }

        // =========================
        // BRAKING PHASE
        // =========================
        double brakingDistance = (speedMs * speedMs) / (2 * deceleration);
        double brakingTime = speedMs / deceleration;

        // =========================
        // TOTAL
        // =========================
        double totalDistance = reactionDistance + brakingDistance;
        double totalTime = reactionTime + brakingTime;

        Simulations sim = new Simulations();
        sim.setReactionDistance(reactionDistance);
        sim.setBrakingDistance(brakingDistance);
        sim.setTotalDistance(totalDistance);
        sim.setBrakingTime(brakingTime);
        sim.setTotalTime(totalTime);

        return sim;
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
        double factor = weight / 2000.0;
        return mu * (1 - 0.1 * factor);
    }

    private double applyBrakeFade(double efficiency, double weight) {
        double factor = weight / 2000.0;
        return efficiency * (1 - 0.05 * factor);
    }
}
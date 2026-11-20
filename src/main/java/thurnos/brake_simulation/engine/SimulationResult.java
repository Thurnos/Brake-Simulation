package thurnos.brake_simulation.engine;

import java.util.List;

/**
 * Outcome of one run. Fields that only make sense when the car actually stopped are {@code null}
 * otherwise, so a car that cannot stop never produces a fake "infinite" distance.
 *
 * @param stopped              true if the car came to rest within the time limit
 * @param stopTimeS            time from the hazard to standstill, or null
 * @param stopDistanceM        total distance from the hazard to standstill, or null
 * @param reactionDistanceM    distance covered before brake force starts to build
 * @param brakingDistanceM     stopDistanceM minus reactionDistanceM, or null
 * @param distanceTravelledM   distance covered by the end of the run, stopped or not
 * @param timeSimulatedS       length of the run
 * @param peakDecelerationMs2  strongest deceleration reached
 * @param impactSpeedKmh       speed at the obstacle (0 if the car stops short), or null when there is no obstacle
 * @param brakeEnergyJ         energy turned into heat in the brakes (input for the Phase 4 thermal model)
 * @param tireSlipEnergyJ      energy lost to tire slip against the road
 * @param hydroplaningOnsetKmh speed above which the tires start to hydroplane, or null on a dry road
 * @param thermal              brake temperatures and fade over the run, or null when the thermal model is off
 * @param trace                sampled state over time
 */
public record SimulationResult(boolean stopped,
                               Double stopTimeS,
                               Double stopDistanceM,
                               double reactionDistanceM,
                               Double brakingDistanceM,
                               double distanceTravelledM,
                               double timeSimulatedS,
                               double peakDecelerationMs2,
                               Double impactSpeedKmh,
                               double brakeEnergyJ,
                               double tireSlipEnergyJ,
                               Double hydroplaningOnsetKmh,
                               ThermalSummary thermal,
                               List<TracePoint> trace) {

    /**
     * Brake thermal outcome of a run.
     *
     * @param peakFrontC    highest front brake temperature
     * @param peakRearC     highest rear brake temperature
     * @param finalFrontC   front brake temperature at the end
     * @param finalRearC    rear brake temperature at the end
     * @param minFadeFactor lowest share of normal pad friction on either axle (1 = no fade)
     */
    public record ThermalSummary(double peakFrontC, double peakRearC, double finalFrontC, double finalRearC,
                                 double minFadeFactor) {
    }

    /** Same result without the sampled trace, for callers that only need the summary numbers. */
    public SimulationResult withoutTrace() {
        return new SimulationResult(stopped, stopTimeS, stopDistanceM, reactionDistanceM, brakingDistanceM,
                distanceTravelledM, timeSimulatedS, peakDecelerationMs2, impactSpeedKmh,
                brakeEnergyJ, tireSlipEnergyJ, hydroplaningOnsetKmh, thermal, List.of());
    }

    /** Convenience for callers that know the car stops. */
    public double requireStopDistanceM() {
        if (stopDistanceM == null) throw new IllegalStateException("the car did not stop");
        return stopDistanceM;
    }
}

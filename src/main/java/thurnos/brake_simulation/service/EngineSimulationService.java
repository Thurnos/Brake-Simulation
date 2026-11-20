package thurnos.brake_simulation.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import thurnos.brake_simulation.engine.BrakingEngine;
import thurnos.brake_simulation.engine.Scenario;
import thurnos.brake_simulation.engine.ScenarioFactory;
import thurnos.brake_simulation.engine.SimulationResult;
import thurnos.brake_simulation.models.Brakes;
import thurnos.brake_simulation.models.Cars;
import thurnos.brake_simulation.models.DriverCondition;
import thurnos.brake_simulation.models.RoadConditions;
import thurnos.brake_simulation.models.Tires;
import thurnos.brake_simulation.repository.BrakesRepository;
import thurnos.brake_simulation.repository.CarsRepository;
import thurnos.brake_simulation.repository.RoadConditionsRepository;
import thurnos.brake_simulation.repository.TiresRepository;

import java.math.BigInteger;

/**
 * Runs the time-stepped engine for a combination of rows from the existing tables.
 * Unlike {@link SimulationsService} it does not store anything: the answer is a full time trace
 * the frontend can play back.
 */
@Service
public class EngineSimulationService {

    private final CarsRepository carRepo;
    private final TiresRepository tireRepo;
    private final BrakesRepository brakeRepo;
    private final RoadConditionsRepository roadRepo;
    private final BrakingEngine engine = new BrakingEngine();

    public EngineSimulationService(CarsRepository carRepo,
                                   TiresRepository tireRepo,
                                   BrakesRepository brakeRepo,
                                   RoadConditionsRepository roadRepo) {
        this.carRepo = carRepo;
        this.tireRepo = tireRepo;
        this.brakeRepo = brakeRepo;
        this.roadRepo = roadRepo;
    }

    public SimulationResult run(Long carId,
                                Long tireId,
                                Long brakeId,
                                Long roadId,
                                double speedKmh,
                                double treadDepthMm,
                                double slopePercent,
                                boolean hasAbs,
                                DriverCondition driverCondition,
                                Double waterDepthOverrideMm,
                                Double obstacleM,
                                boolean includeTrace) {

        Cars car = carRepo.findById(BigInteger.valueOf(carId))
                .orElseThrow(() -> notFound("Car", carId));
        Tires tire = tireRepo.findById(BigInteger.valueOf(tireId))
                .orElseThrow(() -> notFound("Tire", tireId));
        Brakes brake = brakeRepo.findById(BigInteger.valueOf(brakeId))
                .orElseThrow(() -> notFound("Brake", brakeId));
        RoadConditions road = roadRepo.findById(BigInteger.valueOf(roadId))
                .orElseThrow(() -> notFound("Road condition", roadId));

        // explicit request value wins, then the road row, then a guess from the road family
        Double waterDepthMm = waterDepthOverrideMm != null ? waterDepthOverrideMm : road.getWaterDepthMm();
        double gripModifier = required(road.getGripModifier(), "road grip modifier");
        if (waterDepthMm == null) {
            waterDepthMm = ScenarioFactory.defaultWaterDepthMm(gripModifier);
        }

        Scenario scenario = ScenarioFactory.fromTableValues(
                required(car.getWeightKg(), "car weight"),
                required(tire.getBaseGrip(), "tire base grip"),
                gripModifier,
                required(brake.getEfficiency(), "brake efficiency"),
                treadDepthMm,
                slopePercent,
                hasAbs,
                driverCondition.getReactionTime(),
                speedKmh,
                waterDepthMm,
                obstacleM);

        SimulationResult result = engine.simulate(scenario);
        return includeTrace ? result : result.withoutTrace();
    }

    private static ResponseStatusException notFound(String what, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " " + id + " not found");
    }

    private static double required(Double value, String what) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Missing value: " + what);
        }
        return value;
    }
}

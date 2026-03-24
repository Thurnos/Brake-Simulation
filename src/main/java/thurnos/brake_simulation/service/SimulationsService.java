package thurnos.brake_simulation.service;

import org.springframework.stereotype.Service;
import thurnos.brake_simulation.models.*;
import thurnos.brake_simulation.repository.*;

import java.math.BigInteger;

@Service
public class SimulationsService {

    private final CarsRepository carRepo;
    private final TiresRepository tireRepo;
    private final BrakesRepository brakeRepo;
    private final RoadConditionsRepository roadRepo;
    private final SimulationsRepository simulationRepo;
    private final BrakingPhysicsEngine engine;

    public SimulationsService(CarsRepository carRepo,
                              TiresRepository tireRepo,
                              BrakesRepository brakeRepo,
                              RoadConditionsRepository roadRepo,
                              SimulationsRepository simulationRepo,
                              BrakingPhysicsEngine engine) {
        this.carRepo = carRepo;
        this.tireRepo = tireRepo;
        this.brakeRepo = brakeRepo;
        this.roadRepo = roadRepo;
        this.simulationRepo = simulationRepo;
        this.engine = engine;
    }

    public Simulations runSimulation(Long carId,
                                     Long tireId,
                                     Long brakeId,
                                     Long roadId,
                                     double speedKmh,
                                     double treadDepth,
                                     double slopePercent,
                                     boolean hasABS,
                                     DriverCondition driverCondition) {

        Cars car = carRepo.findById(BigInteger.valueOf(carId)).orElseThrow();
        Tires tire = tireRepo.findById(BigInteger.valueOf(tireId)).orElseThrow();
        Brakes brake = brakeRepo.findById(BigInteger.valueOf(brakeId)).orElseThrow();
        RoadConditions road = roadRepo.findById(BigInteger.valueOf(roadId)).orElseThrow();

        double reactionTime = driverCondition.getReactionTime();

        // 🔥 Call engine
        Simulations result = engine.calculateFullStop(
                speedKmh,
                tire.getBaseGrip(),
                treadDepth,
                road.getGripModifier(),
                brake.getEfficiency(),
                car.getWeightKg(),
                reactionTime,
                hasABS,
                slopePercent
        );

        // 🔹 Merge into entity
        result.setCar(car);
        result.setTire(tire);
        result.setBrake(brake);
        result.setRoadCondition(road);

        result.setSpeedKmh(speedKmh);
        result.setTireTreadMm(treadDepth);
        result.setSlopePercent(slopePercent);
        result.setHasAbs(hasABS);
        result.setReactionTime(reactionTime);

        return simulationRepo.save(result);
    }
}
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

//    public SimulationService(CarsRepository carRepo,
//                             TiresRepository tireRepo,
//                             BrakesRepository brakeRepo,
//                             RoadConditionsRepository roadRepo,
//                             SimulationsRepository simulationRepo,
//                             BrakingPhysicsEngine engine) {
//        this.carRepo = carRepo;
//        this.tireRepo = tireRepo;
//        this.brakeRepo = brakeRepo;
//        this.roadRepo = roadRepo;
//        this.simulationRepo = simulationRepo;
//        this.engine = engine;
//    }

    public SimulationsService(CarsRepository carRepo, TiresRepository tireRepo, BrakesRepository brakeRepo, RoadConditionsRepository roadRepo, SimulationsRepository simulationRepo, BrakingPhysicsEngine engine) {
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
                                     double treadDepth) {

        Cars car = carRepo.findById(BigInteger.valueOf(carId)).orElseThrow();
        Tires tire = tireRepo.findById(BigInteger.valueOf(carId)).orElseThrow();
        Brakes brake = brakeRepo.findById(BigInteger.valueOf(carId)).orElseThrow();
        RoadConditions road = roadRepo.findById(BigInteger.valueOf(carId)).orElseThrow();

        double distance = engine.calculateStoppingDistance(
                speedKmh,
                tire.getBaseGrip(),
                treadDepth,
                road.getGripModifier(),
                brake.getEfficiency(),
                car.getWeightKg()
        );

        double time = engine.calculateStoppingTime(
                speedKmh,
                tire.getBaseGrip(),
                treadDepth,
                road.getGripModifier(),
                brake.getEfficiency(),
                car.getWeightKg()
        );

        Simulations sim = new Simulations();
        sim.setCar(car);
        sim.setTire(tire);
        sim.setBrake(brake);
        sim.setRoadCondition(road);
        sim.setSpeedKmh(speedKmh);
        sim.setTireTreadMm(treadDepth);
        sim.setStoppingDistance(distance);
        sim.setStoppingTime(time);

        return simulationRepo.save(sim);
    }
}
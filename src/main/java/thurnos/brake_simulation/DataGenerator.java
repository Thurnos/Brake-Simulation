//package thurnos.brake_simulation;
//
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//import thurnos.brake_simulation.models.*;
//import thurnos.brake_simulation.repository.*;
//import thurnos.brake_simulation.service.SimulationsService;
//
//import java.math.BigInteger;
//import java.util.List;
//import java.util.Random;
//
//@Component
//public class DataGenerator implements CommandLineRunner {
//
//    private final CarsRepository carRepo;
//    private final TiresRepository tireRepo;
//    private final BrakesRepository brakeRepo;
//    private final RoadConditionsRepository roadRepo;
//    private final SimulationsService service;
//
//    public DataGenerator(CarsRepository carRepo,
//                         TiresRepository tireRepo,
//                         BrakesRepository brakeRepo,
//                         RoadConditionsRepository roadRepo,
//                         SimulationsService service) {
//        this.carRepo = carRepo;
//        this.tireRepo = tireRepo;
//        this.brakeRepo = brakeRepo;
//        this.roadRepo = roadRepo;
//        this.service = service;
//    }
//
//    @Override
//    public void run(String... args) {
//
//        Random r = new Random();
//
//        List<Cars> cars = carRepo.findAll();
//        List<Tires> tires = tireRepo.findAll();
//        List<Brakes> brakes = brakeRepo.findAll();
//        List<RoadConditions> roads = roadRepo.findAll();
//
//        for(int i = 0; i < 1000; i++) {
//
//            Cars car = cars.get(r.nextInt(cars.size()));
//            Tires tire = tires.get(r.nextInt(tires.size()));
//            Brakes brake = brakes.get(r.nextInt(brakes.size()));
//            RoadConditions road = roads.get(r.nextInt(roads.size()));
//
//            double speed = 30 + r.nextDouble() * 120; // 30-150
//            double tread = 2 + r.nextDouble() * 6;   // 2-8mm
//            double slope = -10 + r.nextDouble() * 20; // -10 to +10
//            boolean abs = r.nextBoolean();
//
//            DriverCondition driver = DriverCondition.values()[r.nextInt(DriverCondition.values().length)];
//
//            service.runSimulation(
//                    car.getId().longValue(),
//                    tire.getId().longValue(),
//                    brake.getId().longValue(),
//                    road.getId().longValue(),
//                    speed,
//                    tread,
//                    slope,
//                    abs,
//                    driver
//            );
//
//            System.out.println("Generated: " + i);
//        }
//    }
//}
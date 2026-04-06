package thurnos.brake_simulation.controller.api;

import org.springframework.web.bind.annotation.*;
import thurnos.brake_simulation.models.DriverCondition;
import thurnos.brake_simulation.models.Simulations;
import thurnos.brake_simulation.repository.SimulationsRepository;
import thurnos.brake_simulation.service.SimulationsService;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping("/api/simulations")
public class SimulationsController {

    private final SimulationsService service;
    private final SimulationsRepository repo;

    public SimulationsController(SimulationsService service,
                                 SimulationsRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    // 🔥 Run simulation
    @PostMapping("/execute")
    public Simulations runSimulation(
            @RequestParam Long carId,
            @RequestParam Long tireId,
            @RequestParam Long brakeId,
            @RequestParam Long roadId,
            @RequestParam double speed,
            @RequestParam double tread,
            @RequestParam double slope,
            @RequestParam boolean abs,
            @RequestParam DriverCondition driverCondition
    ) {
        return service.runSimulation(
                carId, tireId, brakeId, roadId,
                speed, tread, slope, abs, driverCondition
        );
    }

    // 🔹 Get all simulations
    @GetMapping
    public List<Simulations> getAll() {
        return repo.findAll();
    }

    // 🔥 FIX: Only match numeric IDs
    @GetMapping("/{id:\\d+}")
    public Simulations getById(@PathVariable Long id) {
        return repo.findById(BigInteger.valueOf(id)).orElseThrow();
    }
}
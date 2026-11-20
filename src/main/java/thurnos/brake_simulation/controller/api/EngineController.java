package thurnos.brake_simulation.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import thurnos.brake_simulation.engine.SimulationResult;
import thurnos.brake_simulation.models.DriverCondition;
import thurnos.brake_simulation.service.EngineSimulationService;

import java.util.Map;

/**
 * Time-stepped physics engine. Same parameter names as {@code /api/simulations/execute},
 * plus an optional obstacle distance. Nothing is saved.
 */
@RestController
@RequestMapping("/api/engine")
public class EngineController {

    private final EngineSimulationService service;

    public EngineController(EngineSimulationService service) {
        this.service = service;
    }

    @PostMapping("/simulate")
    public SimulationResult simulate(
            @RequestParam Long carId,
            @RequestParam Long tireId,
            @RequestParam Long brakeId,
            @RequestParam Long roadId,
            @RequestParam double speed,
            @RequestParam double tread,
            @RequestParam double slope,
            @RequestParam boolean abs,
            @RequestParam DriverCondition driverCondition,
            @RequestParam(required = false) Double obstacle,
            @RequestParam(defaultValue = "true") boolean trace
    ) {
        return service.run(carId, tireId, brakeId, roadId,
                speed, tread, slope, abs, driverCondition, obstacle, trace);
    }

    /** Out-of-range inputs (negative speed, bad obstacle distance, ...) come back as a 400 with the reason. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badInput(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}

package thurnos.brake_simulation.controller.api;

import org.springframework.web.bind.annotation.*;
import thurnos.brake_simulation.models.RoadConditions;
import thurnos.brake_simulation.repository.RoadConditionsRepository;

import java.util.List;

@RestController
@RequestMapping("/api/roads")
public class RoadConditionsController {

    private final RoadConditionsRepository repo;

    public RoadConditionsController(RoadConditionsRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<RoadConditions> getAll() {
        return repo.findAll();
    }

    @PostMapping
    public RoadConditions create(@RequestBody RoadConditions road) {
        return repo.save(road);
    }
}
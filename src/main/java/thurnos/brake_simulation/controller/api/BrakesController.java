package thurnos.brake_simulation.controller.api;

import org.springframework.web.bind.annotation.*;
import thurnos.brake_simulation.models.Brakes;
import thurnos.brake_simulation.repository.BrakesRepository;

import java.util.List;

@RestController
@RequestMapping("/api/brakes")
public class BrakesController {

    private final BrakesRepository repo;

    public BrakesController(BrakesRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Brakes> getAll() {
        return repo.findAll();
    }

    @PostMapping
    public Brakes create(@RequestBody Brakes brake) {
        return repo.save(brake);
    }
}
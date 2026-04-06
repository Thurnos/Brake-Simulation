package thurnos.brake_simulation.controller.api;

import org.springframework.web.bind.annotation.*;
import thurnos.brake_simulation.models.Tires;
import thurnos.brake_simulation.repository.TiresRepository;

import java.util.List;

@RestController
@RequestMapping("/api/tires")
public class TiresController {

    private final TiresRepository repo;

    public TiresController(TiresRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Tires> getAll() {
        return repo.findAll();
    }

    @PostMapping
    public Tires create(@RequestBody Tires tire) {
        return repo.save(tire);
    }
}

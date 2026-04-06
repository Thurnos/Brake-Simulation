package thurnos.brake_simulation.controller.api;

import org.springframework.web.bind.annotation.*;
import thurnos.brake_simulation.models.Cars;
import thurnos.brake_simulation.repository.CarsRepository;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarsController {

    private final CarsRepository repo;

    public CarsController(CarsRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Cars> getAll() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Cars getById(@PathVariable BigInteger id) {
        return repo.findById(id).orElseThrow();
    }

    @PostMapping
    public Cars create(@RequestBody Cars car) {
        return repo.save(car);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable BigInteger id) {
        repo.deleteById(id);
    }
}
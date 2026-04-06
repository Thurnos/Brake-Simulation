package thurnos.brake_simulation.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import thurnos.brake_simulation.repository.BrakesRepository;
import thurnos.brake_simulation.repository.CarsRepository;
import thurnos.brake_simulation.repository.RoadConditionsRepository;
import thurnos.brake_simulation.repository.TiresRepository;

@Controller
@RequestMapping("/web")
public class WebController {

    private final CarsRepository carRepo;
    private final TiresRepository tireRepo;
    private final BrakesRepository brakeRepo;
    private final RoadConditionsRepository roadRepo;

    public WebController(CarsRepository carRepo,
                         TiresRepository tireRepo,
                         BrakesRepository brakeRepo,
                         RoadConditionsRepository roadRepo) {
        this.carRepo = carRepo;
        this.tireRepo = tireRepo;
        this.brakeRepo = brakeRepo;
        this.roadRepo = roadRepo;
    }

    @GetMapping("/form")
    public String showForm(Model model) {
        model.addAttribute("cars", carRepo.findAll());
        model.addAttribute("tires", tireRepo.findAll());
        model.addAttribute("brakes", brakeRepo.findAll());
        model.addAttribute("roads", roadRepo.findAll());
        return "simulation-form";
    }
}
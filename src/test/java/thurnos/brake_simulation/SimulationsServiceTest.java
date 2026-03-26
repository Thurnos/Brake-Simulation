package thurnos.brake_simulation;

import org.junit.jupiter.api.Test;
import thurnos.brake_simulation.models.*;
import thurnos.brake_simulation.repository.*;
import thurnos.brake_simulation.service.BrakingPhysicsEngine;
import thurnos.brake_simulation.service.SimulationsService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimulationsServiceTest {

    @Test
     void shouldRunSimulationSuccessfully() {

        CarsRepository carRepo = mock(CarsRepository.class);
        TiresRepository tireRepo = mock(TiresRepository.class);
        BrakesRepository brakeRepo = mock(BrakesRepository.class);
        RoadConditionsRepository roadRepo = mock(RoadConditionsRepository.class);
        SimulationsRepository simRepo = mock(SimulationsRepository.class);

        BrakingPhysicsEngine engine = new BrakingPhysicsEngine();

        SimulationsService service = new SimulationsService(
                carRepo, tireRepo, brakeRepo, roadRepo, simRepo, engine
        );

        // Mock data
        Cars car = new Cars();
        car.setWeightKg(1400.0);

        Tires tire = new Tires();
        tire.setBaseGrip(1.0);

        Brakes brake = new Brakes();
        brake.setEfficiency(1.0);

        RoadConditions road = new RoadConditions();
        road.setGripModifier(1.0);

        when(carRepo.findById(any())).thenReturn(java.util.Optional.of(car));
        when(tireRepo.findById(any())).thenReturn(java.util.Optional.of(tire));
        when(brakeRepo.findById(any())).thenReturn(java.util.Optional.of(brake));
        when(roadRepo.findById(any())).thenReturn(java.util.Optional.of(road));
        when(simRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Simulations result = service.runSimulation(
                1L, 1L, 1L, 1L,
                100,
                7,
                0,
                true,
                DriverCondition.NORMAL
        );

        assertNotNull(result);
        assertTrue(result.getTotalDistance() > 0);

        verify(simRepo, times(1)).save(any());
    }
}
package thurnos.brake_simulation;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import thurnos.brake_simulation.engine.SimulationResult;
import thurnos.brake_simulation.models.*;
import thurnos.brake_simulation.repository.*;
import thurnos.brake_simulation.service.EngineSimulationService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineSimulationServiceTest {

    private final CarsRepository carRepo = mock(CarsRepository.class);
    private final TiresRepository tireRepo = mock(TiresRepository.class);
    private final BrakesRepository brakeRepo = mock(BrakesRepository.class);
    private final RoadConditionsRepository roadRepo = mock(RoadConditionsRepository.class);
    private final EngineSimulationService service =
            new EngineSimulationService(carRepo, tireRepo, brakeRepo, roadRepo);

    private void givenRows() {
        Cars car = new Cars();
        car.setWeightKg(1400.0);
        Tires tire = new Tires();
        tire.setBaseGrip(1.1);
        Brakes brake = new Brakes();
        brake.setEfficiency(1.0);
        RoadConditions road = new RoadConditions();
        road.setGripModifier(1.0);

        when(carRepo.findById(any())).thenReturn(Optional.of(car));
        when(tireRepo.findById(any())).thenReturn(Optional.of(tire));
        when(brakeRepo.findById(any())).thenReturn(Optional.of(brake));
        when(roadRepo.findById(any())).thenReturn(Optional.of(road));
    }

    @Test
    void returnsATraceForExistingRows() {
        givenRows();

        SimulationResult result = service.run(1L, 1L, 1L, 1L,
                100, 7, 0, true, DriverCondition.NORMAL, null, null, null, true);

        assertTrue(result.stopped());
        assertTrue(result.stopDistanceM() > 0);
        assertFalse(result.trace().isEmpty());
    }

    @Test
    void canLeaveTheTraceOut() {
        givenRows();

        SimulationResult result = service.run(1L, 1L, 1L, 1L,
                100, 7, 0, true, DriverCondition.NORMAL, null, null, null, false);

        assertTrue(result.stopped());
        assertTrue(result.trace().isEmpty());
    }

    @Test
    void missingRowIsReportedAsNotFound() {
        when(carRepo.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.run(99L, 1L, 1L, 1L,
                100, 7, 0, true, DriverCondition.NORMAL, null, null, null, true));
    }

    @Test
    void waterOverrideChangesTheResultAndReportsHydroplaningOnset() {
        givenRows();

        SimulationResult dry = service.run(1L, 1L, 1L, 1L,
                130, 1.6, 0, true, DriverCondition.NORMAL, 0.0, null, null, false);
        SimulationResult flooded = service.run(1L, 1L, 1L, 1L,
                130, 1.6, 0, true, DriverCondition.NORMAL, 6.0, null, null, false);

        assertNull(dry.hydroplaningOnsetKmh());
        assertNotNull(flooded.hydroplaningOnsetKmh());
        assertTrue(flooded.stopDistanceM() > dry.stopDistanceM());
    }

    @Test
    void repeatedStopsHeatTheBrakes() {
        givenRows();

        var runs = service.runRepeatedStops(1L, 1L, 1L, 1L,
                120, 7, 0, true, DriverCondition.NORMAL, null, null, 4, 10, 50);

        assertEquals(4, runs.size());
        assertTrue(runs.get(3).thermal().peakFrontC() > runs.get(0).thermal().peakFrontC());
    }

    @Test
    void hotBrakesAtTheStartLengthenTheStop() {
        givenRows();

        SimulationResult cold = service.run(1L, 1L, 1L, 1L,
                100, 7, 0, true, DriverCondition.NORMAL, null, 20.0, null, false);
        SimulationResult hot = service.run(1L, 1L, 1L, 1L,
                100, 7, 0, true, DriverCondition.NORMAL, null, 750.0, null, false);

        assertTrue(hot.stopDistanceM() > cold.stopDistanceM());
    }
}

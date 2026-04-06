package thurnos.brake_simulation;

import org.junit.jupiter.api.Test;
import thurnos.brake_simulation.models.Simulations;
import thurnos.brake_simulation.service.BrakingPhysicsEngine;

import static org.junit.jupiter.api.Assertions.*;

public class BrakingPhysicsEngineTest {
    private final BrakingPhysicsEngine engine = new BrakingPhysicsEngine();

    @Test
    void shouldCalculateStoppingCorrectlyFlatRoadWithABS() {

        Simulations result = engine.calculateFullStop(
                100,    // speed km/h
                1.0,    // tire grip
                7.0,    // tread depth
                1.0,    // road (dry)
                1.0,    // brake efficiency
                1400,   // weight
                1.2,    // reaction time
                true,   // ABS
                0       // slope
        );

        assertNotNull(result);

        assertTrue(result.getReactionDistance() > 0);
        assertTrue(result.getBrakingDistance() > 0);
        assertTrue(result.getTotalDistance() > result.getReactionDistance());

        // 💡 important logical check
        assertEquals(
                result.getReactionDistance() + result.getBrakingDistance(),
                result.getTotalDistance(),
                0.01
        );
    }
    @Test
    void shouldIncreaseDistanceOnDownhill() {

        Simulations flat = engine.calculateFullStop(
                100, 1.0, 7, 1.0, 1.0, 1400, 1.2, true, 0
        );

        Simulations downhill = engine.calculateFullStop(
                100, 1.0, 7, 1.0, 1.0, 1400, 1.2, true, -10
        );

        assertTrue(downhill.getTotalDistance() < flat.getTotalDistance());
    }
    @Test
    void shouldBrakeBetterWithABS() {

        Simulations withABS = engine.calculateFullStop(
                100, 1.0, 7, 0.7, 1.0, 1400, 1.2, true, 0
        );

        Simulations withoutABS = engine.calculateFullStop(
                100, 1.0, 7, 0.7, 1.0, 1400, 1.2, false, 0
        );

        assertTrue(withABS.getTotalDistance() < withoutABS.getTotalDistance());
    }
    @Test
    void shouldFailOnExtremeDownhill() {

        Simulations result = engine.calculateFullStop(
                120, 0.5, 2, 0.4, 0.7, 1800, 2.0, false, -30
        );

        assertEquals(Double.MAX_VALUE, result.getTotalDistance());
    }
    @Test
    void shouldFailOnHeavierCarStoppageDistance(){
        Simulations lighter = engine.calculateFullStop(
                100, 1.0, 7, 0.7, 1.0, 1400, 1.2, true, 0
        );
        Simulations heavier = engine.calculateFullStop(
                100, 1.0, 7, 0.7, 1.0, 2500, 1.2, true, 0
        );

        assertTrue(lighter.getTotalDistance() < heavier.getTotalDistance());
    }
    @Test
    void lighterCarNoABSHasBetterStoppageThanHeavierWithABS(){
        Simulations lighter = engine.calculateFullStop(
                100, 1.0, 7, 0.7, 1.0, 1400, 1.2, false, 0
        );
        Simulations heavier = engine.calculateFullStop(
                100, 1.0, 7, 0.7, 1.0, 2500, 1.2, true, 0
        );
        assertTrue(lighter.getTotalDistance() > heavier.getTotalDistance());
    }
    @Test
    void shouldPassOnPossitiveTotalDistance() {

        Simulations result = engine.calculateFullStop(
                1, 0.5, 2, 0.4, 0.7, 1800, 2.0, false, -30
        );

        assertTrue(result.getTotalDistance()>0);
    }

}


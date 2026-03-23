package thurnos.brake_simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thurnos.brake_simulation.models.RoadConditions;

import java.math.BigInteger;

public interface RoadConditionsRepository extends JpaRepository<RoadConditions, BigInteger> {
}

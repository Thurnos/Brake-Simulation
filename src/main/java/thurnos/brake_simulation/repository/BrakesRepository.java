package thurnos.brake_simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thurnos.brake_simulation.models.Brakes;

import java.math.BigInteger;

public interface BrakesRepository extends JpaRepository<Brakes, BigInteger> {
}

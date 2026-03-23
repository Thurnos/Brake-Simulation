package thurnos.brake_simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thurnos.brake_simulation.models.Simulations;

import java.math.BigInteger;

public interface SimulationsRepository extends JpaRepository<Simulations, BigInteger> {
}

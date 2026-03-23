package thurnos.brake_simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thurnos.brake_simulation.models.Cars;

import java.math.BigInteger;

public interface CarsRepository extends JpaRepository<Cars, BigInteger> {
}

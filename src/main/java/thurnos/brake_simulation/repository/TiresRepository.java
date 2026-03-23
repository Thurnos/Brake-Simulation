package thurnos.brake_simulation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thurnos.brake_simulation.models.Tires;

import java.math.BigInteger;

public interface TiresRepository extends JpaRepository<Tires,BigInteger> {
}

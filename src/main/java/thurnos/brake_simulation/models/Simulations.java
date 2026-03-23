package thurnos.brake_simulation.models;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulations")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Simulations {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "car_id")
    private Cars cars;

    @ManyToOne
    @JoinColumn(name = "tire_id")
    private Tires tires;

    @ManyToOne
    @JoinColumn(name = "brake_id")
    private Brakes brakes;

    @ManyToOne
    @JoinColumn(name = "road_condition_id")
    private RoadConditions roadConditions;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "tire_tread_mm")
    private Double tireTreadMm;

    @Column(name = "stopping_distance")
    private Double stoppingDistance;

    @Column(name = "stopping_time")
    private Double stoppingTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
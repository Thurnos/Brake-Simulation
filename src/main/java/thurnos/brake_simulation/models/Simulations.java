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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "reaction_time")
    private Double reactionTime;

    @Column(name = "has_abs")
    private Boolean hasAbs;

    @Column(name = "total_distance")
    private Double totalDistance;
    @Column(name = "total_time")
    private Double totalTime;

    @Column(name = "slope_percent")
    private Double slopePercent;

    @Column(name = "reaction_distance")
    private Double reactionDistance;

    @Column(name = "braking_time")
    private Double brakingTime;

    @Column(name = "braking_distance")
    private Double brakingDistance;


    public void setCar(Cars car) {
        this.cars = car;
    }

    public void setTire(Tires tire) {
        this.tires = tire;
    }

    public void setRoadCondition(RoadConditions road) {
        this.roadConditions = road;
    }

    public void setBrake(Brakes brake) {
        this.brakes = brake;
    }
}
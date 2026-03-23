package thurnos.brake_simulation.models;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;

@Entity
@Table(name = "cars")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Cars {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private BigInteger id;

    @Column(name = "brand",nullable = false,length = 255)
    private String brand;
    @Column(name = "model",nullable = false,length = 255)
    private String model;

    @Column(name = "weight_kg")
    private Double weightKg;
    @Column(name = "drive_train",nullable = false,length = 255)
    private String driveTrain;

    @Column(name = "year")
    private Integer year;
}

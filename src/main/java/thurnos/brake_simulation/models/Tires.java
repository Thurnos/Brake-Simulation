package thurnos.brake_simulation.models;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;

@Entity
@Table(name = "tires")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Tires {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private BigInteger id;

    @Column(name = "brand",nullable = false,unique = true)
    private String brand;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "base_grip")
    private Double baseGrip;

    @Column(name = "tire_type",nullable = false,length = 255)
    private String tireType;
}

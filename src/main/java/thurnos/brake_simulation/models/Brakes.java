package thurnos.brake_simulation.models;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;

@Entity
@Table(name = "brakes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Brakes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private BigInteger id;

    @Column(name = "brand",nullable = false,length = 255)
    private String brand;

    @Column(name="model",nullable = false,length = 255)
    private String model;

    @Column(name = "efficiency")
    private Double efficiency;

    @Column(name="type",nullable = false,length = 255)
    private String type;
}

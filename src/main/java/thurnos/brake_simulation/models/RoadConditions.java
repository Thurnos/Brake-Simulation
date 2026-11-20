package thurnos.brake_simulation.models;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;

@Entity
@Table(name = "road_conditions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RoadConditions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private BigInteger id;

    @Column(name = "name",nullable = false,length = 255)
    private String name;

    @Column(name = "grip_modifier")
    private Double gripModifier;

    /**
     * Depth of the water film on this road in millimetres. Null means "not stated": the engine then assumes a
     * typical film for wet-grip roads and none otherwise. Added column; ddl-auto=update creates it.
     */
    @Column(name = "water_depth_mm")
    private Double waterDepthMm;
}

package thurnos.brake_simulation.engine;

/**
 * Broad road-surface families. Each has its own slip-curve shape: how quickly friction rises with wheel slip,
 * where it peaks, and how far it falls when the wheel locks. How much friction there is overall comes from
 * the tire and road tables, not from here.
 */
public enum SurfaceKind {

    //            B     C     E
    DRY(10.0, 1.9, 0.97),
    WET(12.0, 2.3, 1.00),
    SNOW(5.0, 2.0, 1.00),
    ICE(4.0, 2.0, 1.00);

    final double stiffness;
    final double shape;
    final double curvature;

    SurfaceKind(double stiffness, double shape, double curvature) {
        this.stiffness = stiffness;
        this.shape = shape;
        this.curvature = curvature;
    }

    /** Picks a family from the road table's grip modifier (1.0 dry, 0.7 wet, 0.4 snow, 0.2 ice, 0.6 gravel). */
    public static SurfaceKind fromGripModifier(double modifier) {
        if (modifier >= 0.9) return DRY;
        if (modifier >= 0.55) return WET;
        if (modifier >= 0.3) return SNOW;
        return ICE;
    }
}

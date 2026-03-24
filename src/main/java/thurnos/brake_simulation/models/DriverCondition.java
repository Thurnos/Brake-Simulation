package thurnos.brake_simulation.models;

public enum DriverCondition {

    ALERT(0.9),
    NORMAL(1.3),
    TIRED(2.0);

    private final double reactionTime;


    DriverCondition(double reactionTime){
        this.reactionTime = reactionTime;
    }

    public double getReactionTime(){
        return reactionTime;
    }
}

package dk.dtu.routeplanner.core.model;

public class SingleSourceResult {
    private final int settledCounter;
    private final int relaxedCounter;
    private final double[] dist;


    public SingleSourceResult(int settledCounter, int relaxedCounter, double[] dist){
        this.settledCounter = settledCounter;
        this.relaxedCounter = relaxedCounter;
        this.dist = dist;
    }

    public int settledCounter() {
        return settledCounter;
    }

    public int relaxedCounter() {
        return relaxedCounter;
    }

    public double distance(int node) {
        return dist[node];
    }

}

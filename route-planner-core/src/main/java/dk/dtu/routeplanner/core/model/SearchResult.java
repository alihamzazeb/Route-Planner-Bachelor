package dk.dtu.routeplanner.core.model;

public class SearchResult {
    private final double distance;
    private final int[] path;
    private final int settledCounter;
    private final int relaxedCounter;

    public SearchResult(double distance, int[] path, int settledCounter, int relaxedCounter) {
        this.distance = distance;
        this.path = path;
        this.settledCounter = settledCounter;
        this.relaxedCounter = relaxedCounter;
    }

    public double distance() {
        return distance;
    }

    public int[] path() {
        return path.clone();
    }

    public int settledCounter() {
        return settledCounter;
    }

    public int relaxedCounter() {
        return relaxedCounter;
    }

}

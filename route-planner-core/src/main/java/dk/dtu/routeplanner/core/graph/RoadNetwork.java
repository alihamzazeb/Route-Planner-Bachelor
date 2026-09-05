package dk.dtu.routeplanner.core.graph;

public class RoadNetwork {
    private final Graph forward;
    private final Graph reverse;

    public RoadNetwork(Graph forward, Graph reverse) {
        this.forward = forward;
        this.reverse = reverse;
    }

    public Graph forward() { return forward; }
    public Graph reverse() { return reverse; }
}
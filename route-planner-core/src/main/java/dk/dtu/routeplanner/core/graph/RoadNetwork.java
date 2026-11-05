package dk.dtu.routeplanner.core.graph;

import java.util.Objects;

public class RoadNetwork {
    private final Graph forward;
    private final Graph reverse;

    // reverse must contain the same edges with their directions swapped.
    public RoadNetwork(Graph forward, Graph reverse) {
        this.forward = Objects.requireNonNull(forward);
        this.reverse = Objects.requireNonNull(reverse);

        if (forward.nodeCount() != reverse.nodeCount()) {
            throw new IllegalArgumentException(
                "Forward and reverse graphs must have the same node count"
            );
        }
    }

    public Graph forward() { return forward; }
    public Graph reverse() { return reverse; }
}
package dk.dtu.routeplanner.core.graph;

// Node IDs start at 0. Edge weights must be finite and nonnegative.
public interface Graph {
    int nodeCount();
    int edgeStart(int node);
    int edgeEnd(int node);
    int edgeTarget(int i);
    double edgeWeight(int i);
}
package dk.dtu.routeplanner.core.graph;

public interface Graph {
    int nodeCount();
    int edgeStart(int node);
    int edgeEnd(int node);
    int edgeTarget(int i);
    double edgeWeight(int i);
}
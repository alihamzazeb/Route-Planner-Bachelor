package dk.dtu.routeplanner.core.algorithms;

// The estimate must not overestimate the remaining distance and must be 0 at the target.
public interface Heuristic {

    double estimate(int node, int target);

}
package dk.dtu.routeplanner.core.algorithms;

// The estimate must be 0 at the target and must not overestimate the remaining distance.
// AStar does not reopen settled nodes, so it also needs estimate(u) <= weight(u, v) + estimate(v)
// on every edge that can still reach the target.
public interface Heuristic {

    double estimate(int node, int target);

}
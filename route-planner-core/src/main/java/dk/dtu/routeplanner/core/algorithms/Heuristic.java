package dk.dtu.routeplanner.core.algorithms;

public interface Heuristic {

    double estimate(int node, int target);

}
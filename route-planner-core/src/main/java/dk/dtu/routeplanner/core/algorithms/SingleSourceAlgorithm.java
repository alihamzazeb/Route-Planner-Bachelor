package dk.dtu.routeplanner.core.algorithms;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

public interface SingleSourceAlgorithm {
    SingleSourceResult computeShortestPaths(Graph graph, int source);

}
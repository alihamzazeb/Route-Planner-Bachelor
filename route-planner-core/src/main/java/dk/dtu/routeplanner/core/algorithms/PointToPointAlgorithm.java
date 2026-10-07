package dk.dtu.routeplanner.core.algorithms;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;

public interface PointToPointAlgorithm {
    SearchResult findShortestPath(Graph graph, int source, int target);

}
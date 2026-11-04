package dk.dtu.routeplanner.benchmark.expb;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.Graph;

final class RouteChecks {
    private RouteChecks() {}

    static double[] referenceDistances(Graph graph, int[][] pairs) {
        Dijkstra dijkstra = new Dijkstra();
        double[] distances = new double[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            distances[i] = dijkstra.findShortestPath(graph, pairs[i][0], pairs[i][1]).distance();
            if (!Double.isFinite(distances[i])) {
                throw new IllegalStateException("Unreachable pair at index " + i);
            }
        }
        return distances;
    }
}

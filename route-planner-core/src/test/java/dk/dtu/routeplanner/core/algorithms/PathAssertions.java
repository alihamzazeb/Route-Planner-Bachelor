package dk.dtu.routeplanner.core.algorithms;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PathAssertions {
    private PathAssertions() {}

    static void assertValidPath(Graph graph, int source, int target, SearchResult result) {
        String pair = source + " -> " + target;
        int[] path = result.path();
        if (result.distance() == Double.POSITIVE_INFINITY) {
            assertEquals(0, path.length, pair);
            return;
        }
        assertTrue(Double.isFinite(result.distance()), pair);
        assertTrue(path.length > 0 && path.length <= graph.nodeCount(), pair);
        assertEquals(source, path[0], pair);
        assertEquals(target, path[path.length - 1], pair);
        boolean[] seen = new boolean[graph.nodeCount()];
        for (int node : path) {
            assertTrue(node >= 0 && node < graph.nodeCount(), pair + ": invalid node " + node);
            assertTrue(!seen[node], pair + ": repeated node " + node);
            seen[node] = true;
        }
        double cost = 0;
        for (int step = 1; step < path.length; step++) {
            int from = path[step - 1];
            int to = path[step];
            double weight = Double.POSITIVE_INFINITY;
            for (int edge = graph.edgeStart(from); edge < graph.edgeEnd(from); edge++) {
                if (graph.edgeTarget(edge) == to) {
                    weight = Math.min(weight, graph.edgeWeight(edge));
                }
            }
            assertTrue(Double.isFinite(weight), pair + ": missing edge " + from + " -> " + to);
            cost += weight;
        }
        assertEquals(result.distance(), cost, 1e-9, pair);
    }
}

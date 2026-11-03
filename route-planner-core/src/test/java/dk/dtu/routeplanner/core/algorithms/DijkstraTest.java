package dk.dtu.routeplanner.core.algorithms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

class DijkstraTest {

    @Test
    void singleSourceAndPointToPointAgreeOnDistancesAndPaths() {
        Graph graph = new CsrGraph(new int[]{0, 3, 4, 5, 6},
                new int[]{1, 2, 3, 3, 0, 1}, new double[]{10, 40, 60, 30, 20, 50});
        Dijkstra algorithm = new Dijkstra();
        SingleSourceResult distances = algorithm.computeShortestPaths(graph, 0);
        double[] expected = {0, 10, 40, 40};
        int[][] paths = {{0}, {0, 1}, {0, 2}, {0, 1, 3}};

        for (int target = 0; target < graph.nodeCount(); target++) {
            SearchResult result = algorithm.findShortestPath(graph, 0, target);
            assertEquals(expected[target], distances.distance(target), "target=" + target);
            assertEquals(distances.distance(target), result.distance(), "target=" + target);
            assertArrayEquals(paths[target], result.path(), "target=" + target);
        }
    }

    @Test
    void unreachableNodeHasInfiniteDistanceAndNoPath() {
        Graph graph = new CsrGraph(new int[]{0, 1, 1, 1}, new int[]{1}, new double[]{10});
        Dijkstra algorithm = new Dijkstra();
        SingleSourceResult distances = algorithm.computeShortestPaths(graph, 0);

        assertEquals(0, distances.distance(0));
        assertEquals(10, distances.distance(1));
        assertEquals(Double.POSITIVE_INFINITY, distances.distance(2));
        SearchResult result = algorithm.findShortestPath(graph, 0, 2);
        assertEquals(Double.POSITIVE_INFINITY, result.distance());
        assertEquals(0, result.path().length);
        assertEquals(1, result.relaxedCounter());
    }

    @Test
    void zeroWeightAndParallelEdgesRespectDirection() {
        Graph graph = new CsrGraph(new int[]{0, 3, 4, 4},
                new int[]{1, 1, 2, 2}, new double[]{9, 0, 8, 2});
        Dijkstra algorithm = new Dijkstra();
        SingleSourceResult distances = algorithm.computeShortestPaths(graph, 0);
        SearchResult route = algorithm.findShortestPath(graph, 0, 2);

        assertEquals(0, distances.distance(1));
        assertEquals(2, distances.distance(2));
        assertEquals(distances.distance(2), route.distance());
        assertArrayEquals(new int[]{0, 1, 2}, route.path());
        SearchResult reverse = algorithm.findShortestPath(graph, 2, 0);
        assertEquals(Double.POSITIVE_INFINITY, reverse.distance());
        assertEquals(0, reverse.path().length);
    }
}

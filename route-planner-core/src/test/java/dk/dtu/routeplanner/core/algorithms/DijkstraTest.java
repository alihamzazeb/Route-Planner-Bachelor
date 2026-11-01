package dk.dtu.routeplanner.core.algorithms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

public class DijkstraTest {

    @Test
    void distancesFromNodeZero() {
        int[] offsets = {0, 3, 4, 5, 6};
        int[] targets = {1, 2, 3, 3, 0, 1};
        double[] weights = {10, 40, 60, 30, 20, 50};
        Graph graph = new CsrGraph(offsets, targets, weights);

        SingleSourceResult dist = new Dijkstra().computeShortestPaths(graph, 0);

        assertEquals(0, dist.distance(0));
        assertEquals(10, dist.distance(1));
        assertEquals(40, dist.distance(2));
        assertEquals(40, dist.distance(3));
    }


    @Test
    void unreachableNodeStaysInfinity() {
        int[] offsets = {0, 1, 1, 1};
        int[] targets = {1};
        double[] weights = {10};
        Graph graph = new CsrGraph(offsets, targets, weights);

        SingleSourceResult dist = new Dijkstra().computeShortestPaths(graph, 0);

        assertEquals(0, dist.distance(0));
        assertEquals(10, dist.distance(1));
        assertEquals(Double.POSITIVE_INFINITY, dist.distance(2));
    }

    @Test
    void targetPath() {
        int[] offsets = {0, 3, 4, 5, 6};
        int[] targets = {1, 2, 3, 3, 0, 1};
        double[] weights = {10, 40, 60, 30, 20, 50};
        Graph graph = new CsrGraph(offsets, targets, weights);

        SearchResult result = new Dijkstra().findShortestPath(graph, 0, 3);
        int[] EXPECTED_PATH = {0, 1, 3};
        double EXPECTED_DISTANCE = 40;
        assertEquals(EXPECTED_DISTANCE, result.distance());
        assertArrayEquals(EXPECTED_PATH, result.path());
    }


    @Test
    void unreachableTarget() {
        int[] offsets = {0, 1, 1, 1};
        int[] targets = {1};
        double[] weights = {10};
        Graph graph = new CsrGraph(offsets, targets, weights);

        SearchResult result = new Dijkstra().findShortestPath(graph, 0, 2);

        assertEquals(Double.POSITIVE_INFINITY, result.distance());
        assertEquals(0, result.path().length);
        assertEquals(1, result.relaxedCounter());
    }


}
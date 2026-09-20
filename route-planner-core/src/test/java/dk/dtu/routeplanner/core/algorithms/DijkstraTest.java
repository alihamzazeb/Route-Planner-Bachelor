package dk.dtu.routeplanner.core.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;

public class DijkstraTest {

    @Test
    void distancesFromNodeZero() {
        int[] offsets = {0, 3, 4, 5, 6};
        int[] targets = {1, 2, 3, 3, 0, 1};
        double[] weights = {10, 40, 60, 30, 20, 50};
        Graph graph = new CsrGraph(offsets, targets, weights);

        double[] dist = new Dijkstra().computeShortestPaths(graph, 0);

        assertEquals(0, dist[0]);
        assertEquals(10, dist[1]);
        assertEquals(40, dist[2]);
        assertEquals(40, dist[3]);
    }
}
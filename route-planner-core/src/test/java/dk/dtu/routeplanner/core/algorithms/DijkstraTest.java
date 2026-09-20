package dk.dtu.routeplanner.core.algorithms;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

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


    @Test
    void unreachableNodeStaysInfinity() {
        int[] offsets = {0, 1, 1, 1};
        int[] targets = {1};
        double[] weights = {10};
        Graph graph = new CsrGraph(offsets, targets, weights);

        double[] dist = new Dijkstra().computeShortestPaths(graph, 0);

        assertEquals(0, dist[0]);
        assertEquals(10, dist[1]);
        assertEquals(Double.POSITIVE_INFINITY, dist[2]);
    }

    @Test
    void noOutgoingEdgeFromSource() {
        int[] offsets = {0, 0, 0, 0};
        int[] targets = {};
        double[] weights = {};
        Graph graph = new CsrGraph(offsets, targets, weights);

        double[] dist = new Dijkstra().computeShortestPaths(graph, 0);

        assertEquals(0, dist[0]);
        assertEquals(Double.POSITIVE_INFINITY, dist[1]);
        assertEquals(Double.POSITIVE_INFINITY, dist[2]);
    }


    @Test
    void runsOnNewYork() throws IOException {
        RoadNetwork network = new DimacsLoader().load(Path.of("../data/USA-road-d.NY.gr"));

        long start = System.nanoTime();
        double[] dist = new Dijkstra().computeShortestPaths(network.forward(), 0);
        long elapsed = System.nanoTime() - start;

        int unreachable = 0;
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] == Double.POSITIVE_INFINITY) {
                unreachable++;
            }
        }
        System.out.println("unreachable: " + unreachable);

        assertEquals(0.0, dist[0]);
        assertEquals(0, unreachable); // to see if it covered all of the nodes
        System.out.println("time: " + elapsed / 1_000_000 + " ms");
    }
}
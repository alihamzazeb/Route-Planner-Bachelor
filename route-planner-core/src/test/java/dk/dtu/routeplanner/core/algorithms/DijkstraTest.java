package dk.dtu.routeplanner.core.algorithms;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
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
    void noOutgoingEdgeFromSource() {
        int[] offsets = {0, 0, 0, 0};
        int[] targets = {};
        double[] weights = {};
        Graph graph = new CsrGraph(offsets, targets, weights);

        SingleSourceResult dist = new Dijkstra().computeShortestPaths(graph, 0);

        assertEquals(0, dist.distance(0));
        assertEquals(Double.POSITIVE_INFINITY, dist.distance(1));
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
    void runsOnNewYork() throws IOException {
        RoadNetwork network = new DimacsLoader().load(Path.of("../data/USA-road-d.NY.gr"));

        long start = System.nanoTime();
        SingleSourceResult dist = new Dijkstra().computeShortestPaths(network.forward(), 0);
        long elapsed = System.nanoTime() - start;

        int unreachable = 0;
        for (int i = 0; i < network.forward().nodeCount(); i++) {
            if (dist.distance(i) == Double.POSITIVE_INFINITY) {
                unreachable++;
            }
        }
        System.out.println("unreachable: " + unreachable);

        assertEquals(0.0, dist.distance(0));
        assertEquals(0, unreachable); // to see if it covered all of the nodes
        System.out.println("time: " + elapsed / 1_000_000 + " ms");
    }


    @Test
    void pointToPointAgreesWithSingleSource() throws IOException {
        RoadNetwork network = new DimacsLoader().load(Path.of("../data/USA-road-d.NY.gr"));
        Graph graph = network.forward();

        SingleSourceResult all = new Dijkstra().computeShortestPaths(graph, 0);

        int targetA = 43;
        int targetB = 8765;
        int targetC = 23456;

        SearchResult resultA = new Dijkstra().findShortestPath(graph, 0, targetA);
        SearchResult resultB = new Dijkstra().findShortestPath(graph, 0, targetB);
        SearchResult resultC = new Dijkstra().findShortestPath(graph, 0, targetC);

        assertEquals(all.distance(targetA), resultA.distance());
        assertEquals(all.distance(targetB), resultB.distance());
        assertEquals(all.distance(targetC), resultC.distance());
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
    }


}
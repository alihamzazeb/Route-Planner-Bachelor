package dk.dtu.routeplanner.core.algorithms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;

class BidirectionalDijkstraTest {

    private RoadNetwork example() {
        Graph forward = new CsrGraph(
                new int[]{0, 2, 4, 5, 6, 6},
                new int[]{1, 2, 2, 3, 3, 4},
                new double[]{2, 8, 2, 9, 2, 1});
        Graph reverse = new CsrGraph(
                new int[]{0, 0, 1, 3, 5, 6},
                new int[]{0, 0, 1, 1, 2, 3},
                new double[]{2, 8, 2, 9, 2, 1});
        return new RoadNetwork(forward, reverse);
    }

    @Test
    void findsShortestPathAndReconstructsIt() {
        RoadNetwork network = example();
        SearchResult result = new BidirectionalDijkstra(network)
                .findShortestPath(network.forward(), 0, 4);

        assertEquals(7.0, result.distance());
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, result.path());
    }

    @Test
    void respectsEdgeDirection() {
        RoadNetwork network = example();
        SearchResult result = new BidirectionalDijkstra(network)
                .findShortestPath(network.forward(), 4, 0);

        assertEquals(Double.POSITIVE_INFINITY, result.distance());
        assertEquals(0, result.path().length);
    }

    @Test
    void sourceEqualsTarget() {
        RoadNetwork network = example();
        SearchResult result = new BidirectionalDijkstra(network)
                .findShortestPath(network.forward(), 2, 2);

        assertEquals(0.0, result.distance());
        assertArrayEquals(new int[]{2}, result.path());
    }

    @Test
    void randomDirectedGraphsAgreeWithDijkstra() {
        Random random = new Random(42);
        for (int graphNumber = 0; graphNumber < 30; graphNumber++) {
            int n = 8;
            double[][] weights = new double[n][n];
            for (int u = 0; u < n; u++) {
                Arrays.fill(weights[u], Double.POSITIVE_INFINITY);
                for (int v = 0; v < n; v++) {
                    if (u != v && random.nextDouble() < 0.35) {
                        weights[u][v] = random.nextInt(11);
                    }
                }
            }
            RoadNetwork network = new RoadNetwork(fromMatrix(weights, false), fromMatrix(weights, true));
            BidirectionalDijkstra bidirectional = new BidirectionalDijkstra(network);
            Dijkstra reference = new Dijkstra();
            for (int source = 0; source < n; source++) {
                for (int target = 0; target < n; target++) {
                    SearchResult expected = reference.findShortestPath(network.forward(), source, target);
                    SearchResult actual = bidirectional.findShortestPath(network.forward(), source, target);
                    assertEquals(expected.distance(), actual.distance(), 1e-10,
                            "Graph " + graphNumber + ", " + source + " -> " + target);
                    if (Double.isFinite(actual.distance())) {
                        assertEquals(source, actual.path()[0]);
                        assertEquals(target, actual.path()[actual.path().length - 1]);
                        double total = 0;
                        for (int i = 1; i < actual.path().length; i++) {
                            int from = actual.path()[i - 1];
                            int to = actual.path()[i];
                            assertTrue(Double.isFinite(weights[from][to]));
                            total += weights[from][to];
                        }
                        assertEquals(actual.distance(), total, 1e-10);
                    } else {
                        assertEquals(0, actual.path().length);
                    }
                }
            }
        }
    }

    @Test
    void agreesWithDijkstraOnNewYork() throws IOException {
        assumeTrue(Files.isRegularFile(Path.of("../data/USA-road-d.NY.gr")), "New York graph not available");
        RoadNetwork network = new DimacsLoader().load(Path.of("../data/USA-road-d.NY.gr"));
        Graph graph = network.forward();
        BidirectionalDijkstra bidirectional = new BidirectionalDijkstra(network);
        Dijkstra reference = new Dijkstra();
        Random random = new Random(1234);
        long dijkstraSettled = 0;
        long bidirectionalSettled = 0;

        for (int i = 0; i < 20; i++) {
            int source = random.nextInt(graph.nodeCount());
            int target = random.nextInt(graph.nodeCount());
            SearchResult expected = reference.findShortestPath(graph, source, target);
            SearchResult actual = bidirectional.findShortestPath(graph, source, target);

            assertEquals(expected.distance(), actual.distance(), 1e-9,
                    "from " + source + " to " + target);

            if (Double.isFinite(actual.distance())) {
                int[] path = actual.path();
                assertEquals(source, path[0]);
                assertEquals(target, path[path.length - 1]);
                assertEquals(actual.distance(), pathLength(graph, path), 1e-9,
                        "path from " + source + " to " + target);
            } else {
                assertEquals(0, actual.path().length);
            }

            dijkstraSettled += expected.settledCounter();
            bidirectionalSettled += actual.settledCounter();
        }

        assertTrue(bidirectionalSettled < dijkstraSettled,
                "bidirectional settled " + bidirectionalSettled + " nodes, Dijkstra " + dijkstraSettled);
    }

    @Test
    void rejectsInvalidNode() {
        RoadNetwork network = example();
        BidirectionalDijkstra algorithm = new BidirectionalDijkstra(network);
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.findShortestPath(network.forward(), -1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.findShortestPath(network.forward(), 5, 3));
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.findShortestPath(network.forward(), 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.findShortestPath(network.forward(), 0, 5));
    }

    @Test
    void rejectsAnotherGraph() {
        BidirectionalDijkstra algorithm = new BidirectionalDijkstra(example());

        assertThrows(IllegalArgumentException.class,
                () -> algorithm.findShortestPath(example().forward(), 0, 4));
    }


    private double pathLength(Graph graph, int[] path) {
        double total = 0;
        for (int i = 1; i < path.length; i++) {
            int from = path[i - 1];
            int to = path[i];
            double weight = Double.POSITIVE_INFINITY;
            for (int edge = graph.edgeStart(from); edge < graph.edgeEnd(from); edge++) {
                if (graph.edgeTarget(edge) == to) {
                    weight = Math.min(weight, graph.edgeWeight(edge));
                }
            }
            total += weight;
        }
        return total;
    }

    private Graph fromMatrix(double[][] weights, boolean reversed) {
        int n = weights.length;
        int[] offsets = new int[n + 1];
        int edges = 0;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                if (Double.isFinite(reversed ? weights[v][u] : weights[u][v])) {
                    edges++;
                }
            }
            offsets[u + 1] = edges;
        }
        int[] targets = new int[edges];
        double[] edgeWeights = new double[edges];
        int pos = 0;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                double weight = reversed ? weights[v][u] : weights[u][v];
                if (Double.isFinite(weight)) {
                    targets[pos] = v;
                    edgeWeights[pos] = weight;
                    pos++;
                }
            }
        }
        return new CsrGraph(offsets, targets, edgeWeights);
    }
}
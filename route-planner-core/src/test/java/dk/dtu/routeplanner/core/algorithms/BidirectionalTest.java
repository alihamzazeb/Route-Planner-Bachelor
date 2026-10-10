package dk.dtu.routeplanner.core.algorithms;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.model.SearchResult;

class BidirectionalDijkstraTest {

    // Directed graph: 0 -> 1 -> 2 -> 3 -> 4 costs 2+2+2+1 = 7.
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
    void comparesAllPairsAgainstDijkstra() {
        RoadNetwork network = example();
        BidirectionalDijkstra bidirectional = new BidirectionalDijkstra(network);
        Dijkstra reference = new Dijkstra();

        for (int source = 0; source < network.forward().nodeCount(); source++) {
            for (int target = 0; target < network.forward().nodeCount(); target++) {
                SearchResult expected = reference.findShortestPath(network.forward(), source, target);
                SearchResult actual = bidirectional.findShortestPath(network.forward(), source, target);
                assertEquals(expected.distance(), actual.distance(), 1e-10,
                        "Mismatch from " + source + " to " + target);
            }
        }
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
                        weights[u][v] = random.nextInt(11); // zero-weight edges are supported
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
    void rejectsInvalidNode() {
        RoadNetwork network = example();
        BidirectionalDijkstra algorithm = new BidirectionalDijkstra(network);
        assertThrows(IllegalArgumentException.class,
                () -> algorithm.findShortestPath(network.forward(), -1, 3));
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

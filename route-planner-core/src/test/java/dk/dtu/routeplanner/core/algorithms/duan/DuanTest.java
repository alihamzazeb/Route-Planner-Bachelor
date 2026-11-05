package dk.dtu.routeplanner.core.algorithms.duan;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

import static dk.dtu.routeplanner.core.algorithms.duan.DuanTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DuanTest {
    @Test
    void findsDirectedPathsAndHandlesUnreachableNodes() {
        Graph graph = graph(7, new Edge(0, 0, 0), new Edge(0, 1, 9), new Edge(0, 1, 5),
                new Edge(0, 2, 20), new Edge(1, 2, 2), new Edge(2, 3, 1), new Edge(4, 5, 1));
        double infinity = Double.POSITIVE_INFINITY;
        for (Duan algorithm : variants(graph)) {
            assertDistances(algorithm.computeShortestPaths(graph, 0), 0, 5, 7, 8, infinity, infinity, infinity);
            var route = algorithm.findShortestPath(graph, 0, 3);
            assertEquals(8, route.distance());
            assertArrayEquals(new int[]{0, 1, 2, 3}, route.path());
            assertPath(graph, 3, 0, null, algorithm.findShortestPath(graph, 3, 0));
            assertPath(graph, 6, 6, BigDecimal.ZERO, algorithm.findShortestPath(graph, 6, 6));
            SingleSourceResult isolated = algorithm.computeShortestPaths(graph, 6);
            assertDistances(isolated, infinity, infinity, infinity, infinity, infinity, infinity, 0);
            assertEquals(0, isolated.relaxedCounter());
        }
    }

    @Test
    void returnsASimplePathAfterDegreeReduction() {
        // The internal zero-cost cycle used to project to [0, 9, 0, 10].
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(0, 9, 0));
        for (int node = 1; node <= 8; node++) edges.add(new Edge(0, node, 0));
        edges.add(new Edge(0, 10, 1));
        for (int node = 1; node <= 9; node++) edges.add(new Edge(node, 0, 0));
        Graph graph = graph(11, edges);
        for (Duan algorithm : variants(graph)) {
            var route = algorithm.findShortestPath(graph, 0, 10);
            assertArrayEquals(new int[]{0, 10}, route.path());
            assertPath(graph, 0, 10, BigDecimal.ONE, route);
        }
    }

    @Test
    void completesLongChainsIncludingZeroWeightCycles() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int weight : new int[]{0, 1}) {
                List<Edge> edges = new ArrayList<>();
                double[] expected = new double[1024];
                for (int node = 0; node < expected.length; node++) {
                    expected[node] = node * weight;
                    edges.add(new Edge(node, (node + 1) % expected.length, weight));
                    if (node + 20 < expected.length) edges.add(new Edge(node, node + 20, 50));
                }
                Graph graph = graph(expected.length, edges);
                for (Duan algorithm : variants(graph)) {
                    assertDistances(algorithm.computeShortestPaths(graph, 0), expected);
                }
            }
        });
    }

    @Test
    void rejectsInvalidQueries() {
        Graph graph = graph(2);
        for (Duan algorithm : variants(graph)) {
            assertThrows(NullPointerException.class, () -> algorithm.computeShortestPaths(null, 0));
            assertThrows(IllegalArgumentException.class, () -> algorithm.computeShortestPaths(graph, -1));
            assertThrows(IllegalArgumentException.class, () -> algorithm.computeShortestPaths(graph, 2));
            assertThrows(IllegalArgumentException.class, () -> algorithm.findShortestPath(graph, 0, -1));
            assertThrows(IllegalArgumentException.class, () -> algorithm.findShortestPath(graph, 0, 2));
        }
        assertThrows(IllegalArgumentException.class, () -> new Duan().computeShortestPaths(graph(0), 0));
        assertThrows(IllegalStateException.class, () -> new Duan().graphInfo());
        for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
            Duan prepared = new Duan(graph, mode);
            assertThrows(IllegalArgumentException.class, () -> prepared.computeShortestPaths(graph(2), 0));
        }
    }

    @Test
    void finishesWhenTheWorkLimitEqualsTheNodeCount() {
        // An empty queue means the search finished, even if it also reached the work limit.
        Graph graph = graph(16,
                new Edge(12, 7, 442), new Edge(0, 12, 437), new Edge(5, 3, 977), new Edge(12, 10, 631),
                new Edge(10, 1, 378), new Edge(10, 4, 411), new Edge(9, 11, 233), new Edge(2, 9, 84),
                new Edge(6, 13, 692), new Edge(7, 3, 246), new Edge(6, 14, 909), new Edge(12, 15, 782),
                new Edge(0, 6, 442), new Edge(10, 2, 9), new Edge(7, 8, 603), new Edge(12, 5, 309));
        BigDecimal[] expected = bellmanFord(graph, 0);
        for (Duan algorithm : variants(graph)) {
            assertDistances(algorithm.computeShortestPaths(graph, 0), expected);
        }
    }

    @Test
    void preparedQueriesKeepIndependentResults() throws Exception {
        Graph graph = graph(64, new Edge(0, 1, 0.1), new Edge(1, 2, 0.2), new Edge(2, 1, 0),
                new Edge(20, 21, 1), new Edge(21, 22, 0), new Edge(22, 20, 2));
        for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
            Duan prepared = new Duan(graph, mode);
            SingleSourceResult first = prepared.computeShortestPaths(graph, 0);
            try (var executor = Executors.newFixedThreadPool(3)) {
                List<Callable<Void>> tasks = new ArrayList<>();
                for (int source : new int[]{0, 20, 63, 2, 21, 0}) {
                    tasks.add(() -> {
                        assertDistances(prepared.computeShortestPaths(graph, source), bellmanFord(graph, source));
                        return null;
                    });
                }
                for (var result : executor.invokeAll(tasks)) result.get();
            }
            assertDistances(first, bellmanFord(graph, 0));
        }
    }

    @Test
    void seededGraphsMatchBellmanFord() {
        Random random = new Random(42);
        for (int trial = 0; trial < 40; trial++) {
            int n = trial < 36 ? 5 + random.nextInt(60) : 320;
            List<Edge> edges = new ArrayList<>();
            for (int edge = 0; edge < 4 * n; edge++) {
                double weight = switch (trial % 4) {
                    case 0 -> random.nextInt(2);
                    case 1 -> random.nextInt(1000) / 10.0;
                    case 2 -> Math.scalb(1 + random.nextDouble(), random.nextInt(2075) - 1074);
                    default -> random.nextDouble() * 20;
                };
                edges.add(new Edge(random.nextInt(n), random.nextInt(n), weight));
            }
            Graph graph = graph(n, edges);
            List<Duan> algorithms = variants(graph);
            for (int query = 0; query < 2; query++) {
                int source = random.nextInt(n);
                int target = random.nextInt(n);
                BigDecimal[] expected = bellmanFord(graph, source);
                String context = "trial=" + trial + ", source=" + source;
                for (Duan algorithm : algorithms) {
                    assertAll(context, () -> {
                        assertDistances(algorithm.computeShortestPaths(graph, source), expected);
                        assertPath(graph, source, target, expected[target],
                                algorithm.findShortestPath(graph, source, target));
                    });
                }
            }
        }
    }
}

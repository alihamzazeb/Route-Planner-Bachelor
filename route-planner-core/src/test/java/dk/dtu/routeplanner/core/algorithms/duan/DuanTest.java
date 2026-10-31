package dk.dtu.routeplanner.core.algorithms.duan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuanTest {

    @Test
    void computesKnownDistances() {
        Graph graph = new CsrGraph(
                new int[]{0, 3, 4, 5, 6},
                new int[]{1, 2, 3, 3, 0, 1},
                new double[]{10, 40, 60, 30, 20, 50});
        for (Duan algorithm : variants(graph)) {
            checkDistances(graph, 0, new double[]{0, 10, 40, 40}, algorithm);
        }
    }

    @Test
    void respectsDirectionAndUnreachableNodes() {
        Graph graph = graph(5, List.of(new Edge(0, 1, 5), new Edge(2, 3, 1)));
        double infinity = Double.POSITIVE_INFINITY;
        for (Duan algorithm : variants(graph)) {
            checkDistances(graph, 0, new double[]{0, 5, infinity, infinity, infinity}, algorithm);
            checkDistances(graph, 1, new double[]{infinity, 0, infinity, infinity, infinity}, algorithm);
            SingleSourceResult isolated = algorithm.computeShortestPaths(graph, 4);
            assertEquals(0, isolated.distance(4));
            assertEquals(infinity, isolated.distance(0));
            assertEquals(1, isolated.settledCounter());
            assertEquals(0, isolated.relaxedCounter());
        }
    }

    @Test
    void handlesZeroWeightCyclesAndTiedRoutes() {
        Graph graph = graph(6, List.of(
                new Edge(0, 1, 0), new Edge(1, 2, 0), new Edge(2, 0, 0),
                new Edge(0, 3, 2), new Edge(2, 3, 2), new Edge(1, 4, 2),
                new Edge(3, 4, 0), new Edge(4, 3, 0), new Edge(4, 5, 1)));
        for (Duan algorithm : variants(graph)) {
            checkDistances(graph, 0, new double[]{0, 0, 0, 2, 2, 3}, algorithm);
            SearchResult route = algorithm.findShortestPath(graph, 0, 5);
            assertEquals(3, route.distance());
            checkPath(graph, 0, 5, route);
        }
    }

    @Test
    void choosesTheCheapestParallelEdgeAndIgnoresSelfLoops() {
        Graph graph = graph(3, List.of(
                new Edge(0, 0, 0), new Edge(0, 1, 9), new Edge(0, 1, 2),
                new Edge(0, 1, 2), new Edge(1, 1, 4), new Edge(1, 2, 0.5), new Edge(0, 2, 10)));
        for (Duan algorithm : variants(graph)) {
            checkDistances(graph, 0, new double[]{0, 2, 2.5}, algorithm);
            SearchResult route = algorithm.findShortestPath(graph, 0, 2);
            assertArrayEquals(new int[]{0, 1, 2}, route.path());
            assertEquals(2.5, route.distance());
        }
    }

    @Test
    void completesALongChain() {
        List<Edge> edges = new ArrayList<>();
        double[] expected = new double[1024];
        for (int node = 0; node < expected.length; node++) {
            expected[node] = node;
            if (node + 1 < expected.length) edges.add(new Edge(node, node + 1, 1));
            if (node + 20 < expected.length) edges.add(new Edge(node, node + 20, 50));
        }
        Graph graph = graph(expected.length, edges);
        for (Duan algorithm : variants(graph)) {
            checkDistances(graph, 0, expected, algorithm);
        }
    }

    @Test
    void terminatesOnALongZeroWeightCycle() {
        List<Edge> edges = new ArrayList<>();
        for (int node = 0; node < 1024; node++) {
            edges.add(new Edge(node, (node + 1) % 1024, 0));
        }
        Graph graph = graph(1024, edges);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (Duan algorithm : variants(graph)) {
                checkDistances(graph, 711, new double[1024], algorithm);
            }
        });
    }

    @Test
    void reconstructsOriginalNodesThroughAHighDegreeVertex() {
        List<Edge> edges = new ArrayList<>();
        for (int node = 1; node < 80; node++) {
            edges.add(new Edge(node, 0, 1));
            edges.add(new Edge(0, node, node));
        }
        Graph graph = graph(80, edges);
        for (Duan algorithm : variants(graph)) {
            SearchResult route = algorithm.findShortestPath(graph, 79, 50);
            assertEquals(51, route.distance());
            assertArrayEquals(new int[]{79, 0, 50}, route.path());
            assertEquals(80, route.settledCounter());
        }
    }

    @Test
    void handlesUnreachableAndSameNodeRoutesWithoutChangingEarlierResults() {
        Graph graph = graph(4, List.of(new Edge(0, 1, 2), new Edge(1, 2, 3)));
        for (Duan algorithm : variants(graph)) {
            SingleSourceResult firstDistances = algorithm.computeShortestPaths(graph, 0);
            SearchResult firstRoute = algorithm.findShortestPath(graph, 0, 2);
            SearchResult unreachable = algorithm.findShortestPath(graph, 2, 0);
            SearchResult sameNode = algorithm.findShortestPath(graph, 3, 3);
            assertEquals(5, firstDistances.distance(2));
            assertEquals(5, firstRoute.distance());
            assertArrayEquals(new int[]{0, 1, 2}, firstRoute.path());
            assertEquals(Double.POSITIVE_INFINITY, unreachable.distance());
            assertArrayEquals(new int[0], unreachable.path());
            assertEquals(0, sameNode.distance());
            assertArrayEquals(new int[]{3}, sameNode.path());
        }
    }

    @Test
    void rejectsInvalidInputs() {
        Graph graph = graph(2, List.of());
        for (Duan algorithm : variants(graph)) {
            assertThrows(IllegalArgumentException.class, () -> algorithm.computeShortestPaths(graph, -1));
            assertThrows(IllegalArgumentException.class, () -> algorithm.computeShortestPaths(graph, 2));
            assertThrows(NullPointerException.class, () -> algorithm.computeShortestPaths(null, 0));
            assertThrows(IllegalArgumentException.class, () -> algorithm.findShortestPath(graph, 0, -1));
            assertThrows(IllegalArgumentException.class, () -> algorithm.findShortestPath(graph, 0, 2));
        }
        Graph empty = graph(0, List.of());
        for (Duan algorithm : variants(empty)) {
            assertThrows(IllegalArgumentException.class, () -> algorithm.computeShortestPaths(empty, 0));
        }
        for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
            Duan prepared = new Duan(graph, mode);
            Graph anotherGraph = graph(2, List.of());
            assertThrows(IllegalArgumentException.class, () -> prepared.computeShortestPaths(anotherGraph, 0));
        }
    }

    @Test
    void graphInfoNeedsAPreparedGraph() {
        assertThrows(IllegalStateException.class, () -> new Duan().graphInfo());
    }

    @Test
    void rejectsDistanceOverflow() {
        Graph graph = graph(3, List.of(
                new Edge(0, 1, Double.MAX_VALUE), new Edge(1, 2, Double.MAX_VALUE)));
        for (Duan algorithm : variants(graph)) {
            assertThrows(ArithmeticException.class, () -> algorithm.computeShortestPaths(graph, 0));
        }
    }

    @Test
    void seededGraphsAgreeWithBellmanFordAndReturnValidPaths() {
        Random random = new Random(42);
        for (int trial = 0; trial < 30; trial++) {
            int n = trial < 25 ? 5 + random.nextInt(60) : 320;
            List<Edge> edges = new ArrayList<>();
            for (int edge = 0; edge < 4 * n; edge++) {
                double weight = trial % 3 == 0 ? 0
                        : trial % 3 == 1 ? random.nextInt(20) / 4.0 : random.nextDouble() * 20;
                edges.add(new Edge(random.nextInt(n), random.nextInt(n), weight));
            }
            Graph graph = graph(n, edges);
            List<Duan> algorithms = variants(graph);
            for (int query = 0; query < 3; query++) {
                int source = random.nextInt(n);
                int target = random.nextInt(n);
                double[] expected = bellmanFord(graph, source);
                for (Duan algorithm : algorithms) {
                    checkDistances(graph, source, expected, algorithm);
                    SearchResult route = algorithm.findShortestPath(graph, source, target);
                    assertEquals(expected[target], route.distance(), 1e-9);
                    checkPath(graph, source, target, route);
                }
            }
        }
    }

    @Test
    void preparedQueriesCanRunConcurrentlyFromDifferentComponents() throws Exception {
        Graph graph = graph(64, List.of(
                new Edge(0, 1, 2), new Edge(1, 2, 3), new Edge(2, 1, 0),
                new Edge(20, 21, 1), new Edge(21, 22, 0), new Edge(22, 20, 2)));
        for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
            Duan prepared = new Duan(graph, mode);
            try (var executor = Executors.newFixedThreadPool(3)) {
                List<Callable<Void>> tasks = new ArrayList<>();
                for (int source : new int[]{0, 20, 63, 2, 21, 0}) {
                    tasks.add(() -> {
                        checkDistances(graph, source, bellmanFord(graph, source), prepared);
                        return null;
                    });
                }
                for (var result : executor.invokeAll(tasks)) result.get();
            }
        }
    }

    private List<Duan> variants(Graph graph) {
        return List.of(new Duan(), new Duan(Duan.DegreeMode.BOUNDED),
                new Duan(graph, Duan.DegreeMode.TRANSFORM), new Duan(graph, Duan.DegreeMode.BOUNDED));
    }

    private void checkDistances(Graph graph, int source, double[] expected, Duan algorithm) {
        SingleSourceResult result = algorithm.computeShortestPaths(graph, source);
        int reachable = 0;
        for (int node = 0; node < graph.nodeCount(); node++) {
            assertEquals(expected[node], result.distance(node), 1e-9, "source=" + source + ", node=" + node);
            if (Double.isFinite(expected[node])) reachable++;
        }
        assertEquals(reachable, result.settledCounter());
    }

    private void checkPath(Graph graph, int source, int target, SearchResult result) {
        int[] path = result.path();
        if (result.distance() == Double.POSITIVE_INFINITY) {
            assertEquals(0, path.length);
            return;
        }
        assertTrue(path.length > 0 && path.length <= graph.nodeCount());
        assertEquals(source, path[0]);
        assertEquals(target, path[path.length - 1]);
        double cost = 0;
        for (int step = 1; step < path.length; step++) {
            int from = path[step - 1];
            int to = path[step];
            assertTrue(from >= 0 && from < graph.nodeCount());
            assertTrue(to >= 0 && to < graph.nodeCount());
            assertTrue(from != to);
            double weight = Double.POSITIVE_INFINITY;
            for (int edge = graph.edgeStart(from); edge < graph.edgeEnd(from); edge++) {
                if (graph.edgeTarget(edge) == to) weight = Math.min(weight, graph.edgeWeight(edge));
            }
            assertTrue(Double.isFinite(weight), "Missing directed edge " + from + " -> " + to);
            cost += weight;
        }
        assertEquals(result.distance(), cost, 1e-9);
    }

    private double[] bellmanFord(Graph graph, int source) {
        double[] dist = new double[graph.nodeCount()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source] = 0;
        for (int pass = 1; pass < graph.nodeCount(); pass++) {
            boolean changed = false;
            for (int node = 0; node < graph.nodeCount(); node++) {
                for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                    int neighbour = graph.edgeTarget(edge);
                    double candidate = dist[node] + graph.edgeWeight(edge);
                    if (candidate < dist[neighbour]) {
                        dist[neighbour] = candidate;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        return dist;
    }

    private record Edge(int from, int to, double weight) {}

    private Graph graph(int n, List<Edge> edges) {
        int[] offsets = new int[n + 1];
        for (Edge edge : edges) offsets[edge.from() + 1]++;
        for (int node = 0; node < n; node++) offsets[node + 1] += offsets[node];
        int[] targets = new int[edges.size()];
        double[] weights = new double[edges.size()];
        int[] position = offsets.clone();
        for (Edge edge : edges) {
            int index = position[edge.from()]++;
            targets[index] = edge.to();
            weights[index] = edge.weight();
        }
        return new CsrGraph(offsets, targets, weights);
    }
}

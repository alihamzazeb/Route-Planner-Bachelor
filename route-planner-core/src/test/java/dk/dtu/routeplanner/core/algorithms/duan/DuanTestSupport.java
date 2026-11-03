package dk.dtu.routeplanner.core.algorithms.duan;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

import static org.junit.jupiter.api.Assertions.*;

final class DuanTestSupport {
    private DuanTestSupport() {}

    record Edge(int from, int to, double weight) {}

    static Graph graph(int nodes, Edge... edges) {
        return graph(nodes, List.of(edges));
    }

    static Graph graph(int nodes, List<Edge> edges) {
        int[] offsets = new int[nodes + 1];
        for (Edge edge : edges) offsets[edge.from() + 1]++;
        for (int node = 0; node < nodes; node++) offsets[node + 1] += offsets[node];
        int[] position = offsets.clone();
        int[] targets = new int[edges.size()];
        double[] weights = new double[edges.size()];
        for (Edge edge : edges) {
            int index = position[edge.from()]++;
            targets[index] = edge.to();
            weights[index] = edge.weight();
        }
        return new CsrGraph(offsets, targets, weights);
    }

    static List<Duan> variants(Graph graph) {
        return List.of(new Duan(), new Duan(Duan.DegreeMode.BOUNDED),
                new Duan(graph, Duan.DegreeMode.TRANSFORM), new Duan(graph, Duan.DegreeMode.BOUNDED));
    }

    static void assertDistances(SingleSourceResult result, double... expected) {
        int reachable = 0;
        for (int node = 0; node < expected.length; node++) {
            assertEquals(expected[node], result.distance(node), "node=" + node);
            if (Double.isFinite(expected[node])) reachable++;
        }
        assertEquals(reachable, result.settledCounter());
    }

    static void assertDistances(SingleSourceResult result, BigDecimal[] expected) {
        assertDistances(result, Arrays.stream(expected)
                .mapToDouble(value -> value == null ? Double.POSITIVE_INFINITY : value.doubleValue()).toArray());
    }

    static void assertPath(Graph graph, int source, int target, BigDecimal expected, SearchResult result) {
        int[] path = result.path();
        if (expected == null) {
            assertEquals(Double.POSITIVE_INFINITY, result.distance());
            assertEquals(0, path.length);
            return;
        }
        assertEquals(expected.doubleValue(), result.distance());
        assertTrue(path.length > 0 && path.length <= graph.nodeCount());
        assertEquals(source, path[0]);
        assertEquals(target, path[path.length - 1]);
        boolean[] seen = new boolean[graph.nodeCount()];
        BigDecimal cost = BigDecimal.ZERO;
        for (int step = 0; step < path.length; step++) {
            int node = path[step];
            assertTrue(node >= 0 && node < graph.nodeCount());
            assertFalse(seen[node], "Repeated vertex " + node);
            seen[node] = true;
            if (step == 0) continue;
            double weight = Double.POSITIVE_INFINITY;
            for (int edge = graph.edgeStart(path[step - 1]); edge < graph.edgeEnd(path[step - 1]); edge++) {
                if (graph.edgeTarget(edge) == node) weight = Math.min(weight, graph.edgeWeight(edge));
            }
            assertTrue(Double.isFinite(weight), "Missing edge " + path[step - 1] + " -> " + node);
            cost = cost.add(new BigDecimal(weight));
        }
        assertEquals(0, expected.compareTo(cost), "Path must have the exact shortest cost");
    }

    // An independent Bellman–Ford reference: no BMSSP code or rounded intermediate sums.
    static BigDecimal[] bellmanFord(Graph graph, int source) {
        int n = graph.nodeCount();
        BigDecimal[] weights = new BigDecimal[graph.edgeEnd(n - 1)];
        for (int edge = 0; edge < weights.length; edge++) weights[edge] = new BigDecimal(graph.edgeWeight(edge));
        BigDecimal[] distances = new BigDecimal[n];
        distances[source] = BigDecimal.ZERO;
        for (int pass = 1; pass < n; pass++) {
            BigDecimal[] next = distances.clone();
            boolean changed = false;
            for (int node = 0; node < n; node++) {
                if (distances[node] == null) continue;
                for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                    int target = graph.edgeTarget(edge);
                    BigDecimal candidate = distances[node].add(weights[edge]);
                    if (next[target] == null || candidate.compareTo(next[target]) < 0) {
                        next[target] = candidate;
                        changed = true;
                    }
                }
            }
            distances = next;
            if (!changed) break;
        }
        return distances;
    }
}

package dk.dtu.routeplanner.core.algorithms.duan;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuanGraphTest {

    @Test
    void transformationPreservesDistancesAndBoundsBothDegrees() {
        List<Edge> edges = new ArrayList<>();
        for (int node = 1; node < 25; node++) {
            edges.add(new Edge(0, node, node / 4.0));
            edges.add(new Edge(node, 0, 2));
            edges.add(new Edge(0, node, 100));
            edges.add(new Edge(node, node, 0));
        }
        edges.add(new Edge(25, 26, 0));
        Graph original = graph(28, edges);
        for (int source = 0; source < original.nodeCount(); source++) {
            DuanGraph converted = DuanGraph.prepare(original, source);
            int[] incoming = new int[converted.nodeCount()];
            for (int node = 0; node < converted.nodeCount(); node++) {
                assertTrue(converted.edgeEnd(node) - converted.edgeStart(node) <= 2);
                for (int edge = converted.edgeStart(node); edge < converted.edgeEnd(node); edge++) {
                    incoming[converted.edgeTarget(edge)]++;
                }
            }
            for (int degree : incoming) assertTrue(degree <= 2);
            SingleSourceResult expected = new Dijkstra().computeShortestPaths(original, source);
            SingleSourceResult actual = new Dijkstra().computeShortestPaths(converted, converted.source);
            for (int node = 0; node < original.nodeCount(); node++) {
                int mapped = converted.originalToInternal[node];
                double distance = mapped < 0 ? Double.POSITIVE_INFINITY : actual.distance(mapped);
                assertEquals(expected.distance(node), distance);
            }
            assertEquals(expected.settledCounter(), converted.reachableCount);
        }
    }

    @Test
    void boundedModeChecksIncomingAndOutgoingDegree() {
        for (boolean incoming : new boolean[]{false, true}) {
            for (int degree : new int[]{8, 9}) {
                List<Edge> edges = new ArrayList<>();
                for (int node = 1; node <= degree; node++) {
                    edges.add(incoming ? new Edge(node, 0, 1) : new Edge(0, node, 1));
                    if (incoming && node < degree) edges.add(new Edge(node, node + 1, 1));
                }
                Graph graph = graph(degree + 1, edges);
                int source = incoming ? 1 : 0;
                Duan prepared = new Duan(graph, Duan.DegreeMode.BOUNDED);
                assertEquals(degree, prepared.graphInfo().maxDegree());
                assertEquals(degree > 8, prepared.graphInfo().transformed());
                DuanGraph local = DuanGraph.prepare(graph, source, Duan.DegreeMode.BOUNDED);
                assertEquals(degree > 8, local.transformed);
                SingleSourceResult expected = new Dijkstra().computeShortestPaths(graph, source);
                for (Duan algorithm : List.of(prepared, new Duan(Duan.DegreeMode.BOUNDED))) {
                    SingleSourceResult actual = algorithm.computeShortestPaths(graph, source);
                    for (int node = 0; node < graph.nodeCount(); node++) {
                        assertEquals(expected.distance(node), actual.distance(node));
                    }
                }
            }
        }
    }

    @Test
    void compactionKeepsTheCheapestEdgeAndRemovesSelfLoops() {
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(0, 0, 0));
        for (int weight = 20; weight >= 1; weight--) edges.add(new Edge(0, 1, weight));
        Graph graph = graph(2, edges);
        for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
            DuanGraph converted = DuanGraph.prepare(graph, 0, mode);
            assertEquals(2, converted.nodeCount());
            assertEquals(1, converted.compactEdgeCount());
            assertEquals(1, converted.edgeEnd(1));
            assertEquals(1, converted.edgeWeight(0));
            assertEquals(1, converted.maxDegree);
            assertEquals(mode == Duan.DegreeMode.TRANSFORM, converted.transformed);
        }
    }

    @Test
    void rejectsInvalidWeightsDuringQueryAndFullPreparation() {
        for (double weight : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            Graph invalid = new Graph() {
                public int nodeCount() { return 2; }
                public int edgeStart(int node) { return 0; }
                public int edgeEnd(int node) { return node; }
                public int edgeTarget(int edge) { return 1; }
                public double edgeWeight(int edge) { return weight; }
            };
            for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
                assertThrows(IllegalArgumentException.class,
                        () -> new Duan(mode).computeShortestPaths(invalid, 1));
                assertThrows(IllegalArgumentException.class, () -> new Duan(invalid, mode));
            }
        }
    }

    private record Edge(int from, int to, double weight) {}

    private Graph graph(int n, List<Edge> edges) {
        int[] offsets = new int[n + 1];
        for (Edge edge : edges) offsets[edge.from() + 1]++;
        for (int node = 0; node < n; node++) offsets[node + 1] += offsets[node];
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
}

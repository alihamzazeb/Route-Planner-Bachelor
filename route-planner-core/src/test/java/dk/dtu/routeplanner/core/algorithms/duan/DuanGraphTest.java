package dk.dtu.routeplanner.core.algorithms.duan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

import static dk.dtu.routeplanner.core.algorithms.duan.DuanTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DuanGraphTest {

    @Test
    void preparationPreservesDistancesAndBoundsBothDegrees() {
        for (boolean incoming : new boolean[]{false, true}) {
            for (int degree : new int[]{8, 9}) {
                List<Edge> edges = new ArrayList<>();
                for (int node = 1; node <= degree; node++) {
                    edges.add(incoming ? new Edge(node, 0, node) : new Edge(0, node, node));
                    if (incoming && node < degree) edges.add(new Edge(node, node + 1, 1));
                }
                edges.add(new Edge(degree + 1, degree + 2, 0));
                Graph original = graph(degree + 4, edges);
                int source = incoming ? 1 : 0;
                SingleSourceResult expected = new Dijkstra().computeShortestPaths(original, source);
                for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
                    DuanGraph all = DuanGraph.prepareAll(original, mode);
                    DuanGraph local = mode == Duan.DegreeMode.TRANSFORM
                            ? DuanGraph.prepare(original, source) : DuanGraph.prepare(original, source, mode);
                    assertEquals(degree, new Duan(original, mode).graphInfo().maxDegree());
                    assertEquals(expected.settledCounter(), local.reachableCount);
                    DuanGraph.Reachability reachable = all.reachableFrom(source);
                    assertEquals(local.nodeCount(), reachable.internalNodes());
                    assertEquals(local.reachableCount, reachable.originalNodes());
                    for (DuanGraph converted : List.of(local, all)) {
                        boolean transformed = mode == Duan.DegreeMode.TRANSFORM || degree > 8;
                        assertEquals(transformed, converted.transformed);
                        assertDegreeBound(converted, transformed ? 2 : 8);
                        assertPreservedDistances(converted, source, expected);
                    }
                }
            }
        }
    }

    @Test
    void compactionAndArithmeticProfilingUseOnlyRetainedEdges() {
        Graph original = graph(4, new Edge(0, 0, 0.1), new Edge(0, 1, 2.5),
                new Edge(0, 1, -0.0), new Edge(0, 1, 3), new Edge(2, 3, 0.1));
        for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
            DuanGraph local = DuanGraph.prepare(original, 0, mode);
            assertEquals(2, local.nodeCount());
            assertEquals(1, local.compactEdgeCount());
            assertEquals(1, local.edgeEnd(1));
            assertEquals(0.0, local.edgeWeight(0), 0.0);
            assertFalse(local.usesExactArithmetic());
            DuanGraph all = DuanGraph.prepareAll(original, mode);
            assertTrue(all.usesExactArithmetic());
            assertSame(BigDecimal.ZERO, all.exactEdgeWeight(0));
            assertEquals(new BigDecimal(0.1), all.exactEdgeWeight(1));
        }
    }

    @Test
    void integerArithmeticBoundaryUsesTheTransformedSize() {
        long limit = (1L << 53) / 18;
        for (long weight : new long[]{limit, limit + 1}) {
            List<Edge> edges = new ArrayList<>();
            for (int node = 1; node < 10; node++) edges.add(new Edge(0, node, weight));
            Graph original = graph(10, edges);
            for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
                for (DuanGraph converted : List.of(DuanGraph.prepare(original, 0, mode),
                        DuanGraph.prepareAll(original, mode))) {
                    assertEquals(18, converted.nodeCount());
                    assertEquals(weight > limit, converted.usesExactArithmetic());
                    if (converted.usesExactArithmetic()) {
                        assertSame(BigDecimal.ZERO, converted.exactEdgeWeight(0));
                        assertEquals(BigDecimal.valueOf(weight), converted.exactEdgeWeight(1));
                    }
                }
            }
        }
    }

    @Test
    void preparationRejectsInvalidCustomGraphEdges() {
        for (Edge edge : List.of(new Edge(0, -1, 1), new Edge(0, 2, 1), new Edge(0, 1, -1),
                new Edge(0, 1, Double.NaN), new Edge(0, 1, Double.POSITIVE_INFINITY))) {
            Graph invalid = new Graph() {
                public int nodeCount() { return 2; }
                public int edgeStart(int node) { return node == 0 ? 0 : 1; }
                public int edgeEnd(int node) { return 1; }
                public int edgeTarget(int index) { return edge.to(); }
                public double edgeWeight(int index) { return edge.weight(); }
            };
            for (Duan.DegreeMode mode : Duan.DegreeMode.values()) {
                assertThrows(IllegalArgumentException.class, () -> DuanGraph.prepare(invalid, 0, mode));
                assertThrows(IllegalArgumentException.class, () -> DuanGraph.prepareAll(invalid, mode));
            }
        }
    }

    private void assertDegreeBound(DuanGraph graph, int limit) {
        int[] indegrees = new int[graph.nodeCount()];
        for (int node = 0; node < graph.nodeCount(); node++) {
            assertTrue(graph.edgeEnd(node) - graph.edgeStart(node) <= limit);
            for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                indegrees[graph.edgeTarget(edge)]++;
            }
        }
        for (int degree : indegrees) assertTrue(degree <= limit);
    }

    private void assertPreservedDistances(DuanGraph graph, int source, SingleSourceResult expected) {
        SingleSourceResult actual = new Dijkstra().computeShortestPaths(graph, graph.originalToInternal[source]);
        for (int node = 0; node < graph.originalToInternal.length; node++) {
            int mapped = graph.originalToInternal[node];
            assertEquals(expected.distance(node), mapped < 0 ? Double.POSITIVE_INFINITY : actual.distance(mapped));
        }
    }
}

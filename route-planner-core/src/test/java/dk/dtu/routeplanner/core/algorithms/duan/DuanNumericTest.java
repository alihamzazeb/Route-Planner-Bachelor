package dk.dtu.routeplanner.core.algorithms.duan;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.Graph;

import static dk.dtu.routeplanner.core.algorithms.duan.DuanTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DuanNumericTest {
    @Test
    void keepsExactCostsWhenDoubleSumsRound() {
        // Decimal ties, an absorbed improvement, accumulated small edges, and tied outputs.
        List<Graph> cases = List.of(
                graph(4, new Edge(0, 1, 0.1), new Edge(0, 2, Math.nextUp(0.1)),
                        new Edge(1, 2, 0), new Edge(2, 3, 0.1)),
                graph(6, new Edge(0, 1, 0), new Edge(0, 4, 1), new Edge(1, 2, 0),
                        new Edge(2, 3, 0), new Edge(3, 4, 0), new Edge(4, 5, 1e20)),
                graph(4, new Edge(0, 1, 1e16), new Edge(1, 2, 1), new Edge(2, 3, 1)),
                graph(4, new Edge(0, 1, 1e16), new Edge(0, 2, 1e16),
                        new Edge(1, 3, 1), new Edge(2, 3, 0)));
        for (Graph graph : cases) {
            int target = graph.nodeCount() - 1;
            BigDecimal[] expected = bellmanFord(graph, 0);
            for (Duan algorithm : variants(graph)) {
                assertDistances(algorithm.computeShortestPaths(graph, 0), expected);
                assertPath(graph, 0, target, expected[target], algorithm.findShortestPath(graph, 0, target));
            }
        }
    }

    @Test
    void allowsOverflowingCandidatesButRejectsOverflowingAnswers() {
        double max = Double.MAX_VALUE;
        double quarter = max / 4;
        List<Graph> finiteAnswers = List.of(
                graph(2, new Edge(0, 1, max), new Edge(1, 0, max)),
                graph(5, new Edge(0, 1, max), new Edge(0, 2, quarter),
                        new Edge(1, 3, max), new Edge(2, 4, 0), new Edge(4, 3, quarter)));
        for (Graph graph : finiteAnswers) {
            BigDecimal[] expected = bellmanFord(graph, 0);
            for (Duan algorithm : variants(graph)) {
                assertDistances(algorithm.computeShortestPaths(graph, 0), expected);
            }
        }
        Graph overflow = graph(3, new Edge(0, 1, max), new Edge(1, 2, max));
        for (Duan algorithm : variants(overflow)) {
            assertThrows(ArithmeticException.class, () -> algorithm.computeShortestPaths(overflow, 0));
        }
    }

    @Test
    void keepsThePaperParameterAtPowerBoundaries() {
        assertEquals(1, Duan.parameterT(1));
        assertEquals(3, Duan.parameterT(255));
        assertEquals(4, Duan.parameterT(256));
        assertEquals(4, Duan.parameterT(257));
        assertEquals(8, Duan.parameterT((1 << 27) - 1));
        assertEquals(9, Duan.parameterT(1 << 27));
        assertEquals(9, Duan.parameterT(Integer.MAX_VALUE));
    }
}

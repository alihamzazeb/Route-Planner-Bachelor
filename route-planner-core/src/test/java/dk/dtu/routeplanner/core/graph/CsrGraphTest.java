package dk.dtu.routeplanner.core.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class CsrGraphTest {

    @Test
    void readsNodesAndEdgesFromTheArrays() {
        int[] offsets = {0, 2, 5, 5, 9, 11, 13};
        int[] targets = {1, 3, 0, 2, 4, 1, 5, 0, 4, 3, 5, 4, 2};
        double[] weights = {200, 130, 200, 200, 130, 200, 130, 130, 200, 200, 200, 200, 130};
        Graph graph = new CsrGraph(offsets, targets, weights);

        // Mutating the caller's arrays must not change the stored graph.
        offsets[1] = 0;
        targets[2] = 5;
        weights[4] = 999;
        assertEquals(6, graph.nodeCount());

        // node 1 has edge 2, 3 and 4
        assertEquals(2, graph.edgeStart(1));
        assertEquals(5, graph.edgeEnd(1));
        assertEquals(0, graph.edgeTarget(2));
        assertEquals(2, graph.edgeTarget(3));
        assertEquals(4, graph.edgeTarget(4));
        assertEquals(130.0, graph.edgeWeight(4));
        assertEquals(13, graph.edgeEnd(5));
        assertEquals(5, graph.edgeStart(2));
        assertEquals(5, graph.edgeEnd(2));
    }

    @Test
    void invalidOffsetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{}, new int[]{}, new double[]{}));
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{1, 1}, new int[]{0}, new double[]{5}));
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{0, 2}, new int[]{0}, new double[]{5}));
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{0, 1, 0, 1}, new int[]{0}, new double[]{5}));
    }

    @Test
    void invalidEdgesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{0, 1}, new int[]{0}, new double[]{}));
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{0, 1}, new int[]{-1}, new double[]{5}));
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{0, 1}, new int[]{1}, new double[]{5}));
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{0, 1}, new int[]{0}, new double[]{Double.NaN}));
        assertThrows(IllegalArgumentException.class, () -> new CsrGraph(new int[]{0, 1}, new int[]{0}, new double[]{-5}));
    }

}

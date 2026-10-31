package dk.dtu.routeplanner.core.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CsrGraphTest {

    private Graph graph;

    @BeforeEach
    void setUp() {
        int[] offsets = {0, 2, 5, 7, 9, 11, 13};
        int[] targets = {1, 3, 0, 2, 4, 1, 5, 0, 4, 3, 5, 4, 2};
        double[] weights = {200, 130, 200, 200, 130, 200, 130, 130, 200, 200, 200, 200, 130};
        graph = new CsrGraph(offsets, targets, weights);
    }



    @Test
    void nodeCountIsSix() {
        assertEquals(6, graph.nodeCount());
    }

    @Test
    void edgeStartTest(){
        assertEquals(0, graph.edgeStart(0));
        assertEquals(2, graph.edgeStart(1));
        assertEquals(5, graph.edgeStart(2));
        assertEquals(7, graph.edgeStart(3));
        assertEquals(9, graph.edgeStart(4));
        assertEquals(11, graph.edgeStart(5));
    }

    @Test
    void edgeEndTest(){
        assertEquals(2, graph.edgeEnd(0));
        assertEquals(5, graph.edgeEnd(1));
        assertEquals(7, graph.edgeEnd(2));
        assertEquals(9, graph.edgeEnd(3));
        assertEquals(11, graph.edgeEnd(4));
        assertEquals(13, graph.edgeEnd(5));

    }

    @Test
    void edgeTargetTest(){
        assertEquals(1, graph.edgeTarget(0));
        assertEquals(3, graph.edgeTarget(1));
        assertEquals(0, graph.edgeTarget(2));
        assertEquals(2, graph.edgeTarget(3));
        assertEquals(4, graph.edgeTarget(4));
        assertEquals(1, graph.edgeTarget(5));
        assertEquals(5, graph.edgeTarget(6));
        assertEquals(0, graph.edgeTarget(7));
        assertEquals(4, graph.edgeTarget(8));
        assertEquals(3, graph.edgeTarget(9));
        assertEquals(5, graph.edgeTarget(10));
        assertEquals(4, graph.edgeTarget(11));
        assertEquals(2, graph.edgeTarget(12));
    }

    @Test
    void edgeWeightTest(){
        assertEquals(200.0, graph.edgeWeight(0));
        assertEquals(130.0, graph.edgeWeight(1));
        assertEquals(200.0, graph.edgeWeight(2));
        assertEquals(200.0, graph.edgeWeight(3));
        assertEquals(130.0, graph.edgeWeight(4));
        assertEquals(200.0, graph.edgeWeight(5));
        assertEquals(130.0, graph.edgeWeight(6));
        assertEquals(130.0, graph.edgeWeight(7));
        assertEquals(200.0, graph.edgeWeight(8));
        assertEquals(200.0, graph.edgeWeight(9));
        assertEquals(200.0, graph.edgeWeight(10));
        assertEquals(200.0, graph.edgeWeight(11));
        assertEquals(130.0, graph.edgeWeight(12));
    }


    @Test
    void nodeWithNoEdges() {
        int[] offsets = {0, 2, 2, 3};
        int[] targets = {1, 2, 0};
        double[] weights = {10, 20, 30};
        Graph g = new CsrGraph(offsets, targets, weights);

        assertEquals(g.edgeStart(1), g.edgeEnd(1));
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

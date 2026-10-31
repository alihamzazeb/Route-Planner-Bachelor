package dk.dtu.routeplanner.core.graph;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RoadNetworkTest {

    @Test
    void graphsMustHaveTheSameNodeCount() {
        Graph forward = new CsrGraph(new int[]{0, 0, 0}, new int[]{}, new double[]{});
        Graph reverse = new CsrGraph(new int[]{0, 0, 0, 0}, new int[]{}, new double[]{});

        assertThrows(IllegalArgumentException.class, () -> new RoadNetwork(forward, reverse));
    }
}

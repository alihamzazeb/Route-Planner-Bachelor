package dk.dtu.routeplanner.core.algorithms;

import static dk.dtu.routeplanner.core.algorithms.PathAssertions.assertValidPath;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.model.SearchResult;

class AStarTest {

    @Test
    void findsKnownShortestPathsAndAgreesWithDijkstraOnEveryPair() {
        // Direct 0 -> 2 costs 51,000; going through 1 costs 50,803.
        Graph graph = new CsrGraph(new int[]{0, 2, 4, 4},
                new int[]{1, 2, 0, 2}, new double[]{803, 51000, 803, 50000});
        StraightLine line = new StraightLine(new Coordinates(
                new int[]{-73530767, -73530538, -73519366},
                new int[]{41085396, 41086098, 41048796}));
        AStar aStar = new AStar(line.smallestLimit(graph), line);
        Dijkstra dijkstra = new Dijkstra();
        double infinity = Double.POSITIVE_INFINITY;
        double[][] distances = {{0, 803, 50803}, {803, 0, 50000}, {infinity, infinity, 0}};

        for (int source = 0; source < 3; source++) {
            for (int target = 0; target < 3; target++) {
                String pair = source + " -> " + target;
                SearchResult expected = dijkstra.findShortestPath(graph, source, target);
                SearchResult actual = aStar.findShortestPath(graph, source, target);
                assertEquals(distances[source][target], actual.distance(), 0.0001, pair);
                assertEquals(expected.distance(), actual.distance(), 0.0001, pair);
                assertValidPath(graph, source, target, actual);
                if (source == 0 && target == 2) {
                    assertArrayEquals(new int[]{0, 1, 2}, actual.path());
                }
            }
        }
    }


}

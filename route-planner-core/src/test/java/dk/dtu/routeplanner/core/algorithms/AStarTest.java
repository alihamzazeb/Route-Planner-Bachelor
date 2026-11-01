package dk.dtu.routeplanner.core.algorithms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;

class AStarTest {

    @TempDir
    Path tempDir;

    private Coordinates loadSmallCoordinates() throws IOException {
        Path file = tempDir.resolve("small.co");
        Files.writeString(file, """
            c tiny example
            p aux sp co 3
            v 1 -73530767 41085396
            v 3 -73519366 41048796
            v 2 -73530538 41086098
            """);
        return new DimacsLoader().loadCoordinates(file);
    }

    // slot 0 to slot 2: direct 51,000, or through slot 1 for 803 + 50,000 = 50,803
    private Graph twoRoutes() {
        return new CsrGraph(
            new int[]{0, 2, 4, 4},
            new int[]{1, 2, 0, 2},
            new double[]{803, 51000, 803, 50000}
        );
    }

    private AStar aStarFor(Graph graph) throws IOException {
        StraightLine line = new StraightLine(loadSmallCoordinates());
        return new AStar(line.smallestLimit(graph), line);
    }

    @Test
    void findsTheShorterOfTwoRoutes() throws IOException {
        Graph graph = twoRoutes();
        SearchResult result = aStarFor(graph).findShortestPath(graph, 0, 2);

        assertEquals(50803, result.distance(), 0.0001);
        assertArrayEquals(new int[]{0, 1, 2}, result.path());
    }

    @Test
    void agreesWithDijkstraOnEveryPair() throws IOException {
        Graph graph = twoRoutes();
        AStar aStar = aStarFor(graph);
        Dijkstra dijkstra = new Dijkstra();

        for (int s = 0; s < 3; s++) {
            for (int t = 0; t < 3; t++) {
                SearchResult expected = dijkstra.findShortestPath(graph, s, t);
                SearchResult actual = aStar.findShortestPath(graph, s, t);
                assertEquals(expected.distance(), actual.distance(), 0.0001, "from " + s + " to " + t);
                assertArrayEquals(expected.path(), actual.path(), "from " + s + " to " + t);
            }
        }
    }
}
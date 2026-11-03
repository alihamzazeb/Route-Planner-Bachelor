package dk.dtu.routeplanner.core.algorithms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static dk.dtu.routeplanner.core.algorithms.PathAssertions.assertValidPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

class SeededCheckTest {

    private static final Path NEW_YORK_GRAPH_PATH = Path.of("../data/USA-road-d.NY.gr");
    private static final Path NEW_YORK_COORDINATES_PATH = Path.of("../data/USA-road-d.NY.co");
    private static final long SEED = 42;
    private static final int SOURCES = 10;
    private static final int TARGETS_PER_SOURCE = 10;

    private static Graph graph;
    private static StraightLine line;

    @BeforeAll
    static void loadNewYork() throws IOException {
        assumeTrue(Files.isRegularFile(NEW_YORK_GRAPH_PATH), "New York graph not available");
        assumeTrue(Files.isRegularFile(NEW_YORK_COORDINATES_PATH), "New York coordinates not available");
        DimacsLoader loader = new DimacsLoader();
        graph = loader.load(NEW_YORK_GRAPH_PATH).forward();
        line = new StraightLine(loader.loadCoordinates(NEW_YORK_COORDINATES_PATH));
    }

    @Test
    void aStarAgreesWithDijkstraAndSettlesFewerNodes() {
        Random random = new Random(SEED);
        Dijkstra dijkstra = new Dijkstra();
        AStar aStar = new AStar(line.smallestLimit(graph), line);
        long dijkstraSettled = 0;
        long aStarSettled = 0;

        for (int i = 0; i < SOURCES; i++) {
            int source = random.nextInt(graph.nodeCount());
            SingleSourceResult all = dijkstra.computeShortestPaths(graph, source);
            for (int j = 0; j < TARGETS_PER_SOURCE; j++) {
                int target = random.nextInt(graph.nodeCount());
                String pair = source + " -> " + target;
                SearchResult reference = dijkstra.findShortestPath(graph, source, target);
                SearchResult actual = aStar.findShortestPath(graph, source, target);
                assertEquals(all.distance(target), reference.distance(), 1e-9, pair);
                assertEquals(all.distance(target), actual.distance(), 1e-9, pair);
                assertValidPath(graph, source, target, reference);
                assertValidPath(graph, source, target, actual);
                dijkstraSettled += reference.settledCounter();
                aStarSettled += actual.settledCounter();
            }
        }
        assertTrue(aStarSettled < dijkstraSettled,
                "A* settled " + aStarSettled + " nodes, Dijkstra " + dijkstraSettled);
    }
}

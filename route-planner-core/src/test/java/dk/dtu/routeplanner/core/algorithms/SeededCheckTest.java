package dk.dtu.routeplanner.core.algorithms;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        DimacsLoader loader = new DimacsLoader();
        graph = loader.load(NEW_YORK_GRAPH_PATH).forward();
        line = new StraightLine(loader.loadCoordinates(NEW_YORK_COORDINATES_PATH));
    }

    // Runs any point-to-point algorithm on the same 100 seeded pairs and checks every
    // distance against single-source Dijkstra. Returns how many nodes it settled in total.
    private static long checkAgainstDijkstra(PointToPointAlgorithm algorithm) {
        Random random = new Random(SEED);
        Dijkstra reference = new Dijkstra();
        long settled = 0;

        for (int i = 0; i < SOURCES; i++) {
            int source = random.nextInt(graph.nodeCount());
            SingleSourceResult all = reference.computeShortestPaths(graph, source);

            for (int j = 0; j < TARGETS_PER_SOURCE; j++) {
                int target = random.nextInt(graph.nodeCount());
                SearchResult result = algorithm.findShortestPath(graph, source, target);

                assertEquals(all.distance(target), result.distance(), 1e-9,
                        "from " + source + " to " + target);
                settled += result.settledCounter();
            }
        }
        return settled;
    }

    @Test
    void dijkstraVersionsAgreeOnSeededPairs() {
        checkAgainstDijkstra(new Dijkstra());
    }

    @Test
    void aStarAgreesWithDijkstraAndSettlesFewerNodes() {
        long dijkstraSettled = checkAgainstDijkstra(new Dijkstra());
        long aStarSettled = checkAgainstDijkstra(new AStar(line.smallestLimit(graph), line));

        System.out.println("settled on 100 seeded pairs, Dijkstra " + dijkstraSettled + ", A* " + aStarSettled);
        assertTrue(aStarSettled < dijkstraSettled);
    }
}
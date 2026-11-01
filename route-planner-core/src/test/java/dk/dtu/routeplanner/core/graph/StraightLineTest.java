package dk.dtu.routeplanner.core.graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.dtu.routeplanner.core.loader.DimacsLoader;

public class StraightLineTest {
    private static final Path NEW_YORK_GRAPH_PATH = Path.of("../data/USA-road-d.NY.gr");
    private static final Path NEW_YORK_COORDINATES_PATH = Path.of("../data/USA-road-d.NY.co");


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

    @Test
    void firstRoadDistance() throws IOException {
        StraightLine line = new StraightLine(loadSmallCoordinates());

        double result = line.distance(0, 1);

        assertEquals(722.9, result, 0.1);
    }


    @Test
    void secondPairDistance() throws IOException {
        StraightLine line = new StraightLine(loadSmallCoordinates());

        double result = line.distance(1, 2);

        assertEquals(38241.2, result, 0.1);
    }

    @Test
    void sameDistanceBothWays() throws IOException {
        StraightLine line = new StraightLine(loadSmallCoordinates());

        double result = line.distance(0, 1);
        double result2 = line.distance(1, 0);

        assertEquals(result, result2, 0.0001);
    }

    @Test
    void zeroDistanceToItself() throws IOException {
        StraightLine line = new StraightLine(loadSmallCoordinates());

        double result = line.distance(0, 0);

        assertEquals(0, result, 0.0001);
    }


    @Test
    void smallestLimitSkipsNodesAtTheSameSpot() throws IOException {
        Path file = tempDir.resolve("same-spot.co");
        Files.writeString(file, """
            p aux sp co 3
            v 1 -73530767 41085396
            v 2 -73530767 41085396
            v 3 -73530538 41086098
            """);
        StraightLine line = new StraightLine(new DimacsLoader().loadCoordinates(file));

        Graph graph = new CsrGraph(
            new int[]{0, 1, 2, 2},
            new int[]{1, 2},
            new double[]{0, 803}
        );

        double c = line.smallestLimit(graph);

        assertEquals(1.111, c, 0.001);
    }

    @Test
    void smallestLimitOnThreeRoads() throws IOException {
        StraightLine line = new StraightLine(loadSmallCoordinates());

        Graph graph = new CsrGraph(
            new int[]{0, 1, 3, 3},
            new int[]{1, 0, 2},
            new double[]{803, 803, 50000}
        );

        double c = line.smallestLimit(graph);

        assertEquals(1.111, c, 0.001);
    }

    @Test
    void smallestLimitOnNewYork() throws IOException {
        assumeTrue(Files.isRegularFile(NEW_YORK_GRAPH_PATH), "New York graph not available");
        assumeTrue(Files.isRegularFile(NEW_YORK_COORDINATES_PATH), "New York coordinates not available");
        DimacsLoader loader = new DimacsLoader();
        RoadNetwork network = loader.load(NEW_YORK_GRAPH_PATH);
        StraightLine line = new StraightLine(loader.loadCoordinates(NEW_YORK_COORDINATES_PATH));

        double c = line.smallestLimit(network.forward());
        System.out.println("smallest limit c for New York: " + c);

        assertTrue(c > 0);
    } 


    @Test
    void onlyTinyRoadsFallBelowOne() throws IOException {
        assumeTrue(Files.isRegularFile(NEW_YORK_GRAPH_PATH), "New York graph not available");
        assumeTrue(Files.isRegularFile(NEW_YORK_COORDINATES_PATH), "New York coordinates not available");
        DimacsLoader loader = new DimacsLoader();
        Graph graph = loader.load(NEW_YORK_GRAPH_PATH).forward();
        StraightLine line = new StraightLine(loader.loadCoordinates(NEW_YORK_COORDINATES_PATH));

        int belowOne = 0;
        double largestWeight = 0;

        for (int u = 0; u < graph.nodeCount(); u++) {
            for (int i = graph.edgeStart(u); i < graph.edgeEnd(u); i++) {
                int v = graph.edgeTarget(i);
                double weight = graph.edgeWeight(i);
                double d = line.distance(u, v);
                if (d == 0) {
                    continue;
                }
                double limit = weight / d;

                if (limit < 1.0) {
                    belowOne++;
                    largestWeight = Math.max(largestWeight, weight);
                    System.out.printf("a %d %d   weight %.0f   straight %.1f   limit %.3f%n",
                            u + 1, v + 1, weight, d, limit);
                }
            }
        }

        System.out.println("roads with a limit below 1.0: " + belowOne);
        assertEquals(24, belowOne);
        assertTrue(largestWeight <= 3);
    }
}

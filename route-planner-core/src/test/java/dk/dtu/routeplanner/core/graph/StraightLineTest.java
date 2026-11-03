package dk.dtu.routeplanner.core.graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import dk.dtu.routeplanner.core.loader.DimacsLoader;

public class StraightLineTest {
    private static final Path NEW_YORK_GRAPH_PATH = Path.of("../data/USA-road-d.NY.gr");
    private static final Path NEW_YORK_COORDINATES_PATH = Path.of("../data/USA-road-d.NY.co");


    private StraightLine smallCoordinates() {
        return new StraightLine(new Coordinates(
                new int[]{-73530767, -73530538, -73519366},
                new int[]{41085396, 41086098, 41048796}));
    }

    @Test
    void smallestLimitSkipsNodesAtTheSameSpot() {
        StraightLine line = new StraightLine(new Coordinates(
                new int[]{-73530767, -73530767, -73530538},
                new int[]{41085396, 41085396, 41086098}));

        Graph graph = new CsrGraph(
            new int[]{0, 1, 2, 2},
            new int[]{1, 2},
            new double[]{0, 803}
        );

        double c = line.smallestLimit(graph);

        assertEquals(1.111, c, 0.001);
    }

    @Test
    void scalesTheKnownRoadDistanceByTheTightestEdge() {
        StraightLine line = smallCoordinates();
        assertEquals(722.9, line.distance(0, 1), 0.1);

        Graph graph = new CsrGraph(
            new int[]{0, 1, 3, 3},
            new int[]{1, 0, 2},
            new double[]{803, 803, 50000}
        );

        double c = line.smallestLimit(graph);

        assertEquals(1.111, c, 0.001);
    }

    @Test
    void newYorkScalingMatchesTheKnownLimitAndOnlyTinyRoadsFallBelowOne() throws IOException {
        assumeTrue(Files.isRegularFile(NEW_YORK_GRAPH_PATH), "New York graph not available");
        assumeTrue(Files.isRegularFile(NEW_YORK_COORDINATES_PATH), "New York coordinates not available");
        DimacsLoader loader = new DimacsLoader();
        Graph graph = loader.load(NEW_YORK_GRAPH_PATH).forward();
        StraightLine line = new StraightLine(loader.loadCoordinates(NEW_YORK_COORDINATES_PATH));

        assertEquals(0.7973, line.smallestLimit(graph), 0.0001);

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
                }
            }
        }

        assertEquals(24, belowOne);
        assertTrue(largestWeight <= 3);
    }
}

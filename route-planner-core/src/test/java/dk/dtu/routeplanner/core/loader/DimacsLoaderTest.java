package dk.dtu.routeplanner.core.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;

public class DimacsLoaderTest {

    private static final Path NEW_YORK = Path.of("../data/USA-road-d.NY.gr");
    private static final Path NEW_YORK_COORDINATES_PATH = Path.of("../data/USA-road-d.NY.co");


    @TempDir
    Path tempDir;

    private RoadNetwork loadSmall() throws IOException {
        Path file = tempDir.resolve("small.gr");
        Files.writeString(file, """
            c a small example
            p sp 4 6
            a 1 2 10
            a 3 1 20
            a 2 4 30
            a 1 3 40
            a 4 2 50
            a 1 4 60
            """);
        return new DimacsLoader().load(file);
    }


    @Test
    void loadsSmallGraph() throws IOException {
        RoadNetwork network = loadSmall();
        Graph fwd = network.forward();
        Graph rev = network.reverse();

        assertEquals(4, fwd.nodeCount());

        assertEquals(0, fwd.edgeStart(0));
        assertEquals(3, fwd.edgeEnd(0));
        assertEquals(1, fwd.edgeTarget(0));
        assertEquals(2, fwd.edgeTarget(1));
        assertEquals(3, fwd.edgeTarget(2));
        assertEquals(10.0, fwd.edgeWeight(0));
        assertEquals(40.0, fwd.edgeWeight(1));
        assertEquals(60.0, fwd.edgeWeight(2));

        assertEquals(1, rev.edgeStart(1));
        assertEquals(3, rev.edgeEnd(1));
        assertEquals(0, rev.edgeTarget(1));
        assertEquals(3, rev.edgeTarget(2));
        assertReverseMatchesForward(network);
        assertReverseMatchesForward(new RoadNetwork(rev, fwd));
    }

    @Test
    void loadsNewYork() throws IOException {
        assumeTrue(Files.isRegularFile(NEW_YORK), "New York graph not available");
        RoadNetwork network = new DimacsLoader().load(NEW_YORK);
        assertEquals(264346, network.forward().nodeCount());
        assertEquals(264346, network.reverse().nodeCount());
    }

    private void assertReverseMatchesForward(RoadNetwork network) {
        Graph fwd = network.forward();
        Graph rev = network.reverse();

        for (int u = 0; u < fwd.nodeCount(); u++) {
            for (int i = fwd.edgeStart(u); i < fwd.edgeEnd(u); i++) {
                int v = fwd.edgeTarget(i);
                boolean found = false;
                for (int j = rev.edgeStart(v); j < rev.edgeEnd(v); j++) {
                    if (rev.edgeTarget(j) == u && rev.edgeWeight(j) == fwd.edgeWeight(i)) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found, "missing reverse edge " + u + " to " + v);
            }
        }
    }

    // coordinate stuff
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
    void loadsCoordinatesIntoSlotByNodeId() throws IOException {
        Coordinates coordinates = loadSmallCoordinates();

        assertEquals(3, coordinates.longitude().length);
        assertEquals(3, coordinates.latitude().length);

        assertEquals(-73530767, coordinates.longitude()[0]);
        assertEquals(-73530538, coordinates.longitude()[1]);
        assertEquals(-73519366, coordinates.longitude()[2]);

        assertEquals(41085396, coordinates.latitude()[0]);
        assertEquals(41086098, coordinates.latitude()[1]);
        assertEquals(41048796, coordinates.latitude()[2]);
    }

    @Test
    void loadsNewYorkCoordinates() throws IOException {
        assumeTrue(Files.isRegularFile(NEW_YORK_COORDINATES_PATH), "New York coordinates not available");
        Coordinates coordinates = new DimacsLoader().loadCoordinates(NEW_YORK_COORDINATES_PATH);
        assertEquals(264346, coordinates.longitude().length);
        assertEquals(264346, coordinates.latitude().length);

        assertEquals(-73530767, coordinates.longitude()[0]);
        assertEquals(41085396, coordinates.latitude()[0]);

        for (int node = 0; node < coordinates.longitude().length; node++) {
            assertNotEquals(0, coordinates.longitude()[node], "no longitude for node " + node);
            assertNotEquals(0, coordinates.latitude()[node], "no latitude for node " + node);
        }

    }

}
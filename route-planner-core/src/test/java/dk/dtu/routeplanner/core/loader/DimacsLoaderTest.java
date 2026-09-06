package dk.dtu.routeplanner.core.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;

public class DimacsLoaderTest {

    private static final Path NEW_YORK = Path.of("../data/USA-road-d.NY.gr");

    @TempDir
    Path tempDir;

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
    }

    @Test
    void smallGraphReverseMatchesForward() throws IOException {
        assertReverseMatchesForward(loadSmall());
    }

    @Test
    void loadsNewYork() throws IOException {
        RoadNetwork network = new DimacsLoader().load(NEW_YORK);
        assertEquals(264346, network.forward().nodeCount());
        assertEquals(264346, network.reverse().nodeCount());
    }

    @Test
    void newYorkReverseMatchesForward() throws IOException {
        assertReverseMatchesForward(new DimacsLoader().load(NEW_YORK));
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
}
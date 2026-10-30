package dk.dtu.routeplanner.core.algorithms.duan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuanNewYorkTest {

    @Test
    @EnabledIfSystemProperty(named = "duan.ny", matches = "true")
    void allVariantsMatchDijkstraOnNewYork() throws IOException {
        String configuredPath = System.getProperty("duan.graph");
        Path path = configuredPath == null ? Path.of("data/USA-road-d.NY.gr") : Path.of(configuredPath);
        if (configuredPath == null && !Files.isRegularFile(path)) {
            path = Path.of("../data/USA-road-d.NY.gr");
        }
        assertTrue(Files.isRegularFile(path), "Missing graph file: " + path.toAbsolutePath());
        Graph graph = new DimacsLoader().load(path).forward();
        Duan transformed = new Duan(graph, Duan.DegreeMode.TRANSFORM);
        Duan bounded = new Duan(graph, Duan.DegreeMode.BOUNDED);
        assertTrue(transformed.graphInfo().transformed());
        assertFalse(bounded.graphInfo().transformed());
        assertEquals(graph.nodeCount(), bounded.graphInfo().internalNodes());
        List<Duan> algorithms = List.of(new Duan(), new Duan(Duan.DegreeMode.BOUNDED), transformed, bounded);
        Random random = new Random(42);
        int[] sources = {0, random.nextInt(graph.nodeCount()), random.nextInt(graph.nodeCount())};
        for (int source : sources) {
            SingleSourceResult expected = new Dijkstra().computeShortestPaths(graph, source);
            for (Duan algorithm : algorithms) {
                SingleSourceResult actual = algorithm.computeShortestPaths(graph, source);
                for (int node = 0; node < graph.nodeCount(); node++) {
                    assertEquals(expected.distance(node), actual.distance(node), 0.0,
                            "source=" + source + ", node=" + node);
                }
                assertEquals(expected.settledCounter(), actual.settledCounter());
            }
        }
    }
}

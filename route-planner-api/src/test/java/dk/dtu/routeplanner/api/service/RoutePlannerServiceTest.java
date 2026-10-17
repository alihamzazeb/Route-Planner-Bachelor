package dk.dtu.routeplanner.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.dtu.routeplanner.api.dto.ComparisonResponse;
import dk.dtu.routeplanner.api.dto.ComparisonResult;
import dk.dtu.routeplanner.api.dto.RouteResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerServiceTest {

    @TempDir
    Path tempDir;

    private RoutePlannerService service;

    @BeforeEach
    void setUp() throws IOException {
        Path graph = tempDir.resolve("small.gr");
        Path coordinates = tempDir.resolve("small.co");

        Files.writeString(graph, """
                p sp 6 7
                a 1 2 2
                a 1 3 10
                a 2 3 2
                a 2 4 5
                a 3 4 1
                a 4 5 3
                a 5 2 1
                """);

        Files.writeString(coordinates, """
                p aux sp co 6
                v 1 -73990000 40750000
                v 2 -73989000 40750000
                v 3 -73988000 40750000
                v 4 -73987000 40750000
                v 5 -73986000 40750000
                v 6 -73985000 40750000
                """);

        service = new RoutePlannerService(graph.toString(), coordinates.toString());
    }

    @Test
    void allAlgorithmsReturnTheSameShortestDistance() {
        ComparisonResponse comparison = service.compareAlgorithms(0, 4, "alt");

        assertEquals(0, comparison.source());
        assertEquals(4, comparison.target());
        assertEquals(4, comparison.results().size());
        assertEquals(List.of("dijkstra", "bidirectional", "astar", "alt"),
                comparison.results().stream().map(ComparisonResult::algorithm).toList());

        for (ComparisonResult result : comparison.results()) {
            assertTrue(result.reachable(), result.algorithm());
            assertEquals(8.0, result.distance(), 1e-9, result.algorithm());
            assertTrue(result.pathNodes() > 0, result.algorithm());
            assertTrue(result.settledNodes() > 0, result.algorithm());
            assertTrue(result.relaxedEdges() >= 0, result.algorithm());
            assertTrue(Double.isFinite(result.timeMs()), result.algorithm());
            assertTrue(result.timeMs() >= 0, result.algorithm());
        }
    }

    @Test
    void selectedRouteContainsThePathAndCoordinates() {
        for (String algorithm : List.of("dijkstra", "bidirectional", "astar", "alt")) {
            ComparisonResponse comparison = service.compareAlgorithms(0, 4, algorithm);
            RouteResponse route = comparison.route();

            assertEquals(algorithm, route.getAlgorithm());
            assertTrue(route.isReachable());
            assertEquals(8.0, route.getDistance(), 1e-9);
            assertArrayEquals(new int[]{0, 1, 2, 3, 4}, route.getPath());
            assertEquals(route.getPath().length, route.getCoordinates().length);
            assertArrayEquals(new double[]{-73.99, 40.75}, route.getCoordinates()[0], 1e-9);
            assertArrayEquals(new double[]{-73.986, 40.75}, route.getCoordinates()[4], 1e-9);

            ComparisonResult selected = comparison.results().stream()
                    .filter(result -> result.algorithm().equals(algorithm))
                    .findFirst()
                    .orElseThrow();

            assertEquals(route.getDistance(), selected.distance());
            assertEquals(route.getPath().length, selected.pathNodes());
            assertEquals(route.getSettledNodes(), selected.settledNodes());
            assertEquals(route.getRelaxedEdges(), selected.relaxedEdges());
        }
    }

    @Test
    void sourceEqualsTargetReturnsOneCoordinate() {
        ComparisonResponse comparison = service.compareAlgorithms(2, 2, "astar");

        for (ComparisonResult result : comparison.results()) {
            assertTrue(result.reachable());
            assertEquals(0.0, result.distance(), 1e-9);
            assertEquals(1, result.pathNodes());
        }

        assertArrayEquals(new int[]{2}, comparison.route().getPath());
        assertEquals(1, comparison.route().getCoordinates().length);
        assertArrayEquals(new double[]{-73.988, 40.75},
                comparison.route().getCoordinates()[0], 1e-9);
    }

    @Test
    void unreachableTargetHasNoPathOrCoordinates() {
        ComparisonResponse comparison = service.compareAlgorithms(0, 5, "bidirectional");

        for (ComparisonResult result : comparison.results()) {
            assertFalse(result.reachable(), result.algorithm());
            assertNull(result.distance());
            assertEquals(0, result.pathNodes());
        }

        assertFalse(comparison.route().isReachable());
        assertNull(comparison.route().getDistance());
        assertEquals(0, comparison.route().getPath().length);
        assertEquals(0, comparison.route().getCoordinates().length);
    }

    @Test
    void repeatedComparisonsReturnConsistentResults() {
        ComparisonResponse first = service.compareAlgorithms(0, 4, "dijkstra");
        ComparisonResponse second = service.compareAlgorithms(0, 4, "dijkstra");

        assertArrayEquals(first.route().getPath(), second.route().getPath());
        assertEquals(first.route().getDistance(), second.route().getDistance());

        for (int i = 0; i < first.results().size(); i++) {
            ComparisonResult a = first.results().get(i);
            ComparisonResult b = second.results().get(i);

            assertEquals(a.algorithm(), b.algorithm());
            assertEquals(a.distance(), b.distance());
            assertEquals(a.settledNodes(), b.settledNodes());
            assertEquals(a.relaxedEdges(), b.relaxedEdges());
        }
    }
}

package dk.dtu.routeplanner.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dk.dtu.routeplanner.api.dto.ComparisonResponse;
import dk.dtu.routeplanner.api.dto.ComparisonResult;
import dk.dtu.routeplanner.api.dto.SingleSourceComparisonResponse;
import dk.dtu.routeplanner.api.dto.SingleSourceResponse;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerServiceTest {

    @TempDir
    Path tempDir;

    private RoutePlannerService service;

    // the file counts nodes from 1, the service from 0
    // shortest path 0 -> 4 is 0-1-2-3-4 with length 8, node 5 has no edges
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
    void allAlgorithmsFindTheShortestRoute() {
        for (String selected : List.of("dijkstra", "bidirectional", "astar", "alt")) {
            ComparisonResponse comparison = service.compareAlgorithms(0, 4, selected);
            assertEquals(0, comparison.source());
            assertEquals(4, comparison.target());
            assertEquals(List.of("dijkstra", "bidirectional", "astar", "alt"),
                    comparison.results().stream().map(ComparisonResult::algorithm).toList());
            for (ComparisonResult result : comparison.results()) {
                assertTrue(result.reachable(), result.algorithm());
                assertEquals(8.0, result.distance(), result.algorithm());
                assertEquals(5, result.pathNodes(), result.algorithm());
                if (result.algorithm().equals(selected)) {
                    assertEquals(result.settledNodes(), comparison.route().getSettledNodes(), selected);
                    assertEquals(result.relaxedEdges(), comparison.route().getRelaxedEdges(), selected);
                }
            }
            assertEquals(selected, comparison.route().getAlgorithm());
            assertArrayEquals(new int[]{0, 1, 2, 3, 4}, comparison.route().getPath());
            double[][] coordinates = comparison.route().getCoordinates();
            assertEquals(5, coordinates.length);
            for (int node = 0; node < coordinates.length; node++) {
                assertArrayEquals(new double[]{-73.99 + node * 0.001, 40.75}, coordinates[node], 1e-9);
            }
        }
    }

    @Test
    void unreachableTargetGivesNoRoute() {
        ComparisonResponse comparison = service.compareAlgorithms(0, 5, "dijkstra");

        assertEquals(4, comparison.results().size());
        for (ComparisonResult result : comparison.results()) {
            assertFalse(result.reachable(), result.algorithm());
            assertNull(result.distance(), result.algorithm());
            assertEquals(0, result.pathNodes(), result.algorithm());
        }

        assertNull(comparison.route().getDistance());
        assertEquals(0, comparison.route().getPath().length);
        assertEquals(0, comparison.route().getCoordinates().length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dijkstra", "duan"})
    void singleSourceSummariesAndComparisonsAgree(String algorithm) {
        SingleSourceResponse response = service.computeSingleSource(0, algorithm);

        assertEquals(algorithm, response.algorithm());
        assertEquals(0, response.source());
        assertEquals(6, response.nodeCount());
        assertEquals(5, response.reachableNodes());
        assertEquals(1, response.unreachableNodes());
        assertEquals(5, response.settledNodes());
        assertEquals(-73.99, response.sourceLongitude(), 1e-9);
        assertEquals(40.75, response.sourceLatitude(), 1e-9);

        SingleSourceComparisonResponse comparison = service.compareSingleSource(0, algorithm);
        assertEquals(algorithm, comparison.result().algorithm());
        assertEquals(5, comparison.result().reachableNodes());
        assertEquals(1, comparison.result().unreachableNodes());
        assertEquals(List.of("dijkstra", "duan"),
                comparison.results().stream().map(SingleSourceResponse::algorithm).toList());
        assertEquals(6, comparison.comparedNodes());
        assertEquals(0, comparison.distanceMismatches());
    }

    @Test
    void countsDistanceMismatches() {
        SingleSourceResult dijkstra = distances(0, 2, 4, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        SingleSourceResult duan = distances(0, 2, 5, Double.POSITIVE_INFINITY, 7);

        assertEquals(2, RoutePlannerService.countDistanceMismatches(dijkstra, duan, 5));
        // Within tolerance, outside tolerance, and finite-to-infinite disagreement.
        assertEquals(2, RoutePlannerService.countDistanceMismatches(
                distances(1, 2, 3), distances(1 + 0.5e-6, 2 + 2e-6, Double.POSITIVE_INFINITY), 3));
    }

    private SingleSourceResult distances(double... values) {
        return new SingleSourceResult(0, 0, values);
    }
}

package dk.dtu.routeplanner.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        ComparisonResponse comparison = service.compareAlgorithms(0, 4, "dijkstra");

        for (ComparisonResult result : comparison.results()) {
            assertEquals(8.0, result.distance(), result.algorithm());
        }

        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, comparison.route().getPath());
        assertArrayEquals(new double[]{-73.99, 40.75}, comparison.route().getCoordinates()[0], 1e-9);
    }

    @Test
    void unreachableTargetGivesNoRoute() {
        ComparisonResponse comparison = service.compareAlgorithms(0, 5, "dijkstra");

        for (ComparisonResult result : comparison.results()) {
            assertFalse(result.reachable(), result.algorithm());
        }

        assertNull(comparison.route().getDistance());
        assertEquals(0, comparison.route().getPath().length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dijkstra", "duan"})
    void singleSourceCountsReachableNodes(String algorithm) {
        SingleSourceResponse response = service.computeSingleSource(0, algorithm);

        assertEquals(5, response.reachableNodes());
        assertEquals(1, response.unreachableNodes());
    }

    @Test
    void duanAgreesWithDijkstra() {
        SingleSourceComparisonResponse comparison = service.compareSingleSource(0, "duan");

        assertEquals("duan", comparison.result().algorithm());
        assertEquals(0, comparison.distanceMismatches());
    }

    @Test
    void countsDistanceMismatches() {
        SingleSourceResult dijkstra = distances(0, 2, 4, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        SingleSourceResult duan = distances(0, 2, 5, Double.POSITIVE_INFINITY, 7);

        assertEquals(2, RoutePlannerService.countDistanceMismatches(dijkstra, duan, 5));
    }

    private SingleSourceResult distances(double... values) {
        return new SingleSourceResult(0, 0, values);
    }
}

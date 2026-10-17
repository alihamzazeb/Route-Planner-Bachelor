package dk.dtu.routeplanner.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RouteResponseTest {

    @Test
    void pathAndCoordinatesAreDefensivelyCopied() {
        int[] path = {10, 20};
        double[][] coordinates = {{-73.99, 40.75}, {-73.98, 40.76}};

        RouteResponse response = new RouteResponse(
                "dijkstra", 10, 20, true, 1250.0,
                path, coordinates, 5, 10
        );

        path[0] = 99;
        coordinates[0][0] = 0;

        int[] returnedPath = response.getPath();
        double[][] returnedCoordinates = response.getCoordinates();
        returnedPath[0] = 99;
        returnedCoordinates[0][0] = 0;

        assertArrayEquals(new int[]{10, 20}, response.getPath());
        assertArrayEquals(new double[]{-73.99, 40.75}, response.getCoordinates()[0]);
    }
}

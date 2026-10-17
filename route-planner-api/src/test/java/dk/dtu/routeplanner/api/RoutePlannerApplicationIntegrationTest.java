
package dk.dtu.routeplanner.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class RoutePlannerApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureDataset(DynamicPropertyRegistry registry) {
        registry.add("routeplanner.graph.path",
                () -> findDataFile("USA-road-d.NY.gr"));

        registry.add("routeplanner.coordinates.path",
                () -> findDataFile("USA-road-d.NY.co"));
    }

    private static String findDataFile(String filename) {
        for (Path directory : List.of(Path.of("data"), Path.of("../data"))) {
            Path file = directory.resolve(filename);

            if (Files.isRegularFile(file)) {
                return file.toAbsolutePath().normalize().toString();
            }
        }

        throw new IllegalStateException("Missing dataset file: " + filename);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dijkstra", "bidirectional", "astar", "alt"})
    void allAlgorithmsFindAValidRoute(String algorithm) throws Exception {

        String response = mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "500")
                        .param("algorithm", algorithm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value(algorithm))
                .andExpect(jsonPath("$.reachable").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number distance = JsonPath.read(response, "$.distance");
        List<Integer> path = JsonPath.read(response, "$.path");
        List<?> coordinates = JsonPath.read(response, "$.coordinates");

        assertTrue(distance.doubleValue() > 0);
        assertEquals(10, path.get(0));
        assertEquals(500, path.get(path.size() - 1));
        assertEquals(path.size(), coordinates.size());
    }

    @Test
    void allAlgorithmsReturnTheSameShortestDistance() throws Exception {

        String response = mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "500")
                        .param("algorithm", "alt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.route.algorithm").value("alt"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> algorithms = JsonPath.read(
                response, "$.results[*].algorithm"
        );

        List<Number> distances = JsonPath.read(
                response, "$.results[*].distance"
        );

        assertEquals(
                List.of("dijkstra", "bidirectional", "astar", "alt"),
                algorithms
        );

        double expected = distances.get(0).doubleValue();

        for (Number distance : distances) {
            assertEquals(expected, distance.doubleValue(), 1e-6);
        }

        Number selectedDistance = JsonPath.read(response, "$.route.distance");

        assertEquals(expected, selectedDistance.doubleValue(), 1e-6);

        List<Integer> path = JsonPath.read(response, "$.route.path");
        List<?> coordinates = JsonPath.read(response, "$.route.coordinates");

        assertEquals(10, path.get(0));
        assertEquals(500, path.get(path.size() - 1));
        assertEquals(path.size(), coordinates.size());
    }

    @Test
    void sourceEqualsTargetReturnsZeroDistance() throws Exception {

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "10")
                        .param("algorithm", "alt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.distance").value(0.0))
                .andExpect(jsonPath("$.path.length()").value(1))
                .andExpect(jsonPath("$.path[0]").value(10))
                .andExpect(jsonPath("$.coordinates.length()").value(1));
    }

    @Test
    void invalidNodeReturnsBadRequest() throws Exception {

        mockMvc.perform(get("/api/routes")
                        .param("source", "264346")
                        .param("target", "500")
                        .param("algorithm", "dijkstra"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Invalid source node: 264346"));
    }
}

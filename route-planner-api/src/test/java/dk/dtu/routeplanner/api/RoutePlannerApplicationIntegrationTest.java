package dk.dtu.routeplanner.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("hasNewYorkData")
@SpringBootTest
@AutoConfigureMockMvc
class RoutePlannerApplicationIntegrationTest {

    private static final double DISTANCE_FROM_10_TO_500 = 57378.0;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureDataset(DynamicPropertyRegistry registry) {
        registry.add("routeplanner.graph.path", () -> findDataFile("USA-road-d.NY.gr"));
        registry.add("routeplanner.coordinates.path", () -> findDataFile("USA-road-d.NY.co"));
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

    static boolean hasNewYorkData() {
        try {
            findDataFile("USA-road-d.NY.gr");
            findDataFile("USA-road-d.NY.co");
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"dijkstra", "bidirectional", "astar", "alt"})
    void everyAlgorithmFindsTheShortestRoute(String algorithm) throws Exception {
        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "500")
                        .param("algorithm", algorithm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distance").value(DISTANCE_FROM_10_TO_500))
                .andExpect(jsonPath("$.algorithm").value(algorithm))
                .andExpect(jsonPath("$.source").value(10))
                .andExpect(jsonPath("$.target").value(500))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.path[0]").value(10))
                .andExpect(jsonPath("$.path[-1]").value(500));
    }

    @Test
    void allAlgorithmsAgreeOnTheDistance() throws Exception {
        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[*].distance").value(everyItem(is(DISTANCE_FROM_10_TO_500))));
    }

    @Test
    void duanAgreesWithDijkstraOnEveryNode() throws Exception {
        mockMvc.perform(get("/api/single-source/compare")
                        .param("source", "10")
                        .param("algorithm", "duan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparedNodes").value(264346))
                .andExpect(jsonPath("$.distanceMismatches").value(0));
    }
}

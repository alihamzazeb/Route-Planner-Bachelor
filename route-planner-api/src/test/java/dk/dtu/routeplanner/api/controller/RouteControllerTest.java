package dk.dtu.routeplanner.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dk.dtu.routeplanner.api.service.RoutePlannerService;
import dk.dtu.routeplanner.core.model.SearchResult;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoutePlannerService service;

    @BeforeEach
    void setUp() {
        when(service.nodeCount()).thenReturn(100);
        doCallRealMethod().when(service).toRouteResponse(anyString(), anyInt(), anyInt(), any(SearchResult.class));
    }

    // different settled counts tell us which algorithm was called
    @ParameterizedTest
    @CsvSource({"dijkstra, 120", "bidirectional, 40", "astar, 25", "alt, 15"})
    void routeUsesTheRequestedAlgorithm(String algorithm, int settledNodes) throws Exception {
        when(service.findResult(10, 50)).thenReturn(route(120));
        when(service.findBidirectionalResult(10, 50)).thenReturn(route(40));
        when(service.findAStarResult(10, 50)).thenReturn(route(25));
        when(service.findALTResult(10, 50)).thenReturn(route(15));

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", algorithm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value(algorithm))
                .andExpect(jsonPath("$.source").value(10))
                .andExpect(jsonPath("$.target").value(50))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.distance").value(1250.0))
                .andExpect(jsonPath("$.path[0]").value(10))
                .andExpect(jsonPath("$.path[1]").value(20))
                .andExpect(jsonPath("$.path[2]").value(50))
                .andExpect(jsonPath("$.path.length()").value(3))
                .andExpect(jsonPath("$.relaxedEdges").value(37))
                .andExpect(jsonPath("$.settledNodes").value(settledNodes));
    }

    @Test
    void unreachableTargetHasNoDistance() throws Exception {
        when(service.findResult(10, 50)).thenReturn(new SearchResult(Double.POSITIVE_INFINITY, new int[0], 30, 70));

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(false))
                .andExpect(jsonPath("$.distance").value(nullValue()))
                .andExpect(jsonPath("$.path").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "-5, 50, Invalid source node: -5",
            "100, 50, Invalid source node: 100",
            "10, -1, Invalid target node: -1",
            "10, 100, Invalid target node: 100",
            "3000000000, 50, Invalid source node: 3000000000"
    })
    void invalidNodeReturnsBadRequest(String source, String target, String message) throws Exception {
        mockMvc.perform(get("/api/routes")
                        .param("source", source)
                        .param("target", target))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/routes", "/api/routes/compare"})
    void unknownAlgorithmReturnsBadRequest(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint)
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", "duan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown algorithm: duan"));
    }

    private SearchResult route(int settledNodes) {
        return new SearchResult(1250.0, new int[]{10, 20, 50}, settledNodes, 37);
    }
}

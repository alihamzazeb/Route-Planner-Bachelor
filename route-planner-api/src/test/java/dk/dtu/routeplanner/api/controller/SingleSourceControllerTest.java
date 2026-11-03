package dk.dtu.routeplanner.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dk.dtu.routeplanner.api.dto.SingleSourceResponse;
import dk.dtu.routeplanner.api.service.RoutePlannerService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SingleSourceController.class)
class SingleSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoutePlannerService service;

    @BeforeEach
    void setUp() {
        when(service.nodeCount()).thenReturn(100);
    }

    @Test
    void singleSourceReturnsTheResult() throws Exception {
        SingleSourceResponse response = new SingleSourceResponse("duan", 10, 100, 90, 10, 90, 220, 1.5, -73.99, 40.75);
        when(service.computeSingleSource(10, "duan")).thenReturn(response);

        mockMvc.perform(get("/api/single-source")
                        .param("source", "10")
                        .param("algorithm", "duan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("duan"))
                .andExpect(jsonPath("$.source").value(10))
                .andExpect(jsonPath("$.nodeCount").value(100))
                .andExpect(jsonPath("$.settledNodes").value(90))
                .andExpect(jsonPath("$.relaxedEdges").value(220))
                .andExpect(jsonPath("$.sourceLongitude").value(-73.99))
                .andExpect(jsonPath("$.sourceLatitude").value(40.75))
                .andExpect(jsonPath("$.reachableNodes").value(90))
                .andExpect(jsonPath("$.unreachableNodes").value(10));
    }

    @Test
    void invalidSourceReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/single-source")
                        .param("source", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid source node: 100"));
    }

    @Test
    void unknownAlgorithmReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/single-source")
                        .param("source", "10")
                        .param("algorithm", "astar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown single-source algorithm: astar"));
    }

    @Test
    void targetParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/single-source/compare")
                        .param("source", "10")
                        .param("target", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Single-source requests take a source only; remove the target parameter."));
    }
}

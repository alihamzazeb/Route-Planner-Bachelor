
package dk.dtu.routeplanner.api.controller;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dk.dtu.routeplanner.api.service.RoutePlannerService;
import dk.dtu.routeplanner.core.model.SearchResult;

@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoutePlannerService service;

    @Test
    void dijkstraReturnsCorrectJson() throws Exception {

        when(service.nodeCount()).thenReturn(100);

        when(service.findResult(10, 50)).thenReturn(
                new SearchResult(
                        1250.0,
                        new int[]{10, 20, 50},
                        120,
                        310
                )
        );

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("dijkstra"))
                .andExpect(jsonPath("$.distance").value(1250.0))
                .andExpect(jsonPath("$.path[0]").value(10))
                .andExpect(jsonPath("$.path[2]").value(50))
                .andExpect(jsonPath("$.settledNodes").value(120));
    }

    @Test
    void invalidSourceReturnsBadRequest() throws Exception {

        when(service.nodeCount()).thenReturn(100);

        mockMvc.perform(get("/api/routes")
                        .param("source", "-5")
                        .param("target", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Invalid source node: -5"));
    }





@Test
void bidirectionalReturnsCorrectJson() throws Exception {
    when(service.nodeCount()).thenReturn(100);

    when(service.findBidirectionalResult(10, 50)).thenReturn(
            new SearchResult(1250.0, new int[]{10, 20, 50}, 40, 90)
    );

    mockMvc.perform(get("/api/routes")
                    .param("source", "10")
                    .param("target", "50")
                    .param("algorithm", "bidirectional"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.algorithm").value("bidirectional"))
            .andExpect(jsonPath("$.distance").value(1250.0))
            .andExpect(jsonPath("$.settledNodes").value(40));
}

@Test
void aStarReturnsCorrectJson() throws Exception {
    when(service.nodeCount()).thenReturn(100);

    when(service.findAStarResult(10, 50)).thenReturn(
            new SearchResult(1250.0, new int[]{10, 20, 50}, 25, 60)
    );

    mockMvc.perform(get("/api/routes")
                    .param("source", "10")
                    .param("target", "50")
                    .param("algorithm", "astar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.algorithm").value("astar"))
            .andExpect(jsonPath("$.distance").value(1250.0))
            .andExpect(jsonPath("$.settledNodes").value(25));
}

    @Test
    void altReturnsCorrectJson() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        when(service.findALTResult(10, 50)).thenReturn(
                new SearchResult(1250.0, new int[]{10, 20, 50}, 15, 35)
        );

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", "alt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("alt"))
                .andExpect(jsonPath("$.distance").value(1250.0))
                .andExpect(jsonPath("$.settledNodes").value(15));
    }



    @Test
    void unreachableTargetReturnsCorrectJson() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        when(service.findResult(10, 50)).thenReturn(
                new SearchResult(
                        Double.POSITIVE_INFINITY,
                        new int[0],
                        30,
                        70
                )
        );

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(false))
                .andExpect(jsonPath("$.distance").value(
                        org.hamcrest.Matchers.nullValue()
                ))
                .andExpect(jsonPath("$.path").isEmpty());
    }

    @Test
    void invalidTargetReturnsBadRequest() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Invalid target node: 100"));
    }

    @Test
    void unknownAlgorithmReturnsBadRequest() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        mockMvc.perform(get("/api/routes")
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Unknown algorithm: unknown"));
    }


}

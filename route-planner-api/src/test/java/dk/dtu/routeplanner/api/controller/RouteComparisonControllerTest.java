package dk.dtu.routeplanner.api.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dk.dtu.routeplanner.api.dto.ComparisonResponse;
import dk.dtu.routeplanner.api.dto.ComparisonResult;
import dk.dtu.routeplanner.api.dto.RouteResponse;
import dk.dtu.routeplanner.api.service.RoutePlannerService;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
class RouteComparisonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoutePlannerService service;

    private ComparisonResponse comparison(String selectedAlgorithm) {
        RouteResponse route = new RouteResponse(
                selectedAlgorithm, 10, 50, true, 1250.0,
                new int[]{10, 20, 50},
                new double[][]{
                        {-73.99, 40.75},
                        {-73.98, 40.76},
                        {-73.97, 40.77}
                },
                15, 35
        );

        return new ComparisonResponse(10, 50, route, List.of(
                new ComparisonResult("dijkstra", true, 1250.0, 3, 120, 310, 1.5),
                new ComparisonResult("bidirectional", true, 1250.0, 3, 40, 90, 1.2),
                new ComparisonResult("astar", true, 1250.0, 3, 25, 60, 0.9),
                new ComparisonResult("alt", true, 1250.0, 3, 15, 35, 0.6)
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dijkstra", "bidirectional", "astar", "alt"})
    void comparisonIncludesSelectedRouteAndAllAlgorithms(String algorithm) throws Exception {
        when(service.nodeCount()).thenReturn(100);
        when(service.compareAlgorithms(10, 50, algorithm)).thenReturn(comparison(algorithm));

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", algorithm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value(10))
                .andExpect(jsonPath("$.target").value(50))
                .andExpect(jsonPath("$.route.algorithm").value(algorithm))
                .andExpect(jsonPath("$.route.reachable").value(true))
                .andExpect(jsonPath("$.route.distance").value(1250.0))
                .andExpect(jsonPath("$.route.path.length()").value(3))
                .andExpect(jsonPath("$.route.path[0]").value(10))
                .andExpect(jsonPath("$.route.path[2]").value(50))
                .andExpect(jsonPath("$.route.coordinates.length()").value(3))
                .andExpect(jsonPath("$.route.coordinates[0][0]").value(-73.99))
                .andExpect(jsonPath("$.route.coordinates[2][1]").value(40.77))
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[0].algorithm").value("dijkstra"))
                .andExpect(jsonPath("$.results[1].algorithm").value("bidirectional"))
                .andExpect(jsonPath("$.results[2].algorithm").value("astar"))
                .andExpect(jsonPath("$.results[3].algorithm").value("alt"))
                .andExpect(jsonPath("$.results[0].settledNodes").value(120))
                .andExpect(jsonPath("$.results[3].relaxedEdges").value(35))
                .andExpect(jsonPath("$.results[3].timeMs").value(0.6));

        verify(service).compareAlgorithms(10, 50, algorithm);
    }

    @Test
    void comparisonDefaultsToDijkstra() throws Exception {
        when(service.nodeCount()).thenReturn(100);
        when(service.compareAlgorithms(10, 50, "dijkstra")).thenReturn(comparison("dijkstra"));

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.algorithm").value("dijkstra"));

        verify(service).compareAlgorithms(10, 50, "dijkstra");
    }

    @Test
    void comparisonAcceptsUppercaseAlgorithm() throws Exception {
        when(service.nodeCount()).thenReturn(100);
        when(service.compareAlgorithms(10, 50, "alt")).thenReturn(comparison("alt"));

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", "ALT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.algorithm").value("alt"));
    }

    @Test
    void comparisonRejectsInvalidSource() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "-1")
                        .param("target", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid source node: -1"));

        verify(service, never()).compareAlgorithms(anyInt(), anyInt(), anyString());
    }

    @Test
    void comparisonRejectsInvalidTarget() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid target node: 100"));

        verify(service, never()).compareAlgorithms(anyInt(), anyInt(), anyString());
    }

    @Test
    void comparisonRejectsUnknownAlgorithm() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown algorithm: unknown"));

        verify(service, never()).compareAlgorithms(anyInt(), anyInt(), anyString());
    }

    @Test
    void comparisonRequiresSourceAndTarget() throws Exception {
        mockMvc.perform(get("/api/routes/compare")
                        .param("target", "50"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void comparisonReturnsUnreachableRoute() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        RouteResponse route = new RouteResponse(
                "alt", 10, 50, false, null,
                new int[0], new double[0][], 30, 70
        );

        when(service.compareAlgorithms(10, 50, "alt")).thenReturn(
                new ComparisonResponse(10, 50, route, List.of(
                        new ComparisonResult("dijkstra", false, null, 0, 40, 90, 1.5),
                        new ComparisonResult("bidirectional", false, null, 0, 20, 45, 1.2),
                        new ComparisonResult("astar", false, null, 0, 35, 80, 0.9),
                        new ComparisonResult("alt", false, null, 0, 30, 70, 0.5)
                ))
        );

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "50")
                        .param("algorithm", "alt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.reachable").value(false))
                .andExpect(jsonPath("$.route.distance").value(nullValue()))
                .andExpect(jsonPath("$.route.path").isEmpty())
                .andExpect(jsonPath("$.route.coordinates").isEmpty())
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.results[3].reachable").value(false))
                .andExpect(jsonPath("$.results[3].distance").value(nullValue()));
    }

    @Test
    void comparisonHandlesSourceEqualsTarget() throws Exception {
        when(service.nodeCount()).thenReturn(100);

        RouteResponse route = new RouteResponse(
                "dijkstra", 10, 10, true, 0.0,
                new int[]{10}, new double[][]{{-73.99, 40.75}}, 1, 0
        );

        when(service.compareAlgorithms(10, 10, "dijkstra")).thenReturn(
                new ComparisonResponse(10, 10, route, List.of(
                        new ComparisonResult("dijkstra", true, 0.0, 1, 1, 0, 0.1),
                        new ComparisonResult("bidirectional", true, 0.0, 1, 1, 0, 0.1),
                        new ComparisonResult("astar", true, 0.0, 1, 1, 0, 0.1),
                        new ComparisonResult("alt", true, 0.0, 1, 1, 0, 0.1)
                ))
        );

        mockMvc.perform(get("/api/routes/compare")
                        .param("source", "10")
                        .param("target", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.reachable").value(true))
                .andExpect(jsonPath("$.route.distance").value(0.0))
                .andExpect(jsonPath("$.route.path[0]").value(10))
                .andExpect(jsonPath("$.route.coordinates.length()").value(1))
                .andExpect(jsonPath("$.results.length()").value(4));
    }
}

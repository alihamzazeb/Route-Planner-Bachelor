
package dk.dtu.routeplanner.api.controller;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.dtu.routeplanner.api.dto.ComparisonResponse;
import dk.dtu.routeplanner.api.dto.RouteResponse;
import dk.dtu.routeplanner.api.service.RoutePlannerService;
import dk.dtu.routeplanner.api.validation.RouteRequestValidator;
import dk.dtu.routeplanner.core.model.SearchResult;

@RestController
public class RouteController {
    private static final Set<String> ALGORITHMS = Set.of("dijkstra", "bidirectional", "astar", "alt");
    private final RoutePlannerService service;

    public RouteController(RoutePlannerService service) {
        this.service = service;
    }

    @GetMapping("/api/routes")
    public RouteResponse findRoute(@RequestParam("source") int source, @RequestParam("target") int target,@RequestParam(name = "algorithm", defaultValue = "dijkstra") String algorithm) {
        RouteRequestValidator.validateNodes(source, target, service.nodeCount());

        String selectedAlgorithm = validateAlgorithm(algorithm);

        SearchResult result = switch (selectedAlgorithm) {
            case "dijkstra" -> service.findResult(source, target);
            case "bidirectional" -> service.findBidirectionalResult(source, target);
            case "astar" -> service.findAStarResult(source, target);
            case "alt" -> service.findALTResult(source, target);
            default -> throw new IllegalStateException("Unrecognized algorithm");
        };

        return service.toRouteResponse(selectedAlgorithm, source, target, result);
    }

    @GetMapping("/api/routes/compare")
    public ComparisonResponse compareRoutes(
            @RequestParam("source") int source,
            @RequestParam("target") int target,
            @RequestParam(name = "algorithm", defaultValue = "dijkstra") String algorithm
    ) {
        RouteRequestValidator.validateNodes(source, target, service.nodeCount());
        return service.compareAlgorithms(source, target, validateAlgorithm(algorithm));
    }

    private static String validateAlgorithm(String algorithm) {
        String selected = algorithm.toLowerCase(Locale.ROOT);

        if (!ALGORITHMS.contains(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown algorithm: " + algorithm);
        }

        return selected;
    }
}

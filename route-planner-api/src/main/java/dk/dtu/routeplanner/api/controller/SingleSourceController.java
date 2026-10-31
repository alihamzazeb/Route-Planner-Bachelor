package dk.dtu.routeplanner.api.controller;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.dtu.routeplanner.api.dto.SingleSourceComparisonResponse;
import dk.dtu.routeplanner.api.dto.SingleSourceResponse;
import dk.dtu.routeplanner.api.service.RoutePlannerService;
import dk.dtu.routeplanner.api.validation.RouteRequestValidator;

@RestController
public class SingleSourceController {

    private final RoutePlannerService service;

    public SingleSourceController(RoutePlannerService service) {
        this.service = service;
    }

    @GetMapping("/api/single-source")
    public SingleSourceResponse computeDistances(
            @RequestParam("source") int source,
            @RequestParam(name = "algorithm", defaultValue = "dijkstra") String algorithm,
            @RequestParam(name = "target", required = false) String target
    ) {
        String selectedAlgorithm = validateRequest(source, algorithm, target);
        return service.computeSingleSource(source, selectedAlgorithm);
    }

    @GetMapping("/api/single-source/compare")
    public SingleSourceComparisonResponse compareAlgorithms(
            @RequestParam("source") int source,
            @RequestParam(name = "algorithm", defaultValue = "dijkstra") String algorithm,
            @RequestParam(name = "target", required = false) String target
    ) {
        String selectedAlgorithm = validateRequest(source, algorithm, target);
        return service.compareSingleSource(source, selectedAlgorithm);
    }

    private String validateRequest(int source, String algorithm, String target) {
        RouteRequestValidator.validateSource(source, service.nodeCount());

        if (target != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Single-source requests take a source only; remove the target parameter.");
        }

        String selectedAlgorithm = algorithm.toLowerCase(Locale.ROOT);
        if (!Set.of("dijkstra", "duan").contains(selectedAlgorithm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown single-source algorithm: " + algorithm);
        }

        return selectedAlgorithm;
    }
}

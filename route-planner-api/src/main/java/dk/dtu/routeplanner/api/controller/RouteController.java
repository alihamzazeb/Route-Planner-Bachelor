package dk.dtu.routeplanner.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.dtu.routeplanner.api.dto.RouteResponse;
import dk.dtu.routeplanner.api.service.RoutePlannerService;
import dk.dtu.routeplanner.core.model.SearchResult;

@RestController
public class RouteController {

    private final RoutePlannerService service;

    public RouteController(RoutePlannerService service) {
        this.service = service;
    }

    @GetMapping("/api/routes")
    public RouteResponse findRoute(@RequestParam("source") int source, @RequestParam("target") int target) {

        SearchResult result = service.findResult(source, target);

        boolean reachable = Double.isFinite(result.distance());

        Double distance = reachable ? result.distance() : null;

        return new RouteResponse(
                "dijkstra",
                source,
                target,
                reachable,
                distance,
                result.path(),
                result.settledCounter(),
                result.relaxedCounter()
        );
    }

}
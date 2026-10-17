package dk.dtu.routeplanner.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.dtu.routeplanner.api.dto.RouteResponse;
import dk.dtu.routeplanner.api.service.RoutePlannerService;
import dk.dtu.routeplanner.api.validation.RouteRequestValidator;
import dk.dtu.routeplanner.core.model.SearchResult;

@RestController
public class RouteController {

    private final RoutePlannerService service;

    public RouteController(RoutePlannerService service) {
        this.service = service;
    }

    

    @GetMapping("/api/routes")
    public RouteResponse findRoute(@RequestParam("source") int source, @RequestParam("target") int target, @RequestParam(name = "algorithm", defaultValue = "dijkstra") String algorithm) {

        RouteRequestValidator.validateNodes(source, target, service.nodeCount());
        
        SearchResult result;

        if (algorithm.equalsIgnoreCase("dijkstra")){
            result= service.findResult(source, target);
        }

        else if (algorithm.equalsIgnoreCase("bidirectional")) {
            result = service.findBidirectionalResult(source, target);

        } else if (algorithm.equalsIgnoreCase("astar")) {
            result = service.findAStarResult(source, target);
        } else if (algorithm.equalsIgnoreCase("alt")) {
            result = service.findALTResult(source, target);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown algorithm: " + algorithm);
        }

        boolean reachable = Double.isFinite(result.distance());

        Double distance = reachable ? result.distance() : null;
        
        int[] path = result.path();

        return new RouteResponse(
                algorithm.toLowerCase(java.util.Locale.ROOT),
                source,
                target,
                reachable,
                distance,
                path,
                service.pathCoordinates(path),
                result.settledCounter(),
                result.relaxedCounter()
        );
    }

}
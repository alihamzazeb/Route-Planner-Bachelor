package dk.dtu.routeplanner.api.service;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dk.dtu.routeplanner.core.algorithms.BidirectionalDijkstra;
import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;

@Service
public class RoutePlannerService {

    private final RoadNetwork network;
    private final Dijkstra dijkstra = new Dijkstra();
    private final BidirectionalDijkstra bidirectional;


    public RoutePlannerService( @Value("${routeplanner.graph.path}") String graphPath) throws IOException {

        // Load the road network here
        DimacsLoader loader = new DimacsLoader();
        this.network = loader.load(Path.of(graphPath));
        this.bidirectional = new BidirectionalDijkstra(network);

    }


    public SearchResult findResult(int source, int target) {

        return dijkstra.findShortestPath(network.forward(), source, target);

    }

    public SearchResult findBidirectionalResult(int source, int target) {

        return bidirectional.findShortestPath(network.forward(), source, target);
    }
}
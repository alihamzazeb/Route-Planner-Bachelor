package dk.dtu.routeplanner.api.service;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

@Service
public class RoutePlannerService {

    private final RoadNetwork network;

    public RoutePlannerService( @Value("${routeplanner.graph.path}") String graphPath) throws IOException {

        // Load the road network here
        DimacsLoader loader = new DimacsLoader();
        this.network = loader.load(Path.of(graphPath));

    }
}
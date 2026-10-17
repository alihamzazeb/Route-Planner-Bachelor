package dk.dtu.routeplanner.api.service;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dk.dtu.routeplanner.core.algorithms.AStar;
import dk.dtu.routeplanner.core.algorithms.BidirectionalDijkstra;
import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;
import dk.dtu.routeplanner.core.model.SearchResult;

@Service
public class RoutePlannerService {

    private final RoadNetwork network;
    private final Dijkstra dijkstra = new Dijkstra();
    private final BidirectionalDijkstra bidirectional;
    private final AStar aStar;
    private final AStar alt;
    private final Coordinates coordinates;



    public RoutePlannerService(@Value("${routeplanner.graph.path}") String graphPath, @Value("${routeplanner.coordinates.path}") String coordinatesPath) throws IOException {

        // Load the road network here
        DimacsLoader loader = new DimacsLoader();
        this.network = loader.load(Path.of(graphPath));
        this.bidirectional = new BidirectionalDijkstra(network);

        // A*
        this.coordinates = loader.loadCoordinates(Path.of(coordinatesPath));

        StraightLine straightLine = new StraightLine(coordinates);

        double c = straightLine.smallestLimit(network.forward());

        this.aStar = new AStar(c, straightLine);

        // ALT
        int[] landmarks = new RandomLandmarkSelector().select(network.forward(), 4, 42L);

        LandmarkPreprocessingResult preprocessing = new LandmarkPreprocessor(network).preprocess(landmarks);

        this.alt = new AStar(new LandmarkHeuristic(preprocessing));

    }


    public SearchResult findResult(int source, int target) {

        return dijkstra.findShortestPath(network.forward(), source, target);

    }

    public SearchResult findBidirectionalResult(int source, int target) {

        return bidirectional.findShortestPath(network.forward(), source, target);
    }


    public SearchResult findAStarResult(int source, int target) {
        return aStar.findShortestPath(network.forward(), source, target);
    }

    public SearchResult findALTResult(int source, int target) {
        return alt.findShortestPath(network.forward(), source, target);
    }

    public int nodeCount() {
        return network.forward().nodeCount();
    }


    public double[] nodeCoordinates(int node) {

        // the map expects this, not the one we have in our file
        double longitude = coordinates.longitude()[node] / 1000000.0;
        double latitude = coordinates.latitude()[node] / 1000000.0;

        return new double[]{longitude, latitude};
    }

    public double[][] pathCoordinates(int[] path) {
        double[][] result = new double[path.length][];

        for (int i = 0; i < path.length; i++) {
            result[i] = nodeCoordinates(path[i]);
        }

        return result;
    }
}
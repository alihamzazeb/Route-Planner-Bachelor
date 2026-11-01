package dk.dtu.routeplanner.api.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dk.dtu.routeplanner.api.dto.ComparisonResponse;
import dk.dtu.routeplanner.api.dto.ComparisonResult;
import dk.dtu.routeplanner.api.dto.RouteResponse;
import dk.dtu.routeplanner.api.dto.SingleSourceComparisonResponse;
import dk.dtu.routeplanner.api.dto.SingleSourceResponse;
import dk.dtu.routeplanner.core.algorithms.AStar;
import dk.dtu.routeplanner.core.algorithms.BidirectionalDijkstra;
import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.algorithms.PointToPointAlgorithm;
import dk.dtu.routeplanner.core.algorithms.SingleSourceAlgorithm;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.algorithms.duan.Duan;
import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

@Service
public class RoutePlannerService {

    private final RoadNetwork network;
    private final Dijkstra dijkstra = new Dijkstra();
    private final BidirectionalDijkstra bidirectional;
    private final AStar aStar;
    private final AStar alt;
    private final Duan duan;
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

        // Duan, the graph is prepared once here like the ALT landmarks, so it is not part of the query time
        this.duan = new Duan(network.forward(), Duan.DegreeMode.BOUNDED);

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

    public RouteResponse toRouteResponse(String algorithm, int source, int target, SearchResult result) {
        boolean reachable = Double.isFinite(result.distance());
        int[] path = result.path();

        return new RouteResponse(
                algorithm,
                source,
                target,
                reachable,
                reachable ? result.distance() : null,
                path,
                pathCoordinates(path),
                result.settledCounter(),
                result.relaxedCounter()
        );
    }


    public ComparisonResponse compareAlgorithms(int source, int target, String selectedAlgorithm) {

        MeasuredRoute dijkstraRun = measureAlgorithm("dijkstra", dijkstra, source, target);
        MeasuredRoute bidirectionalRun = measureAlgorithm("bidirectional", bidirectional, source, target);
        MeasuredRoute aStarRun = measureAlgorithm("astar", aStar, source, target);
        MeasuredRoute altRun = measureAlgorithm("alt", alt, source, target);

        MeasuredRoute selected = switch (selectedAlgorithm) {
            case "dijkstra" -> dijkstraRun;
            case "bidirectional" -> bidirectionalRun;
            case "astar" -> aStarRun;
            case "alt" -> altRun;
            default -> throw new IllegalArgumentException("Unknown algorithm: " + selectedAlgorithm);
        };

        RouteResponse route = toRouteResponse(selectedAlgorithm, source, target, selected.result());

        return new ComparisonResponse(
                source,
                target,
                route,
                List.of(
                        dijkstraRun.comparison(),
                        bidirectionalRun.comparison(),
                        aStarRun.comparison(),
                        altRun.comparison()
                )
        );
    }

    private MeasuredRoute measureAlgorithm(
            String name,
            PointToPointAlgorithm algorithm,
            int source,
            int target
    ) {
        long start = System.nanoTime();

        SearchResult result = algorithm.findShortestPath(network.forward(), source, target);

        double timeMs = (System.nanoTime() - start) / 1000000.0;
        boolean reachable = Double.isFinite(result.distance());

        ComparisonResult comparison = new ComparisonResult(
                name,
                reachable,
                reachable ? result.distance() : null,
                result.path().length,
                result.settledCounter(),
                result.relaxedCounter(),
                timeMs
        );

        return new MeasuredRoute(result, comparison);
    }

    private record MeasuredRoute(
            SearchResult result,
            ComparisonResult comparison
    ) {
    }


    public SingleSourceResponse computeSingleSource(int source, String algorithm) {

        SingleSourceAlgorithm selected = switch (algorithm) {
            case "dijkstra" -> dijkstra;
            case "duan" -> duan;
            default -> throw new IllegalArgumentException("Unknown single-source algorithm: " + algorithm);
        };

        return measureSingleSource(algorithm, selected, source).summary();
    }

    public SingleSourceComparisonResponse compareSingleSource(int source, String selectedAlgorithm) {

        MeasuredSingleSource dijkstraRun = measureSingleSource("dijkstra", dijkstra, source);
        MeasuredSingleSource duanRun = measureSingleSource("duan", duan, source);

        MeasuredSingleSource selected = switch (selectedAlgorithm) {
            case "dijkstra" -> dijkstraRun;
            case "duan" -> duanRun;
            default -> throw new IllegalArgumentException("Unknown single-source algorithm: " + selectedAlgorithm);
        };

        return new SingleSourceComparisonResponse(
                source,
                selected.summary(),
                List.of(dijkstraRun.summary(), duanRun.summary()),
                nodeCount(),
                countDistanceMismatches(dijkstraRun.result(), duanRun.result(), nodeCount())
        );
    }

    private MeasuredSingleSource measureSingleSource(String name, SingleSourceAlgorithm algorithm, int source) {
        long start = System.nanoTime();

        SingleSourceResult result = algorithm.computeShortestPaths(network.forward(), source);

        double timeMs = (System.nanoTime() - start) / 1000000.0;

        int nodeCount = nodeCount();
        int reachable = 0;

        for (int node = 0; node < nodeCount; node++) {
            if (Double.isFinite(result.distance(node))) {
                reachable++;
            }
        }

        double[] location = nodeCoordinates(source);

        SingleSourceResponse summary = new SingleSourceResponse(
                name,
                source,
                nodeCount,
                reachable,
                nodeCount - reachable,
                result.settledCounter(),
                result.relaxedCounter(),
                timeMs,
                location[0],
                location[1]
        );

        return new MeasuredSingleSource(result, summary);
    }

    // unreachable nodes count as equal when both are infinite, reached nodes when within 1e-6
    static int countDistanceMismatches(SingleSourceResult expected, SingleSourceResult actual, int nodeCount) {
        int mismatches = 0;

        for (int node = 0; node < nodeCount; node++) {
            double a = expected.distance(node);
            double b = actual.distance(node);
            boolean equal = a == b || (Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= 1e-6);

            if (!equal) {
                mismatches++;
            }
        }

        return mismatches;
    }

    private record MeasuredSingleSource(
            SingleSourceResult result,
            SingleSourceResponse summary
    ) {
    }


}

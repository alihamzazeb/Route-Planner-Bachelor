
package dk.dtu.routeplanner.benchmark;

import java.io.IOException;
import java.nio.file.Path;

import dk.dtu.routeplanner.core.algorithms.AStar;
import dk.dtu.routeplanner.core.algorithms.BidirectionalDijkstra;
import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.algorithms.PointToPointAlgorithm;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;
import dk.dtu.routeplanner.core.model.SearchResult;

public class BenchmarkRunner {

    public static void main(String[] args) throws IOException {
        DimacsLoader loader = new DimacsLoader();

        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));
        Coordinates coordinates = loader.loadCoordinates(Path.of("data/USA-road-d.NY.co"));

        Graph graph = network.forward();

        StraightLine straightLine = new StraightLine(coordinates);
        double c = straightLine.smallestLimit(graph);

        // save the selected landmarks
        int[] landmarks = new RandomLandmarkSelector().select(graph, 4, 42L);

        LandmarkPreprocessingResult preprocessing = new LandmarkPreprocessor(network).preprocess(landmarks);

        // will use them in the for loop, later
        PointToPointAlgorithm[] algorithms = {
                new Dijkstra(),
                new BidirectionalDijkstra(network),
                new AStar(c, straightLine),
                new AStar(new LandmarkHeuristic(preprocessing))
        };

        String[] names = {
                "Dijkstra",
                "Bidirectional Dijkstra",
                "A*",
                "ALT"
        };

        int[][] queries = Workloads.generatePairs(graph.nodeCount(), 100, 42L);

        System.out.println("New York road network");
        System.out.println("Nodes: " + graph.nodeCount());

        int unreachableCount = 0;
        
        for (int[] query : queries) {
            int source = query[0];
            int target = query[1];

            System.out.println("\nRoute " + source + " -> " + target);

            double expectedDistance = Double.NaN;

            // go through each algorithm we stored earlier
            for (int i = 0; i < algorithms.length; i++) {
                long start = System.nanoTime();

                SearchResult result =
                        algorithms[i].findShortestPath(graph, source, target);

                long elapsed = System.nanoTime() - start;

                validatePath(graph, source, target, result);

                // use djikstra as reference result that we compared with the others, to see if they are the same
                if (i == 0) {
                    expectedDistance = result.distance();

                    if (!Double.isFinite(expectedDistance)) {
                        unreachableCount++;
                    }
                } 
                // check if we get the same distance as the one we got with dijkstra
                else if (!sameDistance(expectedDistance, result.distance())) {
                    throw new IllegalStateException(names[i] + " returned an incorrect distance for " + source + " -> " + target);
                }

                System.out.printf(
                        "%-24s distance: %-10.0f settled: %-8d relaxed: %-8d time: %.3f ms%n",
                        names[i],
                        result.distance(),
                        result.settledCounter(),
                        result.relaxedCounter(),
                        elapsed / 1000000.0
                );
            }

            System.out.println("All four algorithms agree.");
        }
        
        System.out.println("Unreachable pairs: " + unreachableCount);

    }

    private static boolean sameDistance(double a, double b) {
        // if the node is not reachable they remain infinity. so we check for that
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return a == b;
        }

        return Math.abs(a - b) <= 0.000001;
    }

    // checks if the path is valid or not
    private static void validatePath(Graph graph, int source, int target,SearchResult result) {
        int[] path = result.path();

        if (!Double.isFinite(result.distance())) {
            if (path.length != 0) {
                throw new IllegalStateException("Unreachable route has a path");
            }
            return;
        }

        if (path.length == 0 || path[0] != source || path[path.length - 1] != target) {
            throw new IllegalStateException("Invalid path endpoints");
        }

        double totalDistance = 0;

        for (int i = 0; i < path.length - 1; i++) {
            int from = path[i];
            int to = path[i + 1];
            double edgeWeight = Double.POSITIVE_INFINITY;

            for (int edge = graph.edgeStart(from); edge < graph.edgeEnd(from); edge++) {
                if (graph.edgeTarget(edge) == to) {
                    edgeWeight = Math.min(edgeWeight, graph.edgeWeight(edge));
                }
            }

            if (!Double.isFinite(edgeWeight)) {
                throw new IllegalStateException(
                        "Missing edge: " + from + " -> " + to
                );
            }

            totalDistance += edgeWeight;
        }

        if (!sameDistance(totalDistance, result.distance())) {
            throw new IllegalStateException("Path distance does not match result");
        }
    }
}

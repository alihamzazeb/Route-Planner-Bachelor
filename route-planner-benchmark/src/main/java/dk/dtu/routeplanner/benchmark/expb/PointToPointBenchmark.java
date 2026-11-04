package dk.dtu.routeplanner.benchmark.expb;

import java.io.IOException;
import java.nio.file.Path;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

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

@State(Scope.Benchmark)
public class PointToPointBenchmark {
    private Graph graph;
    private int[][] queries;
    private PointToPointAlgorithm algorithm;

    @Param({"dijkstra", "bidirectional", "astar", "alt"})
    public String algorithmName;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        DimacsLoader loader = new DimacsLoader();

        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));

        graph = network.forward();

        queries = Workloads.readPairs(BenchmarkConfig.NY_WORKLOAD, graph.nodeCount(), BenchmarkConfig.PAIR_COUNT);

        switch (algorithmName) {
            case "dijkstra" -> {
                algorithm = new Dijkstra();
            }

            case "bidirectional" -> {
                algorithm = new BidirectionalDijkstra(network);
            }

            case "astar" -> {
                Coordinates coordinates = loader.loadCoordinates(Path.of("data/USA-road-d.NY.co"));

                StraightLine straightLine = new StraightLine(coordinates);
                double c = straightLine.smallestLimit(graph);

                algorithm = new AStar(c, straightLine);
            }

            case "alt" -> {
                int[] landmarks = new RandomLandmarkSelector().select(graph, BenchmarkConfig.LANDMARK_COUNT, BenchmarkConfig.LANDMARK_SEED);

                LandmarkPreprocessingResult preprocessing = new LandmarkPreprocessor(network).preprocess(landmarks);

                algorithm = new AStar(new LandmarkHeuristic(preprocessing));
            }

            default -> throw new IllegalArgumentException(
                    "Unknown algorithm: " + algorithmName
            );
        }
    }

    @Benchmark
    @OperationsPerInvocation(BenchmarkConfig.PAIR_COUNT)
    public void search(Blackhole blackhole) {
        for (int[] query : queries) {
            SearchResult result = algorithm.findShortestPath(graph, query[0], query[1]);

            blackhole.consume(result);
        }
    }
}

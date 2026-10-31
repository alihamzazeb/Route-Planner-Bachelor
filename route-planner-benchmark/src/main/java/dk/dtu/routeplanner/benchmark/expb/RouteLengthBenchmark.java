
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
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

@State(Scope.Benchmark)
public class RouteLengthBenchmark {

    @Param({"dijkstra", "bidirectional", "astar", "alt"})
    public String algorithmName;

    private Graph graph;
    private PointToPointAlgorithm algorithm;

    private int[][] shortPairs;
    private int[][] mediumPairs;
    private int[][] longPairs;

    @Setup(Level.Trial)
    public void setup() throws IOException {

        DimacsLoader loader = new DimacsLoader();

        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));

        graph = network.forward();

        shortPairs = Workloads.readPairs(
                Path.of("benchmark-data/workloads/NY-short-seed42.csv"),
                graph.nodeCount(),
                33
        );

        mediumPairs = Workloads.readPairs(
                Path.of("benchmark-data/workloads/NY-medium-seed42.csv"),
                graph.nodeCount(),
                33
        );

        longPairs = Workloads.readPairs(
                Path.of("benchmark-data/workloads/NY-long-seed42.csv"),
                graph.nodeCount(),
                34
        );

        algorithm = switch (algorithmName) {

            case "dijkstra" -> new Dijkstra();

            case "bidirectional" -> new BidirectionalDijkstra(network);

            case "astar" -> {
                StraightLine straightLine = new StraightLine(loader.loadCoordinates(Path.of("data/USA-road-d.NY.co"))
                );

                yield new AStar(straightLine.smallestLimit(graph), straightLine);
            }

            case "alt" -> {
                int[] landmarks =
                        new RandomLandmarkSelector().select(
                                graph,
                                BenchmarkConfig.LANDMARK_COUNT,
                                BenchmarkConfig.LANDMARK_SEED
                        );

                yield new AStar(
                        new LandmarkHeuristic(
                                new LandmarkPreprocessor(network)
                                        .preprocess(landmarks)
                        )
                );
            }

            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        };
    }

    @Benchmark
    @OperationsPerInvocation(33)
    public void shortRoutes(Blackhole blackhole) {
        search(shortPairs, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(33)
    public void mediumRoutes(Blackhole blackhole) {
        search(mediumPairs, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(34)
    public void longRoutes(Blackhole blackhole) {
        search(longPairs, blackhole);
    }

    private void search(int[][] pairs, Blackhole blackhole) {
        for (int[] pair : pairs) {
            blackhole.consume(algorithm.findShortestPath(graph, pair[0], pair[1]));
        }
    }
}

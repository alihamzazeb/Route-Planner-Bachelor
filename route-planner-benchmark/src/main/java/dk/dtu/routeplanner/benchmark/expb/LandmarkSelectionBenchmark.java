
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
import dk.dtu.routeplanner.core.algorithms.alt.GeographicFarthestLandmarkSelector;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

@State(Scope.Benchmark)
public class LandmarkSelectionBenchmark {

    @Param({"random", "farthest"})
    public String selectionMethod;

    @Param({"4", "8"})
    public int landmarkCount;

    private Graph graph;
    private int[][] queries;
    private AStar alt;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        DimacsLoader loader = new DimacsLoader();
        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));
        graph = network.forward();

        queries = Workloads.readPairs(
                BenchmarkConfig.NY_WORKLOAD,
                graph.nodeCount(),
                BenchmarkConfig.PAIR_COUNT
        );

        int[] landmarks;

        if (selectionMethod.equals("random")) {
            landmarks = new RandomLandmarkSelector().select(
                    graph,
                    landmarkCount,
                    BenchmarkConfig.LANDMARK_SEED
            );

        } else if (selectionMethod.equals("farthest")) {

            StraightLine straightLine = new StraightLine(loader.loadCoordinates(Path.of("data/USA-road-d.NY.co"))
            );

            landmarks = new GeographicFarthestLandmarkSelector()
                    .select(
                            graph,
                            straightLine,
                            landmarkCount,
                            BenchmarkConfig.LANDMARK_SEED
                    );

        } else {
            throw new IllegalArgumentException("Unknown selection method: " + selectionMethod);
        }

        var prepared = new LandmarkPreprocessor(network).preprocess(landmarks);

        alt = new AStar(new LandmarkHeuristic(prepared));
    }

    @Benchmark
    @OperationsPerInvocation(BenchmarkConfig.PAIR_COUNT)
    public void search(Blackhole blackhole) {
        for (int[] pair : queries) {
            blackhole.consume(alt.findShortestPath(graph, pair[0], pair[1]));
        }
    }
}

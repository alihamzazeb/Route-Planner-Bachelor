
package dk.dtu.routeplanner.benchmark;

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
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;

@State(Scope.Benchmark)
public class LandmarkSensitivityBenchmark {

    @Param({"1", "2", "4", "8"})
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

        int[] landmarks = new RandomLandmarkSelector().select(
                graph,
                landmarkCount,
                BenchmarkConfig.LANDMARK_SEED
        );

        var data = new LandmarkPreprocessor(network).preprocess(landmarks);
        alt = new AStar(new LandmarkHeuristic(data));
    }

    @Benchmark
    @OperationsPerInvocation(BenchmarkConfig.PAIR_COUNT)
    public void search(Blackhole blackhole) {
        for (int[] query : queries) {
            SearchResult result = alt.findShortestPath(graph, query[0], query[1]);
            blackhole.consume(result);
        }
    }
}

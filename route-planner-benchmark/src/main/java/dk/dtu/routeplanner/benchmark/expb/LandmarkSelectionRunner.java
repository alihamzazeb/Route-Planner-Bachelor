package dk.dtu.routeplanner.benchmark.expb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dk.dtu.routeplanner.core.algorithms.AStar;
import dk.dtu.routeplanner.core.algorithms.alt.GeographicFarthestLandmarkSelector;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;
import dk.dtu.routeplanner.core.model.SearchResult;

public final class LandmarkSelectionRunner {
    private static final String[] METHODS = {"random", "farthest"};
    private static final int[] COUNTS = {4, 8};
    private static final int TRIALS = 5;

    private LandmarkSelectionRunner() {}

    public static void main(String[] args) throws IOException {
        DimacsLoader loader = new DimacsLoader();
        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));

        Graph graph = network.forward();
        StraightLine line = new StraightLine(loader.loadCoordinates(Path.of("data/USA-road-d.NY.co")));

        int[][] pairs = Workloads.readPairs(
                BenchmarkConfig.NY_WORKLOAD,
                graph.nodeCount(),
                BenchmarkConfig.PAIR_COUNT
        );

        double[] expected = RouteChecks.referenceDistances(graph, pairs);

        LandmarkPreprocessor preprocessor = new LandmarkPreprocessor(network);

        List<String> setupRows = new ArrayList<>();
        List<String> counterRows = new ArrayList<>();

        setupRows.add("method,landmarks,selection_ms,preprocessing_ms,total_setup_ms,table_mib");

        counterRows.add("method,landmarks,queries,mean_settled,mean_relaxed");

        for (int count : COUNTS) {
            for (String method : METHODS) {
                // Warm up before measuring setup costs.
                int[] warmup = select(method, graph, line, count);

                preprocessor.preprocess(warmup);

                double selectionSumMs = 0;
                double preprocessingSumMs = 0;

                LandmarkPreprocessingResult prepared = null;

                for (int trial = 0; trial < TRIALS; trial++) {
                    long start = System.nanoTime();

                    int[] landmarks = select(method, graph, line, count);

                    selectionSumMs += (System.nanoTime() - start) / 1000000.0;

                    start = System.nanoTime();

                    prepared = preprocessor.preprocess(landmarks);

                    preprocessingSumMs += (System.nanoTime() - start) / 1000000.0;
                }

                AStar alt = new AStar(new LandmarkHeuristic(prepared));

                long settled = 0;
                long relaxed = 0;

                for (int i = 0; i < pairs.length; i++) {
                    SearchResult result = alt.findShortestPath(
                            graph,
                            pairs[i][0],
                            pairs[i][1]
                    );

                    if (!Double.isFinite(result.distance()) || Math.abs(result.distance() - expected[i]) > 1e-6) {
                        throw new IllegalStateException(
                                "Incorrect result: " + method
                                        + ", " + count
                                        + " landmarks, pair " + i
                        );
                    }

                    settled += result.settledCounter();
                    relaxed += result.relaxedCounter();
                }

                double selectMs = selectionSumMs / TRIALS;

                double preprocessMs = preprocessingSumMs / TRIALS;

                double tableMiB =
                        2.0 * count * graph.nodeCount()
                                * Double.BYTES
                                / (1024.0 * 1024.0);

                setupRows.add(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%.3f",
                        method, count, selectMs, preprocessMs, selectMs + preprocessMs, tableMiB));
                counterRows.add(String.format(Locale.ROOT, "%s,%d,%d,%.2f,%.2f",
                        method, count, pairs.length,
                        settled / (double) pairs.length, relaxed / (double) pairs.length));

                System.out.printf(Locale.ROOT,
                        "%s, %d landmarks: setup %.1f ms, "
                                + "settled %.0f, relaxed %.0f%n",
                        method, count, selectMs + preprocessMs,
                        settled / (double) pairs.length, relaxed / (double) pairs.length);
            }
        }

        Path results = Path.of("benchmark-data/results");
        Files.createDirectories(results);

        Files.write(results.resolve("NY-landmark-selection-setup.csv"),
                setupRows, StandardCharsets.UTF_8);
        Files.write(results.resolve("NY-landmark-selection-counters.csv"),
                counterRows, StandardCharsets.UTF_8);

        System.out.println("All four configurations matched Dijkstra on 100 routes.");
    }

    private static int[] select(String method, Graph graph, StraightLine line, int count) {
        return switch (method) {
            case "random" ->
                    new RandomLandmarkSelector().select(
                            graph,
                            count,
                            BenchmarkConfig.LANDMARK_SEED
                    );

            case "farthest" ->
                    new GeographicFarthestLandmarkSelector()
                            .select(
                                    graph,
                                    line,
                                    count,
                                    BenchmarkConfig.LANDMARK_SEED
                            );

            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }
}

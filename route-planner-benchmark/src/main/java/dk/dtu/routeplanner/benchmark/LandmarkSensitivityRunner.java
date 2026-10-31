
package dk.dtu.routeplanner.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dk.dtu.routeplanner.core.algorithms.AStar;
import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;

public final class LandmarkSensitivityRunner {

    private static final int[] COUNTS = {1, 2, 4, 8};
    private static final int TRIALS = 5;

    private LandmarkSensitivityRunner() {
    }

    public static void main(String[] args) throws IOException {
        DimacsLoader loader = new DimacsLoader();
        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));

        Graph graph = network.forward();

        int[][] queries = Workloads.readPairs(
                BenchmarkConfig.NY_WORKLOAD,
                graph.nodeCount(),
                BenchmarkConfig.PAIR_COUNT
        );

        Dijkstra dijkstra = new Dijkstra();

        double[] expectedDistances = new double[queries.length];

        for (int i = 0; i < queries.length; i++) {
            expectedDistances[i] = dijkstra.findShortestPath(graph, queries[i][0], queries[i][1]).distance();

            if (!Double.isFinite(expectedDistances[i])) {
                throw new IllegalStateException("Unreachable pair at index " + i);
            }
        }

        LandmarkPreprocessor preprocessor = new LandmarkPreprocessor(network);

        List<String> preprocessingRows = new ArrayList<>();
        List<String> counterRows = new ArrayList<>();

        preprocessingRows.add("landmarks,trial,preprocessing_ms,distance_tables_mib");

        counterRows.add("landmarks,queries,mean_settled,mean_relaxed,distance_tables_mib");

        for (int count : COUNTS) {

            int[] landmarks = new RandomLandmarkSelector().select(
                    graph,
                    count,
                    BenchmarkConfig.LANDMARK_SEED
            );

            // Warm up before collecting preprocessing measurements.
            preprocessor.preprocess(landmarks);

            double tableMiB = 2.0 * count * graph.nodeCount() * Double.BYTES / (1024.0 * 1024.0);

            AStar alt = null;

            for (int trial = 1; trial <= TRIALS; trial++) {

                long start = System.nanoTime();

                var data = preprocessor.preprocess(landmarks);

                double elapsedMs = (System.nanoTime() - start) / 1000000.0;

                preprocessingRows.add(String.format(
                        Locale.ROOT,
                        "%d,%d,%.3f,%.3f",
                        count,
                        trial,
                        elapsedMs,
                        tableMiB
                ));

                if (trial == TRIALS) {
                    alt = new AStar(new LandmarkHeuristic(data));
                }
            }

            long settledTotal = 0;
            long relaxedTotal = 0;

            for (int i = 0; i < queries.length; i++) {

                SearchResult result = alt.findShortestPath(
                        graph,
                        queries[i][0],
                        queries[i][1]
                );

                if (Math.abs(
                        result.distance() - expectedDistances[i]
                ) > 1e-6) {
                    throw new IllegalStateException("Wrong distance with " + count + " landmarks, pair " + i);
                }

                settledTotal += result.settledCounter();
                relaxedTotal += result.relaxedCounter();
            }

            double meanSettled = settledTotal / (double) queries.length;

            double meanRelaxed = relaxedTotal / (double) queries.length;

            counterRows.add(String.format(
                    Locale.ROOT,
                    "%d,%d,%.2f,%.2f,%.3f",
                    count,
                    queries.length,
                    meanSettled,
                    meanRelaxed,
                    tableMiB
            ));

            System.out.printf(
                    Locale.ROOT,
                    "%d landmarks: %.0f settled, %.0f relaxed on average, "
                            + "%.2f MiB distance tables%n",
                    count,
                    meanSettled,
                    meanRelaxed,
                    tableMiB
            );
        }

        Path results = Path.of("benchmark-data/results");
        Files.createDirectories(results);

        Files.write(
                results.resolve("NY-landmarks-preprocessing.csv"),
                preprocessingRows,
                StandardCharsets.UTF_8
        );

        Files.write(
                results.resolve("NY-landmarks-counters.csv"),
                counterRows,
                StandardCharsets.UTF_8
        );

        System.out.println("All 100 routes verified for each landmark count.");

        System.out.println("Saved preprocessing and search-effort CSV files.");
    }
}

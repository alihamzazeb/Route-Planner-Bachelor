
package dk.dtu.routeplanner.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import dk.dtu.routeplanner.core.algorithms.AStar;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

public class PreprocessingRunner {

    private static volatile PreparedData keepAlive;
    private record PreparedData(
        RoadNetwork network,
        Coordinates coordinates,
        AStar astar,
        AStar alt
) {}

    public static void main(String[] args) throws Exception {

        int trial = Integer.parseInt(args[0]);
        DimacsLoader loader = new DimacsLoader();

        // time the load graph
        long start = System.nanoTime();
        RoadNetwork network =loader.load(Path.of("data/USA-road-d.NY.gr"));
        double graphMs = ms(start);

        // time the load coordinates
        start = System.nanoTime();
        Coordinates coordinates =loader.loadCoordinates(Path.of("data/USA-road-d.NY.co"));
        double coordinatesMs = ms(start);

        // time the preparation for A*
        start = System.nanoTime();
        StraightLine straightLine = new StraightLine(coordinates);
        double c = straightLine.smallestLimit(network.forward());
        AStar astar = new AStar(c, straightLine);
        double astarMs = ms(start);

        // Select landmarks selection
        start = System.nanoTime();
        int[] landmarks = new RandomLandmarkSelector().select(
                network.forward(),
                BenchmarkConfig.LANDMARK_COUNT,
                BenchmarkConfig.LANDMARK_SEED
        );
        double selectionMs = ms(start);

        // time the preprocess of ALT
        start = System.nanoTime();
        var preprocessing =new LandmarkPreprocessor(network).preprocess(landmarks);
        AStar alt = new AStar(new LandmarkHeuristic(preprocessing));
        double altMs = ms(start);

        // the prepared data
        keepAlive = new PreparedData(network, coordinates, astar, alt);

        // save the results to a csv
        Path file = Path.of("benchmark-data/results/NY-preprocessing.csv");

        if (Files.notExists(file)) {
            Files.writeString(file,
                    "trial, graph_load_ms, coordinate_load_ms, " +
                    "astar_setup_ms, landmark_selection_ms, " +
                    "alt_preprocessing_ms\n");
        }

        String row = String.format(Locale.ROOT,
                "%d,%.3f,%.3f,%.3f,%.3f,%.3f%n",
                trial, graphMs, coordinatesMs,
                astarMs, selectionMs, altMs);

        Files.writeString(file, row, StandardOpenOption.APPEND);

        System.out.println("Trial " + trial + ": " + row.trim());
    }

    private static double ms(long start) {
        return (System.nanoTime() - start) / 1000000.0;
    }
}

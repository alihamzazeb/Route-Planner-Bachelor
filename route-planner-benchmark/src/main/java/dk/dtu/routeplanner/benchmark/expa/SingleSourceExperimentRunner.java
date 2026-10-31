
package dk.dtu.routeplanner.benchmark.expa;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.algorithms.duan.Duan;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

public final class SingleSourceExperimentRunner {

    private SingleSourceExperimentRunner() {
    }

    public static void main(String[] args) throws Exception {

        Graph graph = new DimacsLoader()
                .load(Path.of("data/USA-road-d.NY.gr"))
                .forward();

        int[] sources = SingleSourceWorkload.read(
                SingleSourceWorkload.NY_SOURCES,
                graph.nodeCount()
        );

        Dijkstra dijkstra = new Dijkstra();

        Duan duan = new Duan(
                graph,
                Duan.DegreeMode.BOUNDED
        );

        System.out.println(
                "Duan preparation: " + duan.graphInfo()
        );

        List<String> rows = new ArrayList<>();
        rows.add("source,reachable,unreachable,checked_nodes");

        for (int source : sources) {

            SingleSourceResult expected =
                    dijkstra.computeShortestPaths(graph, source);

            SingleSourceResult actual =
                    duan.computeShortestPaths(graph, source);

            int reachable = 0;

            for (int node = 0; node < graph.nodeCount(); node++) {

                double a = expected.distance(node);
                double b = actual.distance(node);

                if (Double.isNaN(a)
                        || Double.isNaN(b)
                        || Double.compare(a, b) != 0) {

                    throw new IllegalStateException(
                            "Distance mismatch: source=" + source
                                    + ", node=" + node
                                    + ", Dijkstra=" + a
                                    + ", Duan=" + b
                    );
                }

                if (Double.isFinite(a)) {
                    reachable++;
                }
            }

            if (expected.settledCounter() != reachable
                    || actual.settledCounter() != reachable) {

                throw new IllegalStateException(
                        "Reachability count mismatch for source " + source
                );
            }

            rows.add(
                    source + ","
                            + reachable + ","
                            + (graph.nodeCount() - reachable) + ","
                            + graph.nodeCount()
            );

            System.out.println(
                    "Verified source " + source + ": "
                            + reachable + "/" + graph.nodeCount()
                            + " reachable"
            );
        }

        Path output = Path.of(
                "benchmark-data/results/NY-single-source-validation.csv"
        );

        Files.createDirectories(output.getParent());
        Files.write(output, rows, StandardCharsets.UTF_8);

        System.out.println(
                "All " + sources.length
                        + " complete distance vectors match exactly."
        );

        System.out.println("Validation saved to " + output);
    }
}

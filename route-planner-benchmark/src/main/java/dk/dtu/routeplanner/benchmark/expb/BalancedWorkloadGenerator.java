package dk.dtu.routeplanner.benchmark.expb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

public final class BalancedWorkloadGenerator {
    // The NY distance graph uses approximately 10 weight units per metre.
    private static final double UNITS_PER_METRE = 10.0;

    private static final double METRES_PER_COORDINATE_UNIT = 111195.0 / 1000000.0;

    private static final int MAX_ATTEMPTS = 5000000;

    private static final Path WORKLOAD_DIR = Path.of("benchmark-data/workloads");

    // definition of the short,medium, long distances
    private enum Group {
        SHORT("short", 33, 0, 5000),
        MEDIUM("medium", 33, 5000, 20000),
        LONG("long", 34, 20000, Double.POSITIVE_INFINITY);

        final String label;
        final int count;
        final double minMetres;
        final double maxMetres;

        Group(String label, int count, double minMetres, double maxMetres) {
            this.label = label;
            this.count = count;
            this.minMetres = minMetres;
            this.maxMetres = maxMetres;
        }

        boolean contains(double roadMetres) {
            return roadMetres >= minMetres && roadMetres < maxMetres;
        }

        boolean plausible(double straightLineMetres) {
            // 10% tolerance because the straight-line distance is approximate.
            return this == LONG || straightLineMetres < maxMetres * 1.1;
        }
    }

    private record Route(int source, int target, double weight, double straightLineMetres) {}

    private BalancedWorkloadGenerator() {}

    public static void main(String[] args) throws IOException {
        DimacsLoader loader = new DimacsLoader();

        Graph graph = loader.load(Path.of("data/USA-road-d.NY.gr")).forward();

        StraightLine straightLine = new StraightLine(loader.loadCoordinates(Path.of("data/USA-road-d.NY.co")));

        Random random = new Random(BenchmarkConfig.QUERY_SEED);
        Dijkstra dijkstra = new Dijkstra();

        Set<Long> usedPairs = new HashSet<>();
        List<Route> allRoutes = new ArrayList<>();

        List<String> metadata = new ArrayList<>();
        metadata.add("category,source,target,road_weight,road_km,straight_line_km");

        Files.createDirectories(WORKLOAD_DIR);

        for (Group group : Group.values()) {
            List<Route> routes = new ArrayList<>();
            int attempts = 0;

            while (routes.size() < group.count && attempts < MAX_ATTEMPTS) {
                attempts++;

                // making a pair
                int source = random.nextInt(graph.nodeCount());
                int target = random.nextInt(graph.nodeCount());

                // make sure source and target are different and not in usedpairs
                if (source == target || usedPairs.contains(pairId(source, target))) {
                    continue;
                }
                // The straight-line distance is an approximate lower bound, so it
                // rejects most pairs that are too long without running Dijkstra and then also convert it to meter
                double straightMetres = straightLine.distance(source, target) * METRES_PER_COORDINATE_UNIT;

                if (!group.plausible(straightMetres)) {
                    continue;
                }

                // running dijsktra to find the actual distance
                double roadWeight = dijkstra.findShortestPath(graph, source, target).distance();

                double roadMetres = roadWeight / UNITS_PER_METRE;

                // decide if we keep the pair or not
                if (!Double.isFinite(roadMetres) || !group.contains(roadMetres)) {
                    continue;
                }

                Route route = new Route(source, target, roadWeight, straightMetres);

                routes.add(route);
                allRoutes.add(route);
                usedPairs.add(pairId(source, target));
            }

            if (routes.size() != group.count) {
                throw new IllegalStateException("Found only " + routes.size() + " " + group.label + " routes after " + attempts + " attempts");
            }

            // organize the selected routes, saves them into CSV files
            double min = Double.POSITIVE_INFINITY;
            double max = 0;

            for (int i = 0; i < routes.size(); i++) {
                Route route = routes.get(i);

                double km = route.weight() / UNITS_PER_METRE / 1000.0;

                min = Math.min(min, km);
                max = Math.max(max, km);

                metadata.add(String.format(Locale.ROOT, "%s,%d,%d,%.0f,%.3f,%.3f",
                        group.label, route.source(), route.target(), route.weight(),
                        km, route.straightLineMetres() / 1000.0));
            }

            // Save the separate category workload.
            Workloads.savePairs(WORKLOAD_DIR.resolve("NY-" + group.label + "-seed42.csv"), pairs(routes));

            System.out.printf(Locale.ROOT, "%s: %d routes, %.2f to %.2f km (%d attempts)%n",
                    group.label, routes.size(), min, max, attempts);
        }

        if (allRoutes.size() != BenchmarkConfig.PAIR_COUNT) {
            throw new IllegalStateException("Expected exactly 100 routes");
        }

        Workloads.savePairs(BenchmarkConfig.NY_WORKLOAD, pairs(allRoutes));

        Files.write(WORKLOAD_DIR.resolve("NY-balanced-metadata-seed42.csv"),
                metadata, StandardCharsets.UTF_8);

        System.out.println("Saved 100 balanced routes. Run BenchmarkRunner to validate all four algorithms.");
    }

    private static int[][] pairs(List<Route> routes) {
        int[][] pairs = new int[routes.size()][2];
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            pairs[i][0] = route.source();
            pairs[i][1] = route.target();
        }
        return pairs;
    }

    private static long pairId(int source, int target) {
        return ((long) source << 32) | (target & 0xffffffffL);
    }
}

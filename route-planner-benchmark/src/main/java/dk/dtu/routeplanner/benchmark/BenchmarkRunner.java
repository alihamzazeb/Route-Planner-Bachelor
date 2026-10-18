
package dk.dtu.routeplanner.benchmark;

import java.io.IOException;
import java.nio.file.Path;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;

public class BenchmarkRunner {

    public static void main(String[] args) throws IOException {
        DimacsLoader loader = new DimacsLoader();
        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));

        Graph graph = network.forward();
        Dijkstra dijkstra = new Dijkstra();

        // the queries we want to run. source and target
        int[][] queries = {
                {10, 500},
                {20, 2000},
                {100, 500},
                {100, 2000},
                {500, 2000}
        };

        System.out.println("New York road network");
        System.out.println("Nodes: " + graph.nodeCount());

        for (int[] query : queries) {
            int source = query[0];
            int target = query[1];

            long start = System.nanoTime();

            SearchResult result = dijkstra.findShortestPath(graph, source, target);

            // get the time taken
            long elapsed = System.nanoTime() - start;

            double timeMs = elapsed / 1000000.0; // get in ms

            System.out.printf(
                    "%d -> %d | distance: %.0f | settled: %d | relaxed: %d | time: %.3f ms%n",
                    source,
                    target,
                    result.distance(),
                    result.settledCounter(),
                    result.relaxedCounter(),
                    timeMs
            );
        }
    }
}

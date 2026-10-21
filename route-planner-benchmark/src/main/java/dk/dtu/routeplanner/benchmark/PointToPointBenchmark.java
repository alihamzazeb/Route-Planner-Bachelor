
package dk.dtu.routeplanner.benchmark;

import java.io.IOException;
import java.nio.file.Path;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.SearchResult;

@State(Scope.Benchmark)
public class PointToPointBenchmark {

    private Graph graph;
    private Dijkstra dijkstra;

    @Setup(Level.Trial)
    public void setup() throws IOException {

        DimacsLoader loader = new DimacsLoader();

        RoadNetwork network = loader.load(Path.of("data/USA-road-d.NY.gr"));

        graph = network.forward();
        dijkstra = new Dijkstra();
    }

    @Benchmark
    public void dijkstra(Blackhole blackhole) {

        SearchResult result = dijkstra.findShortestPath(graph, 10, 500);

        blackhole.consume(result);
    }
}

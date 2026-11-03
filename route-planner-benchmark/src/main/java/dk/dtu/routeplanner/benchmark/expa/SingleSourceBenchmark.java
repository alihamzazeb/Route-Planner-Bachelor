
package dk.dtu.routeplanner.benchmark.expa;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.algorithms.SingleSourceAlgorithm;
import dk.dtu.routeplanner.core.algorithms.duan.Duan;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SingleSourceBenchmark {

    @Param({"dijkstra", "duan"})
    public String algorithmName;

    private Graph graph;
    private int[] sources;
    private SingleSourceAlgorithm algorithm;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        graph = new DimacsLoader().load(Path.of("data/USA-road-d.NY.gr")).forward();

        sources = SingleSourceWorkload.read(SingleSourceWorkload.NY_SOURCES, graph.nodeCount());

        algorithm = switch (algorithmName) {

            case "dijkstra" -> new Dijkstra();

            case "duan" -> new Duan(graph, Duan.DegreeMode.BOUNDED);

            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        };
    }

    @Benchmark
    @OperationsPerInvocation(SingleSourceWorkload.SOURCE_COUNT)
    public void search(Blackhole blackhole) {
        for (int source : sources) {
            blackhole.consume(algorithm.computeShortestPaths(graph, source));
        }
    }
}

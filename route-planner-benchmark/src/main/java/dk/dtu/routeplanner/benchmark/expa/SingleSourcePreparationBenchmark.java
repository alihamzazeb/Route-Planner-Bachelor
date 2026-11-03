
package dk.dtu.routeplanner.benchmark.expa;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import dk.dtu.routeplanner.core.algorithms.duan.Duan;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.loader.DimacsLoader;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SingleSourcePreparationBenchmark {

    private Graph graph;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        graph = new DimacsLoader().load(Path.of("data/USA-road-d.NY.gr")).forward();
    }

    @Benchmark
    public Duan prepare() {
        return new Duan(graph, Duan.DegreeMode.BOUNDED);
    }
}

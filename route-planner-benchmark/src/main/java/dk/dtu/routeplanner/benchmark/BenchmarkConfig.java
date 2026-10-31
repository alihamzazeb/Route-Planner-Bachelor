
package dk.dtu.routeplanner.benchmark;

import java.nio.file.Path;

public final class BenchmarkConfig {

    public static final int PAIR_COUNT = 100;
    public static final long QUERY_SEED = 42L;

    public static final int LANDMARK_COUNT = 4;
    public static final long LANDMARK_SEED = 2026L;

    public static final Path NY_WORKLOAD = Path.of("benchmark-data/workloads/NY-100pairs-balanced-seed42.csv");

    private BenchmarkConfig() {
    }
}

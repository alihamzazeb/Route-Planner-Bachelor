# Benchmark Commands

Run these commands in Bash or Zsh from the repository root with Java 21. JMH 1.37 is included in the benchmark JAR. The saved results used Java 21.0.5; the full environment and source revision are recorded in [environment.txt](benchmark-data/results/experiment-b/environment.txt).

**JMH settings:** 1 thread, 3 forks, 5 × 3 s warmup, 10 × 5 s measurement, fixed 2 GB heap. Keep `benchmark-data/workloads/` unchanged.

> These commands overwrite results with the same filenames. Use a separate copy of the project to preserve the recorded results.

The Java CSV runners write directly into `benchmark-data/results/`. The `mv` commands below put their output into the experiment subfolders expected by the figure script.

## Build

```bash
mvn clean verify
mvn -pl route-planner-benchmark -am -DskipTests package
mkdir -p benchmark-data/results/experiment-a benchmark-data/results/experiment-b
```

## Experiment A: Single-source (Dijkstra vs Duan)

**Correctness, 10 sources**

```bash
java -cp route-planner-benchmark/target/benchmarks.jar dk.dtu.routeplanner.benchmark.expa.SingleSourceExperimentRunner
mv benchmark-data/results/NY-single-source-validation.csv benchmark-data/results/experiment-a/
```

**Execution time**

```bash
java -jar route-planner-benchmark/target/benchmarks.jar '.*SingleSourceBenchmark.search' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -rf json -rff benchmark-data/results/experiment-a/NY-single-source-time.json
```

**Allocation**

```bash
java -jar route-planner-benchmark/target/benchmarks.jar '.*SingleSourceBenchmark.search' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -prof gc -rf json -rff benchmark-data/results/experiment-a/NY-single-source-allocation.json
```

**Duan preparation**

```bash
java -jar route-planner-benchmark/target/benchmarks.jar '.*SingleSourcePreparationBenchmark.prepare' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -rf json -rff benchmark-data/results/experiment-a/NY-single-source-preparation.json
```

## Experiment B: Point-to-point (Dijkstra, Bidirectional Dijkstra, A*, ALT)

**Correctness and search counters, 100 routes**

```bash
java -cp route-planner-benchmark/target/benchmarks.jar dk.dtu.routeplanner.benchmark.expb.BenchmarkRunner
mv benchmark-data/results/NY-balanced-seed42-counters.csv benchmark-data/results/experiment-b/
```

**Execution time**

```bash
java -jar route-planner-benchmark/target/benchmarks.jar '.*PointToPointBenchmark.search' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -rf json -rff benchmark-data/results/experiment-b/NY-balanced-time.json
```

**Allocation**

```bash
java -jar route-planner-benchmark/target/benchmarks.jar '.*PointToPointBenchmark.search' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -prof gc -rf json -rff benchmark-data/results/experiment-b/NY-balanced-allocation.json
```

**Short, medium and long routes**

```bash
java -jar route-planner-benchmark/target/benchmarks.jar '.*RouteLengthBenchmark.*' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -rf json -rff benchmark-data/results/experiment-b/NY-balanced-route-length.json
```

**ALT landmark count (1, 2, 4, 8)**

```bash
java -cp route-planner-benchmark/target/benchmarks.jar dk.dtu.routeplanner.benchmark.expb.LandmarkSensitivityRunner
mv benchmark-data/results/NY-landmarks-preprocessing.csv benchmark-data/results/experiment-b/
mv benchmark-data/results/NY-landmarks-counters.csv benchmark-data/results/experiment-b/
java -jar route-planner-benchmark/target/benchmarks.jar '.*LandmarkSensitivityBenchmark.search' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -rf json -rff benchmark-data/results/experiment-b/NY-landmarks-time.json
```

**ALT landmark selection (random vs farthest-first, 4 and 8)**

```bash
java -cp route-planner-benchmark/target/benchmarks.jar dk.dtu.routeplanner.benchmark.expb.LandmarkSelectionRunner
mv benchmark-data/results/NY-landmark-selection-setup.csv benchmark-data/results/experiment-b/
mv benchmark-data/results/NY-landmark-selection-counters.csv benchmark-data/results/experiment-b/
java -jar route-planner-benchmark/target/benchmarks.jar '.*LandmarkSelectionBenchmark.search' -bm avgt -tu ms -t 1 -f 3 -wi 5 -i 10 -w 3s -r 5s -jvmArgs '-Xms2g -Xmx2g' -rf json -rff benchmark-data/results/experiment-b/NY-landmark-selection-time.json
```

**General preprocessing, 10 separate JVM runs**

```bash
rm -f benchmark-data/results/NY-preprocessing.csv
for i in {1..10}; do java -Xms2g -Xmx2g -cp route-planner-benchmark/target/benchmarks.jar dk.dtu.routeplanner.benchmark.expb.PreprocessingRunner "$i"; done
mv benchmark-data/results/NY-preprocessing.csv benchmark-data/results/experiment-b/
```

## Generate figures

Create a Python environment and install Matplotlib once:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install matplotlib
```

Then generate all 17 PDF figures from the saved results:

```bash
.venv/bin/python scripts/benchmarkFigures.py
```

Results: `benchmark-data/results/experiment-a/` and `benchmark-data/results/experiment-b/`  
Figures: `report-figures/experiment-a/` and `report-figures/experiment-b/`

**Measurement scope:** JMH query timings exclude graph loading and algorithm preparation. `gc.alloc.rate.norm` measures allocated bytes per operation, not peak heap usage. The break-even figures are modeled estimates.

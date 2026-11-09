# Route Planner

A Java implementation and experimental comparison of shortest-path algorithms, developed as a BSc Software Technology project at the Technical University of Denmark (DTU).

The project examines how search strategy and preprocessing affect query time, search effort and memory allocation on a road network. It includes the algorithms, a Spring Boot REST API, an interactive map and the workloads and results used in the evaluation. Single-source and point-to-point searches are evaluated separately because they solve different problems.

## Algorithms

| Problem | Algorithm | Approach |
| --- | --- | --- |
| Point-to-point | Dijkstra | Baseline shortest-path search |
| Point-to-point | Bidirectional Dijkstra | Searches from both ends using forward and reverse edges |
| Point-to-point | A* | Uses a conservatively scaled straight-line heuristic |
| Point-to-point | ALT | Uses A* with precomputed distances to and from landmarks |
| Single-source | Dijkstra | Computes distances from one source to every node |
| Single-source | Duan | Java adaptation of the BMSSP-based shortest-path algorithm |

The graph uses forward and reverse compressed sparse row (CSR) representations. ALT precomputes distances both to and from each landmark to support directed graphs. Landmark selection can be seeded random or geographical farthest-first.

The Duan implementation follows the BMSSP approach in [*Breaking the Sorting Barrier for Directed Single-Source Shortest Paths* by Duan et al.](https://arxiv.org/abs/2504.17033v2). It is split into `Duan` (recursive searches and pivot selection), `DuanGraph` (graph preparation) and `DuanQueue` (batched frontier operations). Both bounded and degree-transformed preparation are supported; the API and Experiment A use prepared bounded mode.

## Project structure

```text
route-planner-core/       Graph structures, DIMACS loader and algorithms
route-planner-api/        Spring Boot REST API and MapLibre web interface
route-planner-benchmark/  JMH benchmarks, split into expa/ and expb/
benchmark-data/           Fixed workloads and recorded results
data/                     New York DIMACS graph and coordinates
report-figures/           PDF figures generated from the recorded results
scripts/                  Benchmark figure generator
BENCHMARK_COMMANDS.md     Commands for reproducing the experiments
```

The benchmark module calls the core algorithms directly. HTTP requests, JSON conversion and map rendering are outside the benchmark measurements.

## Dataset

The repository includes the New York distance-weighted graph from the [9th DIMACS Implementation Challenge](https://www.diag.uniroma1.it/~challenge9/download.shtml): **264,346 nodes and 733,846 directed edge records**.

```text
data/USA-road-d.NY.gr     Graph and edge weights
data/USA-road-d.NY.co     Node coordinates
```

The loader converts DIMACS node IDs to zero-based IDs. The API and interface therefore accept IDs from `0` to `264345` for this dataset. Coordinates in API responses are longitude and latitude in degrees.

Algorithms and API distance fields use the original edge weights. The map's kilometre display and the benchmark route categories use an inferred conversion of **10 weight units per metre**. This conversion has not been independently confirmed from the dataset documentation, so kilometre values should be treated as estimates.

## Build and run

Use Java 21 and Maven. The recorded experiments used Java 21.0.5 and Maven 3.9.11. Python 3 with Matplotlib is needed only to regenerate figures.

Run the following commands from the repository root in Bash or Zsh. First, build all modules, run the tests and install the modules into the local Maven repository:

```bash
mvn clean install
```

Then start the API:

```bash
mvn -pl route-planner-api spring-boot:run \
  -Dspring-boot.run.workingDirectory="$PWD"
```

Open [the map](http://localhost:8080/map.html) or check [the API status](http://localhost:8080/status). Stop the server with `Ctrl+C`.

To run from an IDE, start `dk.dtu.routeplanner.api.RoutePlannerApplication` with the repository root as the working directory. The dataset paths are configured in [application.properties](route-planner-api/src/main/resources/application.properties) and are relative to that directory.

The map loads MapLibre GL JS and a CARTO basemap from external servers and requires internet access. Searches and numerical results remain available in the browser if the map cannot load, and the REST API also works independently of these assets. An optional CARTO key can be set as `window.CARTO_API_KEY` in `route-planner-api/src/main/resources/static/config.local.js`; this file is excluded from Git.

The interface takes node IDs rather than addresses. Point-to-point searches display a route and search statistics. Single-source searches display a summary of distances computed from the chosen source.

## REST API

| Endpoint | Purpose |
| --- | --- |
| `GET /status` | Service status |
| `GET /api/routes` | Point-to-point route |
| `GET /api/routes/compare` | Compare Dijkstra, Bidirectional Dijkstra, A* and ALT |
| `GET /api/single-source` | Compute single-source distances and return a summary |
| `GET /api/single-source/compare` | Compare all computed distances from Dijkstra and Duan |

Point-to-point endpoints require `source` and `target`; `algorithm` accepts `dijkstra`, `bidirectional`, `astar` or `alt`. Single-source endpoints require `source`, reject `target`, and accept `dijkstra` or `duan`. Algorithm names are case-insensitive and default to `dijkstra` when omitted.

Example requests:

```bash
curl 'http://localhost:8080/api/routes?source=10&target=500&algorithm=astar'
curl 'http://localhost:8080/api/routes/compare?source=10&target=500&algorithm=alt'
curl 'http://localhost:8080/api/single-source?source=10&algorithm=duan'
curl 'http://localhost:8080/api/single-source/compare?source=10&algorithm=duan'
```

Route responses include the path's node IDs and coordinates, its distance, reachability and search counters. An unreachable route has an empty path and a `null` distance. The route comparison endpoint runs all four algorithms and returns the selected algorithm's route alongside their statistics.

Single-source responses contain summary statistics rather than the full distance vector. The comparison endpoint checks every distance in the two vectors and reports the number of mismatches. Invalid node IDs or algorithm names return HTTP 400.

The comparison endpoints report simple in-process algorithm timings for display. These are not the controlled JMH benchmark measurements below.

## Experiments

Query performance is measured with **JMH 1.37**. The recorded runs use one thread, three JVM forks, five 3-second warmup iterations and ten 5-second measurement iterations per fork, with a fixed 2 GB heap. Time and allocation measurements are normalised per source or route, even though each benchmark invocation processes a batch of queries.

**Experiment A: Single-source.** Dijkstra and prepared bounded-mode Duan are compared on ten fixed sources selected with seed `42`. Measurements cover query time, allocated bytes per query and Duan's graph preparation time. `SingleSourceExperimentRunner` checks every returned distance against Dijkstra.

**Experiment B: Point-to-point.** Dijkstra, Bidirectional Dijkstra, A* and ALT are compared on 100 fixed reachable routes selected with seed `42`: 33 short (below 5 km), 33 medium (5 to below 20 km) and 34 long (20 km or more). Measurements cover query time, search effort, allocation and preprocessing. Further experiments vary the number of ALT landmarks (1, 2, 4 and 8) and compare random with geographical farthest-first selection at 4 and 8 landmarks. The main ALT benchmark and the API both use four random landmarks with seed `2026`.

`BenchmarkRunner` checks point-to-point distances against Dijkstra and validates the returned paths. It and `SingleSourceExperimentRunner` use exact distance comparisons and reject NaN values for the integer-weighted New York workload.

Query timings exclude graph loading and algorithm preparation, which are measured separately. Allocation is the total number of bytes allocated per query, not peak memory usage. The cumulative-time and break-even figures are estimates calculated from the measured mean query and preparation times.

The fixed workloads are in [benchmark-data/workloads](benchmark-data/workloads/). Recorded results are divided into [Experiment A](benchmark-data/results/experiment-a/) and [Experiment B](benchmark-data/results/experiment-b/). The [recorded environment](benchmark-data/results/experiment-b/environment.txt) identifies the machine, software versions, benchmarked commit and workload hashes. It describes the saved measurements rather than the latest repository revision.

See [Benchmark Commands](BENCHMARK_COMMANDS.md) for the complete build, validation and measurement commands. Those commands overwrite result files with the same names; use a separate copy of the project if you want to keep the recorded results.

## Report figures

The repository contains **18 PDF figures**: three in [report-figures/experiment-a](report-figures/experiment-a/) and fifteen in [report-figures/experiment-b](report-figures/experiment-b/). They can be regenerated from the saved results without rerunning the benchmarks:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install matplotlib
.venv/bin/python scripts/benchmarkFigures.py
```

The script generates the full set and overwrites the existing PDFs using the same filenames. If the Python environment is already set up, only the final command is needed.

## Testing and limitations

JUnit tests are in `route-planner-core/src/test/` and `route-planner-api/src/test/`. They cover graph loading and representation, shortest-path correctness, path reconstruction, search counters, directed landmarks, Duan's graph preparation and queue, numerical edge cases, and API validation. Run the standard suite from the repository root with `mvn test`. The web interface has no automated tests.

An additional test comparing Duan's preparation modes with Dijkstra on the New York graph is opt-in:

```bash
mvn -pl route-planner-core -Dduan.ny=true test
```

The evaluation uses one road network and fixed workloads. It does not establish how performance scales across graph sizes. The New York dataset is symmetric, even though the implementation supports general directed graphs, so the experiments do not evaluate direction-dependent travel costs. All landmark experiments use seed `2026`, so the results describe those landmark sets rather than a general ranking of selection methods.

Search counters also need care when comparing algorithms. Bidirectional Dijkstra adds settlements from both searches, so a node settled in both directions is counted twice. The relaxed-edge counter counts scanned edges, including scans that do not improve a distance. Duan reports reachable original nodes as its settled count, while its relaxed count includes repeated scans of the internal graph.

The implementations allocate and initialise arrays for each query. This cost is included in the timings and can matter particularly on short routes, where little graph exploration is needed.

Duan's published theoretical bound is not independently established for this Java adaptation.

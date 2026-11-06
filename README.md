# Route Planner

A Java route-planning service developed as a BSc Software Technology project at the Technical University of Denmark (DTU). It implements and evaluates shortest-path algorithms on directed road networks, with a REST API, an interactive map and reproducible benchmarks.

The experiments use the New York distance-weighted graph from the 9th DIMACS Implementation Challenge: **264,346 vertices and 733,846 directed edges**.

## Algorithms

| Problem | Algorithm | Approach |
| --- | --- | --- |
| Point-to-point | Dijkstra | Baseline shortest-path search |
| Point-to-point | Bidirectional Dijkstra | Searches from both ends using forward and reverse edges |
| Point-to-point | A* | Uses a conservatively scaled straight-line heuristic |
| Point-to-point | ALT | Uses A* with precomputed distances to and from landmarks |
| Single-source | Dijkstra | Computes distances from one source to every vertex |
| Single-source | Duan | Java adaptation of the BMSSP-based shortest-path algorithm |

The graph is represented using forward and reverse compressed sparse row (CSR) structures. ALT supports both seeded random landmark selection and geographical farthest-first selection. Duan is based on the BMSSP approach of [Duan et al.](https://arxiv.org/abs/2504.17033v2). Its implementation is split into `Duan` (recursive searches and pivot selection), `DuanGraph` (graph preparation) and `DuanQueue` (batched frontier operations). It supports bounded and degree-transformed graph preparation.

## Project structure

```text
route-planner-core/       Graph structures, DIMACS loader and algorithms
route-planner-api/        Spring Boot REST API and MapLibre web interface
route-planner-benchmark/  JMH benchmarks, split into expa/ and expb/
benchmark-data/           Fixed workloads and recorded results
data/                     New York DIMACS graph and coordinates
scripts/                  Benchmark figure generator
```

Core algorithm and graph tests are under `route-planner-core/src/test/`; API and integration tests are under `route-planner-api/src/test/`. The benchmark module calls the algorithms directly, without HTTP or browser overhead.

## Build and run

**Requirements:** Java 21 and Maven. Python 3 with Matplotlib is needed only to regenerate figures. The map requires internet access for its external assets.

The repository includes the New York dataset:

```text
data/USA-road-d.NY.gr
data/USA-road-d.NY.co
```

From the repository root, build and test with:

```bash
mvn clean verify
```

Start `dk.dtu.routeplanner.api.RoutePlannerApplication` from your IDE, with the **repository root as the working directory** so the relative dataset paths resolve correctly. Alternatively, after the tests pass, install the local modules and start the API from the repository root:

```bash
mvn install -DskipTests
mvn -pl route-planner-api spring-boot:run \
  -Dspring-boot.run.workingDirectory="$PWD"
```

Open [the map](http://localhost:8080/map.html) or check [the API status](http://localhost:8080/status).

The map uses MapLibre GL JS and a CARTO basemap. An API key is optional. If needed, set `window.CARTO_API_KEY` in `route-planner-api/src/main/resources/static/config.local.js`. This file is Git-ignored; restrict any browser-side key at the provider.

The interface accepts **zero-based node IDs**, not addresses. Point-to-point queries display the route and search statistics; the comparison view runs all four algorithms and displays the selected route. Single-source searches compute a full distance vector but return summary statistics rather than all distances. The comparison endpoint checks the complete vectors from Dijkstra and Duan for disagreements.

## REST API

| Endpoint | Purpose |
| --- | --- |
| `GET /status` | Service status |
| `GET /api/routes` | Point-to-point route |
| `GET /api/routes/compare` | Compare Dijkstra, Bidirectional Dijkstra, A* and ALT |
| `GET /api/single-source` | Compute single-source distances and return a summary |
| `GET /api/single-source/compare` | Compare all computed distances from Dijkstra and Duan |

Example requests:

```bash
curl 'http://localhost:8080/api/routes?source=10&target=500&algorithm=astar'
curl 'http://localhost:8080/api/routes/compare?source=10&target=500&algorithm=alt'
curl 'http://localhost:8080/api/single-source?source=10&algorithm=duan'
```

The route responses include node IDs, coordinates, distance and search counters. Invalid node IDs or algorithm names return HTTP 400. The API's individual timing measurements are for display, **not** the controlled performance evaluation.

## Experiments

Performance is measured separately for two different shortest-path problems using **JMH 1.37**. Query timing excludes graph loading and algorithm preparation. Preparation is measured separately.

**Experiment A: Single-source.** Dijkstra and prepared bounded-mode Duan are compared on ten fixed New York sources. The correctness runner checks every distance in each returned vector.

**Experiment B: Point-to-point.** Dijkstra, Bidirectional Dijkstra, A* and ALT are compared on 100 fixed reachable routes: 33 short, 33 medium and 34 long. Additional measurements cover search effort, allocation, preprocessing, the number of ALT landmarks (1, 2, 4 and 8), and random versus geographical farthest-first selection. The main ALT benchmark uses four random landmarks with seed `2026`; the interactive API uses seed `42`.

Recorded results are in `benchmark-data/results/experiment-a/` and `benchmark-data/results/experiment-b/`; fixed workloads are in `benchmark-data/workloads/`. The primary Experiment B timings are in `benchmark-data/results/experiment-b/NY-balanced-time.json`. The 17 PDF figures are included under `report-figures/experiment-a/` and `report-figures/experiment-b/`.

See [Benchmark Commands](BENCHMARK_COMMANDS.md) for instructions to build and run the benchmarks, check correctness and regenerate the figures. The [recorded environment](benchmark-data/results/experiment-b/environment.txt) lists the hardware, software versions, benchmarked commit and workload hashes.

## Testing and limitations

JUnit tests cover graph loading and representation, algorithm correctness, path reconstruction, directed landmarks, Duan's graph preparation and queue, numerical edge cases, and API validation. Additional New York Duan tests can be enabled with:

```bash
mvn -pl route-planner-core -Dduan.ny=true test
```

The evaluation uses **one road network** and does not establish performance scaling across graph sizes. The short/medium/long route categories and kilometre display use an inferred conversion of **10 stored weight units per metre**; the algorithms themselves operate on the original edge weights. This conversion is not independently verified against the dataset documentation.

Duan's published theoretical bound is not independently established for this Java adaptation.

Dataset source: [9th DIMACS Implementation Challenge](https://www.diag.uniroma1.it/challenge9/).

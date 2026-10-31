# Route Planner

Bachelor project in Software Technology at the Technical University of Denmark (DTU).

The project implements and compares shortest-path algorithms on a directed road network. It includes the algorithms, a Spring Boot API, a browser-based map, and a separate JMH module for performance measurements.

The current experiments use the DIMACS New York road network with 264,346 nodes and 733,846 directed arcs.

## Implemented algorithms

**Point-to-point shortest paths**

- **Dijkstra:** Computes a shortest path between a source and a target. Also used as the correctness reference.
- **Bidirectional Dijkstra:** Searches from both endpoints using the forward and reverse graphs.
- **A\*:** Uses a scaled straight-line distance heuristic based on the node coordinates.
- **ALT:** Runs A\* with landmark-based lower bounds. Distances to and from each landmark are precomputed. Both seeded random selection and geographical farthest-first selection are implemented.

**Single-source shortest paths**

- **Dijkstra:** Computes distances from one source to all reachable nodes.
- **Duan:** An implementation based on the BMSSP approach, with graph preparation and bounded or transformed graph modes. It is evaluated separately from the point-to-point algorithms.

The graph is stored in compressed sparse row (CSR) format. The loader builds both forward and reverse graphs, which are needed for Bidirectional Dijkstra and directed ALT bounds.

## Project structure

```text
route-planner-core/       Graph structures, DIMACS loader, algorithms and unit tests
route-planner-api/        Spring Boot API, application tests and map interface
route-planner-benchmark/  JMH benchmarks and experiment runners
  src/main/java/dk/dtu/routeplanner/benchmark/
    expa/                Single-source: Dijkstra vs. Duan
    expb/                Point-to-point: Dijkstra, Bidirectional, A* and ALT
benchmark-data/
  workloads/             Saved source nodes and route pairs
  results/               Benchmark results and correctness checks
data/                    DIMACS graph and coordinate files (not tracked by Git)
```

## Requirements

- Java 21
- Maven
- DIMACS New York distance graph and coordinate files
- A CARTO API key for the browser map

Put the network files in `data/` at the repository root:

```text
data/
  USA-road-d.NY.gr
  USA-road-d.NY.co
```

The corresponding file paths are configured in `route-planner-api/src/main/resources/application.properties`.

For the map, create `route-planner-api/src/main/resources/static/config.local.js`:

```javascript
window.CARTO_API_KEY = "YOUR_CARTO_API_KEY";
```

This file is ignored by Git. The key is still sent to the browser, so restrict it through the provider's settings. The map also needs an internet connection to load MapLibre and the basemap.

## Build and run

Run the tests from the repository root:

```bash
mvn clean verify
```

Start `RoutePlannerApplication` from your IDE with the repository root as its working directory. The application loads the graph and prepares the algorithms at startup.

Open:

- Application: http://localhost:8080/map.html
- Status endpoint: http://localhost:8080/status

In the web interface, enter node IDs to find a route or compute single-source distances. The point-to-point view draws the returned path on the map and allows comparisons between the four routing algorithms. The single-source view marks the source node and compares the Dijkstra and Duan summaries.

## REST API

All node IDs are zero-based.

| Endpoint | Description |
|---|---|
| `GET /status` | Check that the API is running |
| `GET /api/routes` | Shortest route between a source and a target |
| `GET /api/routes/compare` | Compare the four point-to-point algorithms |
| `GET /api/single-source` | Single-source computation using Dijkstra or Duan |
| `GET /api/single-source/compare` | Compare Dijkstra and Duan on one source |

Examples:

```text
/api/routes?source=10&target=500&algorithm=astar
/api/routes/compare?source=10&target=500&algorithm=alt
/api/single-source?source=10&algorithm=duan
/api/single-source/compare?source=10&algorithm=duan
```

The point-to-point responses include the path, coordinates, distance and search counters. Single-source responses summarize reachability and search work; the comparison also checks whether the two algorithms agree on all node distances. Invalid node IDs and unsupported algorithm names return HTTP 400.

The API's `timeMs` values are timings from individual requests, not controlled benchmark results. Use the JMH results for performance comparisons.

## Benchmarking

The benchmark module is separated into two experiments:

**Experiment A: Single-source** compares Dijkstra and Duan on ten fixed source nodes from the New York graph. The runner checks the complete distance vectors for correctness. JMH measures query time and allocation, with Duan's one-time graph preparation measured separately.

**Experiment B: Point-to-point** compares Dijkstra, Bidirectional Dijkstra, A* and ALT on 100 reachable routes, divided into 33 short, 33 medium and 34 long routes. Additional benchmarks examine route length, preprocessing, heap allocation, landmark count (1, 2, 4 and 8), and random versus geographical farthest-first landmark placement.

The main ALT point-to-point benchmark uses four randomly selected landmarks with seed `2026`. The web application currently uses four random landmarks with seed `42`, so its interactive timings are not directly equivalent to the benchmark configuration.

Build the benchmark archive:

```bash
mvn -pl route-planner-benchmark -am -DskipTests package
```

List the JMH benchmarks:

```bash
java -jar route-planner-benchmark/target/benchmarks.jar -l
```

Run the Experiment A correctness check:

```bash
java -cp route-planner-benchmark/target/benchmarks.jar \
  dk.dtu.routeplanner.benchmark.expa.SingleSourceExperimentRunner
```

Run the Experiment A query-time benchmark:

```bash
java -jar route-planner-benchmark/target/benchmarks.jar \
  '.*expa.SingleSourceBenchmark.search' \
  -bm avgt -tu ms -t 1 -f 2 -wi 3 -w 10s -i 5 -r 10s
```

Experiment B requires `benchmark-data/workloads/NY-100pairs-balanced-seed42.csv` and the three category CSVs. If they are not present, generate them from the New York graph:

```bash
java -cp route-planner-benchmark/target/benchmarks.jar \
  dk.dtu.routeplanner.benchmark.expb.BalancedWorkloadGenerator
```

Run the main Experiment B query-time benchmark:

```bash
java -jar route-planner-benchmark/target/benchmarks.jar \
  '.*expb.PointToPointBenchmark.search' \
  -bm avgt -tu ms -t 1 -f 2 -wi 3 -w 2s -i 5 -r 2s
```

These JMH query benchmarks exclude graph loading and algorithm preprocessing. Preparation is evaluated separately. The workload selection and measurements are specific to the New York network; they should not be treated as results for other road networks.

## Testing

Unit tests cover graph structures, loading and shortest-path algorithms. Spring MVC tests cover request validation and API responses. `RoutePlannerApplicationIntegrationTest` starts the Spring application using the real New York dataset and checks routing and single-source comparisons through the API.

To run the optional larger New York correctness tests for Duan:

```bash
mvn -pl route-planner-core -Dduan.ny=true test
```

The repository excludes the graph files, so the tests that use New York data require those files to be present locally.

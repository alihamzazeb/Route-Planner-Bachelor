# Route Planner

Bachelor project in Software Technology at the Technical University of Denmark (DTU).

The project implements and evaluates shortest-path algorithms on large directed road networks. It provides a Java backend for loading road graphs and calculating routes, a REST API for requesting shortest paths, and a web interface for visualizing routes and comparing algorithms.

## Technologies

- Java 21
- Maven
- Spring Boot 4.1
- JUnit 5 and Spring MVC testing
- MapLibre GL JS
- CARTO basemaps
- DIMACS road-network datasets

## Project structure

The application is divided into two Maven modules:

- `route-planner-core` contains the graph representation, road-network loader, shortest-path algorithms and result models.
- `route-planner-api` contains the Spring Boot application, REST endpoints, request validation, response objects and web interface.

The core module is independent of Spring Boot, allowing algorithms to be tested and benchmarked directly without involving the web server.

A separate benchmarking module is planned for controlled performance evaluation.

## Road network

The application uses directed road networks in the DIMACS format.

The `.gr` file contains the graph's nodes, directed edges and weights. The `.co` file contains geographic coordinates used for route visualization and the A* heuristic.

The graph is stored using compressed sparse row (CSR) representation. Both forward and reversed graph representations are constructed when loading the network.

The current application uses the New York road network.

## Implemented algorithms

**Dijkstra**

Provides point-to-point shortest paths and single-source shortest-path distances. It also acts as the correctness baseline for the other algorithms.

**Bidirectional Dijkstra**

Runs two searches, one from the source and one backward from the destination using the reversed graph. The search uses a stopping condition based on the best known complete path and the minimum distances in both priority queues.

**A***

Uses a geographic straight-line heuristic to guide the search. The heuristic is scaled conservatively using the edge weights of the loaded graph.

**ALT**

Uses A* with landmarks and triangle-inequality lower bounds. Four landmarks are selected using the fixed random seed `42`. Preprocessing calculates distances both from and to each landmark using single-source Dijkstra. The preprocessed distances are reused across route queries.

## Requirements

- JDK 21
- Maven
- New York DIMACS `.gr` and `.co` files
- A CARTO browser API key for the map

Place the road-network files in the repository's `data/` directory:

```text
data/
├── USA-road-d.NY.gr
└── USA-road-d.NY.co
```

The paths are configured in:

`route-planner-api/src/main/resources/application.properties`

The road-network files are excluded from Git.

## Build and run

Run the tests from the repository root:

```bash
mvn -pl route-planner-api -am test
```

Start `RoutePlannerApplication` from the IDE, using the repository root as the working directory.

The API runs locally at `http://localhost:8080`.

The browser interface is available at:

`http://localhost:8080/map.html`

For the map, create `route-planner-api/src/main/resources/static/config.local.js` with a `window.CARTO_API_KEY` value. This file is Git-ignored. The key is still visible to browser clients, so it should be restricted appropriately by the provider.

## REST API

All node IDs accepted by the API are zero-based.

### GET /status

Returns a short message confirming that the server is running.

Example:

```text
GET http://localhost:8080/status
```

### GET /api/routes

Calculates the shortest path between two nodes using the selected algorithm.

Example:

```text
GET http://localhost:8080/api/routes?source=10&target=500&algorithm=dijkstra
```

Parameters:

| Parameter | Type | Description |
|---|---|---|
| `source` | Integer | Starting node ID, required |
| `target` | Integer | Destination node ID, required |
| `algorithm` | String | Optional; defaults to `dijkstra` |

Supported algorithms are `dijkstra`, `bidirectional`, `astar` and `alt`. Algorithm names are case-insensitive.

The JSON response contains:

| Field | Description |
|---|---|
| `algorithm` | Algorithm used |
| `source` | Starting node |
| `target` | Destination node |
| `reachable` | Whether a route exists |
| `distance` | Shortest-path cost in raw graph weight units |
| `path` | Ordered array of node IDs |
| `coordinates` | Route coordinates as `[longitude, latitude]` pairs |
| `settledNodes` | Number of settled nodes |
| `relaxedEdges` | Number of edges examined during relaxation |

For unreachable destinations, `reachable` is `false`, `distance` is `null`, and the path and coordinates are empty.

### GET /api/routes/compare

Runs all four algorithms on the same source and target.

Example:

```text
GET http://localhost:8080/api/routes/compare?source=10&target=500&algorithm=alt
```

The parameters are the same as for `/api/routes`.

The response contains:

- `source` and `target`
- `route`: the full route response for the selected algorithm
- `results`: four algorithm summaries, each containing the algorithm name, reachability, distance, path-node count, settled nodes, relaxed edges and `timeMs`

The selected route is taken from the searches already executed during comparison, rather than calculated a second time.

The recorded `timeMs` measures an individual algorithm query. It excludes graph loading, ALT preprocessing, HTTP processing and visualization. These values are intended for interactive comparison, not rigorous benchmarking.

### API errors

Invalid node IDs and unknown algorithm names produce HTTP 400 responses.

Handled validation errors contain a JSON object with:

- `status`
- `error`
- `message`

For example, requesting an algorithm named `unknown` produces an error rather than running a different algorithm.

## Web interface

The interface allows users to enter starting and destination nodes, select an algorithm and calculate a route.

The resulting path is displayed on an interactive map using MapLibre GL JS. The sidebar displays the distance, reachability, path-node count and search statistics.

The Compare All feature executes all four algorithms, draws the selected algorithm's route, and presents their results in a comparison table below the map.

## Design decisions

### CSR graph representation

CSR was chosen because the road network is large and mostly static. It stores graph edges in arrays rather than creating separate adjacency-list objects for every node. This supports compact storage and efficient iteration over outgoing edges.

The trade-off is that inserting or modifying edges is less convenient than with a dynamic adjacency-list representation.

### Forward and reversed graphs

The network is directed, so travelling from node A to node B is not necessarily equivalent to travelling from B to A.

A reversed graph is stored to support backward search in Bidirectional Dijkstra and the directed landmark-distance calculations used by ALT.

The reversed graph requires additional memory, but avoids rebuilding it for each query.

### Priority queues

The shortest-path implementations use Java's `PriorityQueue`. Improved tentative distances are inserted as new entries, and outdated entries are skipped when processed.

This avoids implementing a custom decrease-key priority queue, at the cost of potentially storing multiple queue entries for one node.

### Separation of algorithms and API

The shortest-path implementations do not depend on Spring Boot.

The service is responsible for loading and holding shared graph data and preprocessing results. Controllers validate requests and return JSON responses.

This separation makes the algorithms reusable for automated tests and benchmarking.

### ALT preprocessing

ALT spends time and memory computing landmark distances before answering queries. These distances are reused for subsequent searches.

This may improve query performance but introduces an initial preprocessing cost and additional memory consumption. Both need to be included when evaluating whether ALT is beneficial.

## Testing

The project includes unit tests for graph construction, road-network loading and shortest-path algorithms.

Spring MVC controller tests verify API response behavior and request validation.

The full test suite can be run using:

```bash
mvn -pl route-planner-api -am test
```

Additional end-to-end integration tests and systematic benchmark correctness checks are part of the remaining verification work.

## Benchmarking

The benchmarking framework is being developed separately from the REST API.

It will compare the algorithms using reproducible query sets, JVM warm-up, repeated measurements, correctness checks and multiple road-network sizes.

The evaluation will cover execution time, settled nodes, relaxed edges, preprocessing cost and memory usage.

Duan's single-source shortest-path algorithm is planned for separate comparison with single-source Dijkstra once its implementation and correctness have been established.
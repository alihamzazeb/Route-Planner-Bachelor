# Route Planner

Bachelor project at the Technical University of Denmark on shortest-path algorithms for large road networks. The application uses Java 21, Spring Boot and a directed graph stored in compressed sparse row (CSR) format.

The implemented algorithms are Dijkstra (point-to-point and single-source), Bidirectional Dijkstra, A* and ALT. A browser interface shows routes on a map and compares the algorithms on the same source and target nodes.

Duan's single-source algorithm and the reproducible performance evaluation are still in progress.

## Setup

Requires **Java 21**, **Maven**, the New York road-network files from the **9th DIMACS Shortest Paths Challenge**, and a **CARTO API key**.

1. Place `USA-road-d.NY.gr` and `USA-road-d.NY.co` in a `data/` directory at the repository root.
2. Create `route-planner-api/src/main/resources/static/config.local.js` with:

   ```js
   window.CARTO_API_KEY = "YOUR_CARTO_KEY";
   ```

3. Run `dk.dtu.routeplanner.api.RoutePlannerApplication` from your IDE, using the repository root as the working directory.
4. Open [http://localhost:8080/map.html](http://localhost:8080/map.html).

The data files and local API key are excluded from Git.

## Tests

Run from the repository root:

```bash
mvn -pl route-planner-api -am test
```

## API

- `GET /api/routes?source=10&target=500&algorithm=dijkstra` returns a route and search statistics.
- `GET /api/routes/compare?source=10&target=500&algorithm=alt` compares all four algorithms and returns the selected route for the map.

Node IDs are zero-based. Supported algorithms: `dijkstra`, `bidirectional`, `astar`, `alt`.
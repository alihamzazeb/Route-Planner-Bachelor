import * as maplibregl from "https://unpkg.com/maplibre-gl@6.13.0/dist/maplibre-gl.mjs";

const CARTO_KEY = window.CARTO_API_KEY;

if (!CARTO_KEY) {
    throw new Error("CARTO API key is missing");
}

const map = new maplibregl.Map({
    container: "map",
    style: `https://basemaps.cartocdn.com/gl/positron-gl-style/style.json?key=${encodeURIComponent(CARTO_KEY)}`,
    center: [-73.9855, 40.7580],
    zoom: 12
});

map.addControl(new maplibregl.NavigationControl(), "top-right");

const routeForm = document.getElementById("route-form");
const searchType = document.getElementById("search-type");
const sourceInput = document.getElementById("source");
const targetGroup = document.getElementById("target-group");
const targetInput = document.getElementById("target");
const algorithmInput = document.getElementById("algorithm");
const findButton = document.getElementById("find-route");
const clearButton = document.getElementById("clear-route");
const routeMessage = document.getElementById("route-message");
const routeResults = document.getElementById("route-results");
const routePlaceholder = document.getElementById("route-placeholder");
const compareButton = document.getElementById("compare-all");
const comparisonSection = document.getElementById("comparison-section");
const comparisonRoute = document.getElementById("comparison-route");
const comparisonMessage = document.getElementById("comparison-message");
const pointToPointTable = document.getElementById("point-to-point-table");
const singleSourceTable = document.getElementById("single-source-table");
const hideComparison = document.getElementById("hide-comparison");
const workspace = document.querySelector(".workspace");

// For each search type: the endpoint, its algorithms, and the text to show in each element (by id)
const searchTypes = {
    "point-to-point": {
        endpoint: "/api/routes",
        algorithms: {
            dijkstra: "Dijkstra",
            bidirectional: "Bidirectional Dijkstra",
            astar: "A*",
            alt: "ALT"
        },
        text: {
            "query-title": "Plan a Route",
            "query-description": "Choose a starting node, destination, and routing algorithm.",
            "find-route": "Find Route",
            "compare-all": "Compare All Algorithms",
            "result-title": "Route Details",
            "route-placeholder": "Calculate a route to see the results.",
            "result-distance-label": "Shortest-path distance",
            "result-distance-unit": "Road distance",
            "result-count-label": "Path nodes",
            "result-settled-label": "Settled nodes",
            "comparison-label": "Same source and destination",
            "comparison-title": "Algorithm Comparison",
            "comparison-description": "Search work and query time for all four algorithms on the same route."
        }
    },
    "single-source": {
        endpoint: "/api/single-source",
        algorithms: {
            duan: "Duan (single-source)",
            dijkstra: "Dijkstra (single-source)"
        },
        text: {
            "query-title": "Single-Source Search",
            "query-description": "Compute shortest-path distances from one source to every reachable node. There is no destination.",
            "find-route": "Compute All Distances",
            "compare-all": "Compare Dijkstra and Duan",
            "result-title": "Single-Source Results",
            "route-placeholder": "Run a single-source search to see the results.",
            "result-distance-label": "Reachable nodes (including source)",
            "result-distance-unit": "",
            "result-count-label": "Unreachable nodes",
            "result-settled-label": "Settled / completed nodes",
            "comparison-label": "Same source, all reachable nodes",
            "comparison-title": "Single-Source Comparison",
            "comparison-description": "Both algorithms compute all distances. Duan's graph preparation runs once at startup and is not included in the time. Duan's edge count refers to its internal graph. These are individual runs, not benchmark averages."
        }
    }
};

let startMarker = null;
let endMarker = null;
let requestId = 0;

const emptyRoute = {
    type: "FeatureCollection",
    features: []
};

const mapReady = new Promise((resolve) => {
    map.once("load", () => {
        map.addSource("route", {
            type: "geojson",
            data: emptyRoute
        });

        map.addLayer({
            id: "route-line",
            type: "line",
            source: "route",
            layout: {
                "line-join": "round",
                "line-cap": "round"
            },
            paint: {
                "line-color": "#2563eb",
                "line-width": 5,
                "line-opacity": 0.9
            }
        });

        resolve();
    });
});

function clearRoute() {
    const routeSource = map.getSource("route");

    if (routeSource) {
        routeSource.setData(emptyRoute);
    }

    if (startMarker) {
        startMarker.remove();
        startMarker = null;
    }

    if (endMarker) {
        endMarker.remove();
        endMarker = null;
    }
}

function drawRoute(coordinates) {
    clearRoute();

    if (coordinates.length === 0) {
        return;
    }

    if (coordinates.length > 1) {
        const geojson = {
            type: "Feature",
            properties: {},
            geometry: {
                type: "LineString",
                coordinates: coordinates
            }
        };

        map.getSource("route").setData(geojson);
    }

    startMarker = new maplibregl.Marker({ color: "#16a34a" })
        .setLngLat(coordinates[0])
        .addTo(map);

    if (coordinates.length === 1) {
        map.flyTo({ center: coordinates[0], zoom: 15 });
        return;
    }

    endMarker = new maplibregl.Marker({ color: "#dc2626" })
        .setLngLat(coordinates[coordinates.length - 1])
        .addTo(map);

    const bounds = new maplibregl.LngLatBounds();

    for (const coordinate of coordinates) {
        bounds.extend(coordinate);
    }

    map.fitBounds(bounds, { padding: 60, maxZoom: 15 });
}

function formatDistance(distance) {
    return `${(distance / 10000).toFixed(2)} km`;
}

function formatCount(count) {
    return count.toLocaleString("en-US");
}

function formatTime(timeMs) {
    return `${timeMs.toFixed(2)} ms`;
}

function setText(id, text) {
    document.getElementById(id).textContent = text;
}

function isSingleSource() {
    return searchType.value === "single-source";
}

function setComparisonMessage(message, isError = false) {
    comparisonMessage.textContent = message;
    comparisonMessage.hidden = !message;
    comparisonMessage.classList.toggle("error", isError);
}

// Responses that arrive after this are ignored, because their request id is out of date
function cancelRequest() {
    requestId++;
    findButton.disabled = false;
    compareButton.disabled = false;
}

function resetResults() {
    cancelRequest();
    routeMessage.textContent = "";
    routeMessage.classList.remove("error");
    routeResults.hidden = true;
    routePlaceholder.hidden = false;
    comparisonSection.hidden = true;
    setComparisonMessage("");
    clearRoute();
}

function configureSearchType() {
    const singleSource = isSingleSource();
    const { algorithms, text } = searchTypes[searchType.value];

    resetResults();

    // a hidden input that is required would still block the form, so it is disabled as well
    targetGroup.hidden = singleSource;
    targetInput.disabled = singleSource;
    algorithmInput.replaceChildren(...Object.entries(algorithms).map(([value, label]) => new Option(label, value)));

    for (const [id, value] of Object.entries(text)) {
        setText(id, value);
    }

    document.getElementById("result-time-item").hidden = !singleSource;
    document.getElementById("single-source-note").hidden = !singleSource;
    pointToPointTable.hidden = singleSource;
    singleSourceTable.hidden = !singleSource;
}

function showResult(data) {
    setText("result-algorithm", searchTypes[searchType.value].algorithms[data.algorithm]);
    setText("result-settled", formatCount(data.settledNodes));
    setText("result-relaxed", formatCount(data.relaxedEdges));

    if (isSingleSource()) {
        drawRoute([[data.sourceLongitude, data.sourceLatitude]]);
        setText("result-status", "Completed");
        setText("result-distance", formatCount(data.reachableNodes));
        setText("result-distance-unit", `of ${formatCount(data.nodeCount)} nodes`);
        setText("result-path-nodes", formatCount(data.unreachableNodes));
        setText("result-time", formatTime(data.timeMs));
        routeMessage.textContent = `All shortest-path distances from node ${data.source} computed. The map marks the source only.`;
    } else {
        drawRoute(data.coordinates);
        setText("result-status", data.reachable ? "Reachable" : "Unreachable");
        setText("result-distance", data.reachable ? formatDistance(data.distance) : "-");
        setText("result-path-nodes", formatCount(data.path.length));
        routeMessage.textContent = data.reachable ? "Route calculated successfully." : "No route exists between these nodes.";
    }

    routePlaceholder.hidden = true;
    routeResults.hidden = false;
}

function resetComparison() {
    for (const row of document.querySelectorAll(".comparison-table tbody tr")) {
        row.classList.remove("comparison-best");

        const fill = row.querySelector(".comparison-fill");

        if (fill) {
            fill.style.width = "0%";
        }

        for (const cell of row.querySelectorAll("[data-field]")) {
            cell.textContent = "-";
        }
    }
}

function showComparison(data) {
    const singleSource = isSingleSource();
    const table = singleSource ? singleSourceTable : pointToPointTable;

    for (const result of data.results) {
        const row = table.querySelector(`tr[data-algorithm="${result.algorithm}"]`);

        row.querySelector('[data-field="settled"]').textContent = formatCount(result.settledNodes);
        row.querySelector('[data-field="relaxed"]').textContent = formatCount(result.relaxedEdges);
        row.querySelector('[data-field="time"]').textContent = formatTime(result.timeMs);
    }

    if (singleSource) {
        const agree = data.distanceMismatches === 0;

        setComparisonMessage(agree
            ? `All ${formatCount(data.comparedNodes)} distances agree, including unreachable nodes.`
            : `${formatCount(data.distanceMismatches)} distances differ. Check the implementations.`, !agree);
    } else {
        showRouteComparison(data.results);
    }
}

// The point-to-point table also has the settled-nodes bar and the distance of each algorithm
function showRouteComparison(results) {
    const maxSettled = Math.max(1, ...results.map(result => result.settledNodes));
    const reachable = results.filter(result => result.reachable);
    const leastSettled = Math.min(...reachable.map(result => result.settledNodes));

    for (const result of results) {
        const row = pointToPointTable.querySelector(`tr[data-algorithm="${result.algorithm}"]`);

        row.querySelector(".comparison-fill").style.width = `${result.settledNodes / maxSettled * 100}%`;
        row.querySelector('[data-field="distance"]').textContent = result.reachable ? formatDistance(result.distance) : "No path";
        row.classList.toggle("comparison-best", result.reachable && result.settledNodes === leastSettled);
    }

    if (reachable.length === 0) {
        setComparisonMessage("No route exists between these nodes.");
    } else if (reachable.length !== results.length ||
            results.some(result => Math.abs(result.distance - reachable[0].distance) > 1e-6)) {
        setComparisonMessage("The algorithms did not return the same distance. Check the results.", true);
    } else {
        setComparisonMessage("");
    }
}

async function runSearch(compare) {
    if (!routeForm.reportValidity()) {
        return;
    }

    resetResults();

    const id = requestId;
    const singleSource = isSingleSource();
    const { endpoint, algorithms } = searchTypes[searchType.value];
    const params = new URLSearchParams({ source: sourceInput.value, algorithm: algorithmInput.value });

    if (!singleSource) {
        params.set("target", targetInput.value);
    }

    findButton.disabled = true;
    compareButton.disabled = true;

    if (compare) {
        resetComparison();
        comparisonRoute.textContent = singleSource
            ? `Source node ${sourceInput.value} to all reachable nodes`
            : `Node ${sourceInput.value} → Node ${targetInput.value}`;
        setComparisonMessage("Calculating comparison...");
        comparisonSection.hidden = false;
        comparisonSection.scrollIntoView({ behavior: "smooth", block: "start" });
    } else {
        routeMessage.textContent = singleSource ? "Computing all distances..." : "Calculating route...";
        workspace.scrollTo({ top: 0, behavior: "smooth" });
    }

    try {
        const response = await fetch(`${endpoint}${compare ? "/compare" : ""}?${params}`);
        const data = await response.json();

        if (id !== requestId) {
            return;
        }

        if (!response.ok) {
            throw new Error(data.message || "Search request failed");
        }

        if (compare && data.results?.length !== Object.keys(algorithms).length) {
            throw new Error("Incomplete comparison results");
        }

        await mapReady;

        if (id !== requestId) {
            return;
        }

        if (compare) {
            showResult(singleSource ? data.result : data.route);
            showComparison(data);
        } else {
            showResult(data);
        }

    } catch (error) {
        if (id !== requestId) {
            return;
        }

        clearRoute();
        routeResults.hidden = true;
        routePlaceholder.hidden = false;

        if (compare) {
            setComparisonMessage(`Error: ${error.message}`, true);
        } else {
            routeMessage.textContent = `Error: ${error.message}`;
            routeMessage.classList.add("error");
        }

    } finally {
        if (id === requestId) {
            findButton.disabled = false;
            compareButton.disabled = false;
        }
    }
}

searchType.addEventListener("change", configureSearchType);

routeForm.addEventListener("input", resetResults);

routeForm.addEventListener("submit", (event) => {
    event.preventDefault();
    runSearch(false);
});

compareButton.addEventListener("click", () => runSearch(true));

hideComparison.addEventListener("click", () => {
    cancelRequest();
    comparisonSection.hidden = true;
});

clearButton.addEventListener("click", () => {
    routeForm.reset();
    configureSearchType();
    workspace.scrollTo({ top: 0, behavior: "smooth" });
});

configureSearchType();

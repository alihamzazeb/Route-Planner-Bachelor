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
const clearButton = document.getElementById("clear-route");
const routeMessage = document.getElementById("route-message");
const routeResults = document.getElementById("route-results");
const routePlaceholder = document.getElementById("route-placeholder");
const compareButton = document.getElementById("compare-all");
const comparisonSection = document.getElementById("comparison-section");
const comparisonRoute = document.getElementById("comparison-route");
const comparisonMessage = document.getElementById("comparison-message");
const hideComparison = document.getElementById("hide-comparison");
const workspace = document.querySelector(".workspace");

let startMarker = null;
let endMarker = null;
let comparisonRequestId = 0;
let routeRequestId = 0;

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

function resetComparison() {
    for (const row of document.querySelectorAll(".comparison-table tbody tr")) {
        row.classList.remove("comparison-best");
        row.querySelector(".comparison-fill").style.width = "0%";

        for (const cell of row.querySelectorAll("[data-field]")) {
            cell.textContent = "—";
        }
    }
}

function showComparison(results) {
    const maxSettled = Math.max(1, ...results.map(result => result.settledNodes));
    const reachable = results.filter(result => result.reachable);
    const leastSettled = Math.min(...reachable.map(result => result.settledNodes));

    for (const result of results) {
        const row = document.querySelector(`.comparison-table tr[data-algorithm="${result.algorithm}"]`);

        if (!row) {
            continue;
        }

        row.querySelector(".comparison-fill").style.width =
            `${result.settledNodes / maxSettled * 100}%`;

        row.querySelector('[data-field="settled"]').textContent =
            result.settledNodes.toLocaleString("en-US");

        row.querySelector('[data-field="relaxed"]').textContent =
            result.relaxedEdges.toLocaleString("en-US");

        row.querySelector('[data-field="time"]').textContent =
            `${result.timeMs.toFixed(2)} ms`;

        row.querySelector('[data-field="distance"]').textContent =
            result.reachable ? `${(result.distance / 10000).toFixed(2)} km` : "No path";

        row.classList.toggle("comparison-best", result.reachable && result.settledNodes === leastSettled);
    }


    if (reachable.length === 0) {
        comparisonMessage.hidden = false;
        comparisonMessage.textContent = "No route exists between these nodes.";
    } else if (reachable.length !== results.length ||
            results.some(result => Math.abs(result.distance - reachable[0].distance) > 0.000001)) {
        comparisonMessage.hidden = false;
        comparisonMessage.textContent = "The algorithms did not return the same distance. Check the results.";
        comparisonMessage.classList.add("error");
    } else {
        comparisonMessage.textContent = "";
        comparisonMessage.hidden = true;
    }

}
routeForm.addEventListener("input", () => {
    routeRequestId++;
    comparisonRequestId++;

    routeMessage.textContent = "";
    routeResults.hidden = true;
    routePlaceholder.hidden = false;
    comparisonSection.hidden = true;

    compareButton.disabled = false;
    clearRoute();
});

routeForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const source = Number(document.getElementById("source").value);
    const target = Number(document.getElementById("target").value);
    const algorithm = document.getElementById("algorithm").value;
    const params = new URLSearchParams({ source, target, algorithm });

    comparisonRequestId++;
    compareButton.disabled = false;
    comparisonSection.hidden = true;
    clearRoute();
    routeResults.hidden = true;
    routePlaceholder.hidden = false;
    routeMessage.textContent = "Calculating route...";

    try {
        const response = await fetch(`/api/routes?${params}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "Route request failed");
        }

        document.getElementById("result-algorithm").textContent =
            data.algorithm.toUpperCase();

        document.getElementById("result-status").textContent =
            data.reachable ? "Reachable" : "Unreachable";

        document.getElementById("result-distance").textContent =
            data.reachable ? `${(data.distance / 10000).toFixed(2)} km` : "-";

        document.getElementById("result-path-nodes").textContent =
            data.path.length.toLocaleString("en-US");

        document.getElementById("result-settled").textContent =
            data.settledNodes.toLocaleString("en-US");

        document.getElementById("result-relaxed").textContent =
            data.relaxedEdges.toLocaleString("en-US");

        await mapReady;

        if (data.reachable) {
            drawRoute(data.coordinates);
        }

        routePlaceholder.hidden = true;
        routeResults.hidden = false;

        routeMessage.textContent = data.reachable
            ? "Route calculated successfully."
            : "No route exists between these nodes.";

    } catch (error) {
        clearRoute();
        routeResults.hidden = true;
        routePlaceholder.hidden = false;
        routeMessage.textContent = `Error: ${error.message}`;
    }
});

compareButton.addEventListener("click", async () => {
    if (!routeForm.reportValidity()) {
        return;
    }

    const source = document.getElementById("source").value;
    const target = document.getElementById("target").value;
    const params = new URLSearchParams({ source, target });
    const requestId = ++comparisonRequestId;

    resetComparison();
    comparisonRoute.textContent = `Node ${source} → Node ${target}`;
    comparisonMessage.hidden = false;
    comparisonMessage.textContent = "Calculating comparison...";
    comparisonMessage.classList.remove("error");
    comparisonSection.hidden = false;
    compareButton.disabled = true;

    comparisonSection.scrollIntoView({ behavior: "smooth", block: "start" });

    try {
        const response = await fetch(`/api/routes/compare?${params}`);
        const data = await response.json();

        if (requestId !== comparisonRequestId) {
            return;
        }

        if (!response.ok) {
            throw new Error(data.message || "Comparison request failed");
        }

        showComparison(data.results);
    } catch (error) {
        if (requestId !== comparisonRequestId) {
            return;
        }
        
        comparisonMessage.hidden = false;
        comparisonMessage.textContent = `Error: ${error.message}`;
        comparisonMessage.classList.add("error");
    } finally {
        if (requestId === comparisonRequestId) {
            compareButton.disabled = false;
        }
    }
});

hideComparison.addEventListener("click", () => {
    comparisonRequestId++;
    compareButton.disabled = false;
    comparisonSection.hidden = true;
});

clearButton.addEventListener("click", () => {
    comparisonRequestId++;
    compareButton.disabled = false;
    routeForm.reset();
    routeMessage.textContent = "";
    routeResults.hidden = true;
    routePlaceholder.hidden = false;
    comparisonSection.hidden = true;
    clearRoute();
    workspace.scrollTo({ top: 0, behavior: "smooth" });
});

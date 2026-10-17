
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

let startMarker = null;
let endMarker = null;

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
        map.flyTo({
            center: coordinates[0],
            zoom: 15
        });
        return;
    }

    endMarker = new maplibregl.Marker({ color: "#dc2626" })
        .setLngLat(coordinates[coordinates.length - 1])
        .addTo(map);

    const bounds = new maplibregl.LngLatBounds();

    for (const coordinate of coordinates) {
        bounds.extend(coordinate);
    }

    map.fitBounds(bounds, {
        padding: 60,
        maxZoom: 15
    });
}

routeForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const source = Number(document.getElementById("source").value);
    const target = Number(document.getElementById("target").value);
    const algorithm = document.getElementById("algorithm").value;

    const params = new URLSearchParams({ source, target, algorithm });

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
            data.reachable
                ? (data.distance / 10000).toFixed(2) + " km"
                : "-";

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

clearButton.addEventListener("click", () => {
    routeForm.reset();
    routeMessage.textContent = "";
    routeResults.hidden = true;
    routePlaceholder.hidden = false;
    clearRoute();
});

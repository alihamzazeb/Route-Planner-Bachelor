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
const targetInput = document.getElementById("target");
const findButton = document.getElementById("find-route");
const compareButton = document.getElementById("compare-all");
const routeMessage = document.getElementById("route-message");
const routeResults = document.getElementById("route-results");
const routePlaceholder = document.getElementById("route-placeholder");
const comparisonSection = document.getElementById("comparison-section");
const comparisonMessage = document.getElementById("comparison-message");
const workspace = document.querySelector(".workspace");

const emptyRoute = { type: "FeatureCollection", features: [] };

let markers = [];
let requestId = 0;

const mapReady = new Promise((resolve) => {
    map.once("load", () => {
        map.addSource("route", { type: "geojson", data: emptyRoute });
        map.addLayer({
            id: "route-line",
            type: "line",
            source: "route",
            layout: { "line-join": "round", "line-cap": "round" },
            paint: { "line-color": "#2563eb", "line-width": 5, "line-opacity": 0.9 }
        });
        resolve();
    });
});

function clearRoute() {
    map.getSource("route")?.setData(emptyRoute);
    markers.forEach(marker => marker.remove());
    markers = [];
}

function addMarker(coordinate, color) {
    markers.push(new maplibregl.Marker({ color }).setLngLat(coordinate).addTo(map));
}

// Green marker at the start, red marker at the end. A single coordinate (single-source search) only gets the start marker
function drawRoute(coordinates) {
    clearRoute();

    if (coordinates.length === 0) {
        return;
    }

    addMarker(coordinates[0], "#16a34a");

    if (coordinates.length === 1) {
        map.flyTo({ center: coordinates[0], zoom: 15 });
        return;
    }

    addMarker(coordinates.at(-1), "#dc2626");
    map.getSource("route").setData({ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates } });

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

function setMessage(element, message, isError = false) {
    element.textContent = message;
    element.classList.toggle("error", isError);
}

function setButtonsDisabled(disabled) {
    findButton.disabled = disabled;
    compareButton.disabled = disabled;
}

function isSingleSource() {
    return searchType.value === "single-source";
}

// The algorithm dropdown of the selected search type
function algorithmInput() {
    return document.getElementById(`${searchType.value}-algorithm`);
}

// A response that is still on its way is ignored, because its request id is now out of date
function cancelRequest() {
    requestId++;
    setButtonsDisabled(false);
}

function resetResults() {
    cancelRequest();
    clearRoute();
    setMessage(routeMessage, "");
    setMessage(comparisonMessage, "");
    routeResults.hidden = true;
    routePlaceholder.hidden = false;
    comparisonSection.hidden = true;
}

function configureSearchType() {
    for (const element of document.querySelectorAll("[data-search-type]")) {
        element.hidden = element.dataset.searchType !== searchType.value;
    }

    // a hidden input that is required would still block the form, so it is disabled as well
    targetInput.disabled = isSingleSource();
}

function showResult(data) {
    setText("result-algorithm", algorithmInput().selectedOptions[0].text);
    setText("result-settled", formatCount(data.settledNodes));
    setText("result-relaxed", formatCount(data.relaxedEdges));

    if (isSingleSource()) {
        drawRoute([[data.sourceLongitude, data.sourceLatitude]]);
        setText("result-status", "Completed");
        setText("result-distance", formatCount(data.reachableNodes));
        setText("result-distance-unit", `of ${formatCount(data.nodeCount)} nodes`);
        setText("result-count", formatCount(data.unreachableNodes));
        setText("result-time", formatTime(data.timeMs));
        setMessage(routeMessage, `All shortest-path distances from node ${data.source} computed. The map marks the source only.`);
    } else {
        drawRoute(data.coordinates);
        setText("result-status", data.reachable ? "Reachable" : "Unreachable");
        setText("result-distance", data.reachable ? formatDistance(data.distance) : "-");
        setText("result-distance-unit", "Road distance");
        setText("result-count", formatCount(data.path.length));
        setMessage(routeMessage, data.reachable ? "Route calculated successfully." : "No route exists between these nodes.");
    }

    routePlaceholder.hidden = true;
    routeResults.hidden = false;
}

function resetComparison() {
    comparisonSection.querySelectorAll(".comparison-best").forEach(row => row.classList.remove("comparison-best"));
    comparisonSection.querySelectorAll(".comparison-fill").forEach(fill => fill.style.width = "0%");
    comparisonSection.querySelectorAll("[data-field]").forEach(cell => cell.textContent = "-");
}

function showComparison(data) {
    const table = comparisonSection.querySelector(`table[data-search-type="${searchType.value}"]`);

    for (const result of data.results) {
        const row = table.querySelector(`tr[data-algorithm="${result.algorithm}"]`);

        row.querySelector('[data-field="settled"]').textContent = formatCount(result.settledNodes);
        row.querySelector('[data-field="relaxed"]').textContent = formatCount(result.relaxedEdges);
        row.querySelector('[data-field="time"]').textContent = formatTime(result.timeMs);
    }

    if (isSingleSource()) {
        const agree = data.distanceMismatches === 0;

        setMessage(comparisonMessage, agree
            ? `All ${formatCount(data.comparedNodes)} distances agree, including unreachable nodes.`
            : `${formatCount(data.distanceMismatches)} distances differ. Check the implementations.`, !agree);
    } else {
        showRouteComparison(table, data.results);
    }
}

// The point-to-point table also has the settled-nodes bar and the distance of each algorithm
function showRouteComparison(table, results) {
    const maxSettled = Math.max(1, ...results.map(result => result.settledNodes));
    const reachable = results.filter(result => result.reachable);
    const leastSettled = Math.min(...reachable.map(result => result.settledNodes));

    for (const result of results) {
        const row = table.querySelector(`tr[data-algorithm="${result.algorithm}"]`);

        row.querySelector(".comparison-fill").style.width = `${result.settledNodes / maxSettled * 100}%`;
        row.querySelector('[data-field="distance"]').textContent = result.reachable ? formatDistance(result.distance) : "No path";
        row.classList.toggle("comparison-best", result.reachable && result.settledNodes === leastSettled);
    }

    if (reachable.length === 0) {
        setMessage(comparisonMessage, "No route exists between these nodes.");
    } else if (reachable.length !== results.length ||
            results.some(result => Math.abs(result.distance - reachable[0].distance) > 1e-6)) {
        setMessage(comparisonMessage, "The algorithms did not return the same distance. Check the results.", true);
    } else {
        setMessage(comparisonMessage, "");
    }
}

async function runSearch(compare) {
    if (!routeForm.reportValidity()) {
        return;
    }

    resetResults();
    setButtonsDisabled(true);

    const id = requestId;
    const singleSource = isSingleSource();
    const source = sourceInput.value;
    const target = targetInput.value;
    const endpoint = (singleSource ? "/api/single-source" : "/api/routes") + (compare ? "/compare" : "");
    const params = new URLSearchParams({ source, algorithm: algorithmInput().value });

    if (!singleSource) {
        params.set("target", target);
    }

    if (compare) {
        resetComparison();
        setText("comparison-route", singleSource ? `Source node ${source} to all reachable nodes` : `Node ${source} → Node ${target}`);
        setMessage(comparisonMessage, "Calculating comparison...");
        comparisonSection.hidden = false;
        comparisonSection.scrollIntoView({ behavior: "smooth", block: "start" });
    } else {
        setMessage(routeMessage, singleSource ? "Computing all distances..." : "Calculating route...");
        workspace.scrollTo({ top: 0, behavior: "smooth" });
    }

    try {
        const response = await fetch(`${endpoint}?${params}`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "Search request failed");
        }

        // the result can only be drawn once the map has loaded
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
        if (id === requestId) {
            setMessage(compare ? comparisonMessage : routeMessage, `Error: ${error.message}`, true);
        }

    } finally {
        if (id === requestId) {
            setButtonsDisabled(false);
        }
    }
}

// any change in the form, also of the search type, makes the shown results out of date
routeForm.addEventListener("input", resetResults);
searchType.addEventListener("change", configureSearchType);
compareButton.addEventListener("click", () => runSearch(true));

routeForm.addEventListener("submit", (event) => {
    event.preventDefault();
    runSearch(false);
});

document.getElementById("hide-comparison").addEventListener("click", () => {
    cancelRequest();
    comparisonSection.hidden = true;
});

document.getElementById("clear-route").addEventListener("click", () => {
    routeForm.reset();
    resetResults();
    configureSearchType();
    workspace.scrollTo({ top: 0, behavior: "smooth" });
});

// the browser can restore the selected search type on reload, so the page is set up from the dropdown
configureSearchType();

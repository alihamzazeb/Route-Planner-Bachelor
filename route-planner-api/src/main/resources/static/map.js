import * as maplibregl from "https://unpkg.com/maplibre-gl@6.13.0/dist/maplibre-gl.mjs";

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
const mapMessage = document.getElementById("map-message");
const workspace = document.querySelector(".workspace");
const emptyRoute = { type: "FeatureCollection", features: [] };
const styleUrl = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json";

let map;
let routeSource;
let routeCoordinates = [];
let markers = [];
let activeRequest;

try {
    map = new maplibregl.Map({
        container: "map",
        style: window.CARTO_API_KEY ? `${styleUrl}?key=${encodeURIComponent(window.CARTO_API_KEY)}` : styleUrl,
        center: [-73.9855, 40.7580],
        zoom: 12
    });
    map.addControl(new maplibregl.NavigationControl(), "top-right");
    map.on("error", () => { mapMessage.hidden = false; });
    map.once("load", () => {
        map.addSource("route", { type: "geojson", data: emptyRoute });
        map.addLayer({
            id: "route-line",
            type: "line",
            source: "route",
            layout: { "line-join": "round", "line-cap": "round" },
            paint: { "line-color": "#2563eb", "line-width": 5, "line-opacity": 0.9 }
        });
        routeSource = map.getSource("route");
        mapMessage.hidden = true;
        drawRoute(routeCoordinates);
    });
} catch {
    mapMessage.hidden = false;
}

function addMarker(coordinate, color) {
    markers.push(new maplibregl.Marker({ color }).setLngLat(coordinate).addTo(map));
}

function drawRoute(coordinates) {
    // Keep only the latest result if the map has not loaded yet.
    routeCoordinates = coordinates;
    markers.forEach(marker => marker.remove());
    markers = [];
    if (!routeSource) return;

    routeSource.setData(coordinates.length > 1
        ? { type: "Feature", properties: {}, geometry: { type: "LineString", coordinates } }
        : emptyRoute);
    if (coordinates.length === 0) return;

    addMarker(coordinates[0], "#16a34a");
    if (coordinates.length === 1) {
        map.flyTo({ center: coordinates[0], zoom: 15 });
        return;
    }

    addMarker(coordinates.at(-1), "#dc2626");
    const bounds = new maplibregl.LngLatBounds();
    for (const coordinate of coordinates) bounds.extend(coordinate);
    map.fitBounds(bounds, { padding: 60, maxZoom: 15 });
}

// DIMACS distances are in tenths of a metre.
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

function setLoading(loading) {
    findButton.disabled = loading;
    compareButton.disabled = loading;
    routeForm.setAttribute("aria-busy", String(loading));
}

function isSingleSource() {
    return searchType.value === "single-source";
}

function algorithmInput() {
    return document.getElementById(`${searchType.value}-algorithm`);
}

function cancelRequest() {
    activeRequest?.abort();
    activeRequest = undefined;
    setLoading(false);
}

function resetResults() {
    cancelRequest();
    drawRoute([]);
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
    // A hidden required input still blocks validation unless disabled.
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
    const singleSource = isSingleSource();
    const table = comparisonSection.querySelector(`table[data-search-type="${searchType.value}"]`);
    const reachable = data.results.filter(result => result.reachable);
    const maxSettled = Math.max(1, ...data.results.map(result => result.settledNodes));
    const leastSettled = Math.min(...reachable.map(result => result.settledNodes));

    for (const result of data.results) {
        const row = table.querySelector(`tr[data-algorithm="${result.algorithm}"]`);
        row.querySelector('[data-field="settled"]').textContent = formatCount(result.settledNodes);
        row.querySelector('[data-field="relaxed"]').textContent = formatCount(result.relaxedEdges);
        row.querySelector('[data-field="time"]').textContent = formatTime(result.timeMs);
        if (!singleSource) {
            row.querySelector(".comparison-fill").style.width = `${result.settledNodes / maxSettled * 100}%`;
            row.querySelector('[data-field="distance"]').textContent = result.reachable ? formatDistance(result.distance) : "No path";
            row.classList.toggle("comparison-best", result.reachable && result.settledNodes === leastSettled);
        }
    }

    if (singleSource) {
        const agree = data.distanceMismatches === 0;
        setMessage(comparisonMessage, agree
            ? `All ${formatCount(data.comparedNodes)} distances agree, including unreachable nodes.`
            : `${formatCount(data.distanceMismatches)} distances differ. Check the implementations.`, !agree);
    } else if (reachable.length === 0) {
        setMessage(comparisonMessage, "No route exists between these nodes.");
    } else if (reachable.length !== data.results.length ||
            data.results.some(result => Math.abs(result.distance - reachable[0].distance) > 1e-6)) {
        setMessage(comparisonMessage, "The algorithms did not return the same distance. Check the results.", true);
    } else {
        setMessage(comparisonMessage, "");
    }
}

function hasNumbers(data, fields) {
    return data && fields.every(field => Number.isFinite(data[field]));
}

function validateResponse(data, compare, singleSource) {
    const result = compare ? (singleSource ? data?.result : data?.route) : data;
    const fields = ["settledNodes", "relaxedEdges"];
    if (singleSource) fields.push("source", "reachableNodes", "unreachableNodes", "nodeCount", "timeMs", "sourceLongitude", "sourceLatitude");
    if (!hasNumbers(result, fields)) throw new Error("The server returned an incomplete search result.");

    if (!singleSource && (typeof result.reachable !== "boolean" || !Array.isArray(result.path) ||
            !Array.isArray(result.coordinates) || result.coordinates.length !== result.path.length ||
            (result.reachable && !Number.isFinite(result.distance)) ||
            result.coordinates.some(point => !Array.isArray(point) || point.length !== 2 || !point.every(Number.isFinite)))) {
        throw new Error("The server returned invalid route data.");
    }
    if (compare) {
        const table = comparisonSection.querySelector(`table[data-search-type="${searchType.value}"]`);
        const algorithms = [...table.querySelectorAll("tbody tr")].map(row => row.dataset.algorithm);
        if (!Array.isArray(data.results) || data.results.length !== algorithms.length ||
                new Set(data.results.map(item => item?.algorithm)).size !== algorithms.length ||
                data.results.some(item => !hasNumbers(item, ["settledNodes", "relaxedEdges", "timeMs"]) ||
                    !algorithms.includes(item.algorithm) || (!singleSource &&
                        (typeof item.reachable !== "boolean" || (item.reachable && !Number.isFinite(item.distance))))) ||
                (singleSource && !hasNumbers(data, ["comparedNodes", "distanceMismatches"]))) {
            throw new Error("The server returned invalid comparison data.");
        }
    }
    return result;
}

async function runSearch(compare) {
    if (!routeForm.reportValidity()) return;
    resetResults();
    const request = new AbortController();
    activeRequest = request;
    setLoading(true);

    const singleSource = isSingleSource();
    const source = sourceInput.value;
    const target = targetInput.value;
    const endpoint = (singleSource ? "/api/single-source" : "/api/routes") + (compare ? "/compare" : "");
    const params = new URLSearchParams({ source, algorithm: algorithmInput().value });
    if (!singleSource) params.set("target", target);

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
        const response = await fetch(`${endpoint}?${params}`, { signal: request.signal });
        let data;
        try {
            data = await response.json();
        } catch {
            throw new Error(response.ok ? "The server returned invalid JSON." : `Search request failed (HTTP ${response.status}).`);
        }
        if (request.signal.aborted) return;
        if (!response.ok) throw new Error(typeof data?.message === "string" ? data.message : `Search request failed (HTTP ${response.status}).`);

        const result = validateResponse(data, compare, singleSource);
        showResult(result);
        if (compare) showComparison(data);
    } catch (error) {
        if (!request.signal.aborted) {
            setMessage(compare ? comparisonMessage : routeMessage, `Error: ${error.message}`, true);
        }
    } finally {
        if (activeRequest === request) {
            activeRequest = undefined;
            setLoading(false);
        }
    }
}

routeForm.addEventListener("input", resetResults);
searchType.addEventListener("change", configureSearchType);
compareButton.addEventListener("click", () => runSearch(true));
routeForm.addEventListener("submit", event => {
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

configureSearchType();

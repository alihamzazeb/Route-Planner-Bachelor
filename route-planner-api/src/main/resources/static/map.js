import * as maplibregl from
    "https://unpkg.com/maplibre-gl@6.13.0/dist/maplibre-gl.mjs";

const CARTO_KEY = window.CARTO_API_KEY;

const map = new maplibregl.Map({

    container: "map",

    style: `https://basemaps.cartocdn.com/gl/positron-gl-style/style.json?key=${encodeURIComponent(CARTO_KEY)}`,

    center: [-73.9855, 40.7580],

    zoom: 12

});

map.addControl(new maplibregl.NavigationControl(), "top-right");
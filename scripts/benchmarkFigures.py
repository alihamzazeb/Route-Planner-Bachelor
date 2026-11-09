
import csv
import json
import math
from pathlib import Path
from textwrap import fill

import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter



ROOT = Path(__file__).resolve().parent.parent
RESULTS = ROOT / "benchmark-data" / "results"
FIGURES = ROOT / "report-figures"


def result_file(filename):
    experiment = (
        "experiment-a"
        if filename.startswith("NY-single-source-")
        else "experiment-b"
    )
    return RESULTS / experiment / filename


COLORS = {
    "dijkstra": "#0072B2",
    "bidirectional": "#E69F00",
    "astar": "#009E73",
    "alt": "#CC79A7",
    "duan": "#E69F00",
    "random": "#0072B2",
    "farthest": "#009E73"
}

NAMES = {
    "dijkstra": "Dijkstra",
    "bidirectional": "Bidirectional Dijkstra",
    "astar": "A*",
    "alt": "ALT",
    "duan": "Duan"
}

ORDER = ["dijkstra", "bidirectional", "astar", "alt"]

JMH_NOTE = (
    "Error bars: JMH 99.9% confidence intervals "
    "(3 forks, 10 measurement iterations per fork)."
)

ROUTES_NOTE = "NY: 100 fixed routes (33 short, 33 medium, 34 long); ALT: 4 random landmarks, seed 2026."

plt.rcParams.update({
    "font.size": 10,
    "axes.titlesize": 12,
    "axes.labelsize": 10,
    "savefig.dpi": 220,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "legend.frameon": False,
    "pdf.fonttype": 42
})


# Shared functions

def jmh(filename):
    with result_file(filename).open(encoding="utf-8") as f:
        records = json.load(f)

    for record in records:
        if record["mode"] != "avgt" or record["primaryMetric"]["scoreUnit"] != "ms/op":
            raise ValueError(f"Expected average time in ms/op in {filename}")
        allocation = record.get("secondaryMetrics", {}).get("gc.alloc.rate.norm")
        if allocation and allocation["scoreUnit"] != "B/op":
            raise ValueError(f"Expected allocation in B/op in {filename}")

    return records


def csv_rows(filename):
    with result_file(filename).open(
        newline="", encoding="utf-8"
    ) as f:
        return [
            {k.strip(): v.strip() for k, v in row.items()}
            for row in csv.DictReader(f)
        ]


def by_param(records, key):
    return {r["params"][key]: r for r in records}


def score(record):
    return record["primaryMetric"]["score"]


def error(record):
    return record["primaryMetric"].get("scoreError", 0.0)


def save(fig, experiment, filename, note=""):
    folder = FIGURES / experiment
    folder.mkdir(parents=True, exist_ok=True)

    if note:
        note = "\n".join(fill(line, width=115) for line in note.splitlines())
        fig.text(0.02, 0.02, note, fontsize=8, color="#444444", va="bottom")
    fig.tight_layout(rect=(0, 0.14 if note else 0, 1, 1))
    fig.savefig(folder / (filename + ".pdf"), bbox_inches="tight")

    plt.close(fig)


def new_plot(title, ylabel, xlabel=None):
    fig, ax = plt.subplots(figsize=(8.6, 5.6))

    ax.set_title(title, pad=14)
    ax.set_ylabel(ylabel)

    if xlabel:
        ax.set_xlabel(xlabel)

    ax.grid(axis="y", alpha=0.22)
    ax.set_axisbelow(True)

    return fig, ax


def bars(experiment, filename, title, labels,
         values, ylabel, errors=None, colors=None, units="",
         *, note="", xlabel=None, annotations=None):

    fig, ax = new_plot(title, ylabel, xlabel)

    palette = (
        colors if colors is not None
        else [f"C{i}" for i in range(len(labels))]
    )

    xs = list(range(len(labels)))

    ax.bar(
        xs,
        values,
        yerr=errors,
        capsize=5 if errors else 0,
        color=palette,
        width=0.64
    )

    ax.set_xticks(xs, [label.replace("Bidirectional Dijkstra", "Bidirectional\nDijkstra") for label in labels])

    ax.set_ylim(
        0,
        max(
            v + (errors[i] if errors else 0)
            for i, v in enumerate(values)
        ) * (1.30 if annotations else 1.20)
    )

    for x, value in zip(xs, values):
        label = f"{value:,.2f}{units}" if units else f"{value:,.0f}"
        if annotations:
            label += "\n" + annotations[x]
        ax.annotate(
            label,
            (x, value + (errors[x] if errors else 0)),
            xytext=(0, 8),
            textcoords="offset points",
            ha="center",
            fontsize=9
        )

    save(fig, experiment, filename, note)


# EXPERIMENT A: SINGLE-SOURCE

def experiment_a():

    times = by_param(
        jmh("NY-single-source-time.json"),
        "algorithmName"
    )

    allocation = by_param(
        jmh("NY-single-source-allocation.json"),
        "algorithmName"
    )

    prep = score(
        jmh("NY-single-source-preparation.json")[0]
    )

    algorithms = ["dijkstra", "duan"]
    labels = [NAMES[x] for x in algorithms]
    colors = [COLORS[x] for x in algorithms]

    # Figure A1: Execution time
    bars(
        "experiment-a",
        "01_query_time",
        "Single-source query time",
        labels,
        [score(times[x]) for x in algorithms],
        "Average time (ms per source)",
        [error(times[x]) for x in algorithms],
        colors,
        " ms",
        note="NY: 10 fixed sources; graph preparation excluded. Ratios compare mean query times.\n" + JMH_NOTE,
        annotations=["Baseline", f"{score(times['duan']) / score(times['dijkstra']):.2f}x Dijkstra time"]
    )

    # Figure A2: Temporary memory allocation
    memory = [
        allocation[x]["secondaryMetrics"]
        ["gc.alloc.rate.norm"]["score"] / 1e6
        for x in algorithms
    ]

    bars(
        "experiment-a",
        "02_allocation",
        "Duan vs Dijkstra: memory allocation per query",
        labels,
        memory,
        "Allocated MB per single-source query",
        errors=[allocation[x]["secondaryMetrics"]["gc.alloc.rate.norm"]["scoreError"] / 1e6 for x in algorithms],
        colors=colors,
        units=" MB",
        annotations=["Baseline", f"{memory[1] / memory[0]:.2f}x Dijkstra allocation"],
        note="NY: 10 fixed sources. Total allocated bytes per query, not peak heap; 1 MB = 1,000,000 bytes.\n" + JMH_NOTE
    )

    # Figure A3: Estimated total runtime
    fig, ax = new_plot(
        "Estimated cumulative runtime (including Duan preparation)",
        "Estimated total time (ms)",
        "Number of single-source queries"
    )

    queries = list(range(0, 101))

    dijkstra = [
        n * score(times["dijkstra"])
        for n in queries
    ]

    duan = [
        prep + n * score(times["duan"])
        for n in queries
    ]

    ax.plot(
        queries,
        dijkstra,
        label=f"Dijkstra ({score(times['dijkstra']):.2f} ms/query)",
        color=COLORS["dijkstra"]
    )

    ax.plot(
        queries,
        duan,
        label=f"Prepared Duan ({score(times['duan']):.2f} ms/query)",
        color=COLORS["duan"]
    )

    ax.legend(loc="upper left")
    ax.set_xlim(0, 100)
    ax.set_ylim(bottom=0)
    if prep >= 0 and score(times["duan"]) >= score(times["dijkstra"]):
        ax.text(0.98, 0.05, f"Duan setup: {prep:.2f} ms\nNo per-query saving to recover the setup cost",
                transform=ax.transAxes, ha="right", va="bottom", fontsize=9)

    save(
        fig,
        "experiment-a",
        "03_modelled_total_time",
        "Model: setup cost + query count x mean query time; graph loading excluded.\n"
        "Setup and query means come from separate JMH runs; lines are estimates, not measured sequences."
    )


# EXPERIMENT B: POINT-TO-POINT

def experiment_b():

    times = by_param(
        jmh("NY-balanced-time.json"),
        "algorithmName"
    )

    # Figure B1: Overall execution time
    bars(
        "experiment-b",
        "01_query_time",
        "Point-to-point query time: 100 balanced routes",
        [NAMES[a] for a in ORDER],
        [score(times[a]) for a in ORDER],
        "Average time (ms per route)",
        [error(times[a]) for a in ORDER],
        [COLORS[a] for a in ORDER],
        " ms",
        note=ROUTES_NOTE + "\n" + JMH_NOTE + " Ratios compare means.",
        annotations=["Baseline"] + [f"{score(times['dijkstra']) / score(times[a]):.2f}x speedup" for a in ORDER[1:]]
    )

    # Figures B2 and B3: Search effort
    counters = csv_rows(
        "NY-balanced-seed42-counters.csv"
    )

    metrics = [
        (
            "settled",
            "02_settled_nodes",
            "Search effort: settled nodes",
            "Average settled nodes per route"
        ),
        (
            "relaxed",
            "03_relaxed_edges",
            "Search effort: examined edges",
            "Average examined edges per route"
        )
    ]

    for metric, filename, title, ylabel in metrics:

        means = {}

        for a in ORDER:
            values = [
                float(row[metric])
                for row in counters
                if row["algorithm"] == NAMES[a]
            ]

            if len(values) != 100:
                raise ValueError(
                    f"Expected 100 results for {NAMES[a]}"
                )

            means[a] = sum(values) / len(values)

        bars(
            "experiment-b",
            filename,
            title,
            [NAMES[a] for a in ORDER],
            [means[a] for a in ORDER],
            ylabel,
            colors=[COLORS[a] for a in ORDER],
            annotations=["Baseline"] + [f"{100 * (1 - means[a] / means['dijkstra']):.1f}% fewer" for a in ORDER[1:]],
            note=ROUTES_NOTE + "\nArithmetic means of per-route counts; percentages compare means, not per-route ratios."
        )

    # Figure B4: Short, medium and long routes
    length_data = jmh(
        "NY-balanced-route-length.json"
    )

    suffixes = [
        "shortRoutes",
        "mediumRoutes",
        "longRoutes"
    ]

    fig, ax = new_plot(
        "Query time by route distance",
        "Average time (ms per route, logarithmic scale)",
        "Route category"
    )

    for a, marker in zip(ORDER, ["o", "s", "^", "D"]):

        records = [
            next(
                r for r in length_data
                if r["params"]["algorithmName"] == a
                and r["benchmark"].endswith("." + suffix)
            )
            for suffix in suffixes
        ]

        ax.errorbar(
            range(3),
            [score(r) for r in records],
            yerr=[error(r) for r in records],
            capsize=4,
            marker=marker,
            linewidth=2,
            label=NAMES[a],
            color=COLORS[a]
        )

    ax.set_xticks(
        range(3),
        [
            "Short (<5 km)\nn = 33 routes",
            "Medium (5-20 km)\nn = 33 routes",
            "Long (>=20 km)\nn = 34 routes"
        ]
    )

    ax.set_yscale("log")
    ax.set_yticks([0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 20])
    ax.yaxis.set_major_formatter(FuncFormatter(lambda value, _: f"{value:g}"))
    ax.legend()

    save(
        fig,
        "experiment-b",
        "04_route_distance_logscale",
        JMH_NOTE + "\nEach point averages its fixed category; overlapping intervals do not establish an ordering."
    )

    # Figure B5: Temporary allocation
    allocations = by_param(
        jmh("NY-balanced-allocation.json"),
        "algorithmName"
    )

    memory = [
        allocations[a]["secondaryMetrics"]
        ["gc.alloc.rate.norm"]["score"] / 1e6
        for a in ORDER
    ]

    bars(
        "experiment-b",
        "05_allocation",
        "Point-to-point heap allocation",
        [NAMES[a] for a in ORDER],
        memory,
        "Allocated MB per route",
        errors=[allocations[a]["secondaryMetrics"]["gc.alloc.rate.norm"]["scoreError"] / 1e6 for a in ORDER],
        colors=[COLORS[a] for a in ORDER],
        units=" MB",
        note="NY: 100 fixed routes. Total allocated bytes per query, not peak heap; 1 MB = 1,000,000 bytes.\n" + JMH_NOTE
    )

    # Figure B6: Random landmark count vs query time
    landmark_times = by_param(
        jmh("NY-landmarks-time.json"),
        "landmarkCount"
    )

    counts = [1, 2, 4, 8]
    query_times = [score(landmark_times[str(k)]) for k in counts]
    query_changes = ["Baseline"] + [
        f"{100 * (1 - query_times[i] / query_times[i - 1]):.1f}% lower mean\n"
        f"than {counts[i - 1]} landmark{'s' if counts[i - 1] > 1 else ''}"
        for i in range(1, len(counts))
    ]

    bars(
        "experiment-b",
        "06_random_landmarks_query_time",
        "ALT query time by landmark count",
        [str(k) for k in counts],
        query_times,
        "Average time (ms per route)",
        [
            error(landmark_times[str(k)])
            for k in counts
        ],
        colors=[COLORS["alt"]] * len(counts),
        units=" ms",
        xlabel="Number of landmarks",
        annotations=query_changes,
        note="NY: the same 100 routes; random selection, seed 2026; preparation excluded.\n"
        "Percentages compare mean query times with the previous landmark count; gains are not monotonic.\n" + JMH_NOTE
    )

    # Figure B7: Landmark preprocessing
    preprocessing = csv_rows(
        "NY-landmarks-preprocessing.csv"
    )

    prep_means = []
    prep_trials = []

    for k in counts:
        values = [
            float(r["preprocessing_ms"])
            for r in preprocessing
            if int(r["landmarks"]) == k
        ]

        prep_means.append(
            sum(values) / len(values)
        )
        prep_trials.append(values)

    fig, ax = new_plot("ALT: landmark preprocessing cost", "Preprocessing time (ms)", "Number of landmarks")
    for x, values in enumerate(prep_trials):
        offsets = [(i - (len(values) - 1) / 2) * 0.045 for i in range(len(values))]
        ax.scatter([x + offset for offset in offsets], values, color=COLORS["alt"],
                   s=40, label="Individual run" if x == 0 else None)
        ax.annotate(f"{prep_means[x]:.2f} ms", (x, max(values)), xytext=(0, 10),
                    textcoords="offset points", ha="center", fontsize=9)
    ax.plot(range(len(counts)), prep_means, linestyle="none", marker="_", markersize=28,
            markeredgewidth=2, color="#222222", label="Mean")
    ax.set_xticks(range(len(counts)), [str(k) for k in counts])
    ax.set_xlim(-0.5, len(counts) - 0.5)
    ax.set_ylim(0, max(max(values) for values in prep_trials) * 1.18)
    ax.legend(loc="upper left")
    save(
        fig,
        "experiment-b",
        "07_random_landmarks_preprocessing",
        "All 5 recorded runs per count are shown after one warmup in a single process.\n"
        "Dots show run-to-run variation, not confidence intervals; random landmarks, seed 2026."
    )

    # Figures B8 and B9: Settled nodes and table memory
    landmark_counters = csv_rows(
        "NY-landmarks-counters.csv"
    )

    landmark_map = {
        int(r["landmarks"]): r
        for r in landmark_counters
    }

    bars(
        "experiment-b",
        "08_random_landmarks_settled",
        "ALT: settled nodes by landmark count",
        [str(k) for k in counts],
        [
            float(landmark_map[k]["mean_settled"])
            for k in counts
        ],
        "Average settled nodes per route",
        colors=[COLORS["alt"]] * len(counts),
        xlabel="Number of landmarks",
        note="NY: the same 100 routes; random selection, seed 2026.\nArithmetic means of per-route counts; these are search-work measurements, not query times."
    )

    bars(
        "experiment-b",
        "09_landmark_table_storage",
        "ALT: persistent landmark distance tables",
        [str(k) for k in counts],
        [
            float(landmark_map[k]["distance_tables_mib"])
            for k in counts
        ],
        "Distance-table storage (MiB)",
        colors=[COLORS["alt"]] * len(counts),
        units=" MiB",
        xlabel="Number of landmarks",
        note="Payload of forward and reverse double-distance tables: 2 x landmarks x nodes x 8 bytes.\nExcludes graph storage and object headers; 1 MiB = 1,048,576 bytes."
    )

    # Figures B10 and B11: Random vs farthest-first
    placement_times = jmh(
        "NY-landmark-selection-time.json"
    )

    placement_setup = csv_rows(
        "NY-landmark-selection-setup.csv"
    )

    setups = {
        (r["method"], int(r["landmarks"])):
        float(r["total_setup_ms"])
        for r in placement_setup
    }

    configurations = [
        ("random", 4),
        ("farthest", 4),
        ("random", 8),
        ("farthest", 8)
    ]

    def placement_time(method, count):
        return next(
            r for r in placement_times
            if r["params"]["selectionMethod"] == method
            and int(r["params"]["landmarkCount"]) == count
        )

    labels = [
        f"{k} landmarks\n{'Random' if m == 'random' else 'Farthest-first'}"
        for m, k in configurations
    ]

    palette = [
        COLORS[m]
        for m, _ in configurations
    ]

    bars(
        "experiment-b",
        "10_placement_query_time",
        "ALT: random vs geographical farthest-first",
        labels,
        [
            score(placement_time(m, k))
            for m, k in configurations
        ],
        "Average time (ms per route)",
        [
            error(placement_time(m, k))
            for m, k in configurations
        ],
        palette,
        " ms",
        note="NY: the same 100 routes; random / geographical farthest-first selection, seed 2026.\n" + JMH_NOTE
    )

    bars(
        "experiment-b",
        "11_placement_setup",
        "ALT: total selection + preprocessing",
        labels,
        [
            setups[(m, k)]
            for m, k in configurations
        ],
        "Total setup time (ms)",
        colors=palette,
        units=" ms",
        note="Mean of 5 warmed setup runs per configuration in one process; graph loading excluded.\nRaw trials were not saved for this experiment, so no uncertainty bars are inferred."
    )


    # Figure B12: Estimated ALT break-even

    preprocessing = csv_rows("NY-preprocessing.csv")

    astar_setup = sum(
        float(row["astar_setup_ms"])
        for row in preprocessing
    ) / len(preprocessing)

    alt_setup = sum(
        float(row["landmark_selection_ms"])
        + float(row["alt_preprocessing_ms"])
        for row in preprocessing
    ) / len(preprocessing)

    baseline_setups = {
        "dijkstra": 0.0,
        "bidirectional": 0.0,
        "astar": astar_setup
    }


    fig, ax = new_plot(
        "Estimated cumulative runtime: ALT vs query algorithms",
        "Estimated total time (ms)",
        "Number of point-to-point queries"
    )

    crossovers = {}
    for algorithm in ORDER[:-1]:
        saving_per_query = score(times[algorithm]) - score(times["alt"])
        if saving_per_query > 0:
            crossovers[algorithm] = (alt_setup - baseline_setups[algorithm]) / saving_per_query
    maximum_queries = max(200, math.ceil(max(crossovers.values(), default=0) * 1.25 / 50) * 50)
    queries = list(range(maximum_queries + 1))

    for algorithm in ORDER[:-1]:
        ax.plot(
            queries,
            [baseline_setups[algorithm] + n * score(times[algorithm]) for n in queries],
            label=NAMES[algorithm],
            color=COLORS[algorithm]
        )

    alt = [
        alt_setup + n * score(times["alt"])
        for n in queries
    ]

    ax.plot(
        queries,
        alt,
        label="ALT (4 random landmarks)",
        color=COLORS["alt"]
    )

    for algorithm, crossover in crossovers.items():
        crossing_time = baseline_setups[algorithm] + crossover * score(times[algorithm])
        ax.vlines(crossover, 0, crossing_time, color=COLORS[algorithm], linestyle="--", linewidth=1)
        ax.scatter([crossover], [crossing_time], color=COLORS[algorithm], zorder=4)
        ax.annotate(f"~{math.ceil(crossover)} queries",
                    xy=(crossover, crossing_time), xytext=(8, -40), textcoords="offset points",
                    arrowprops={"arrowstyle": "->", "color": COLORS[algorithm]}, fontsize=9)
    ax.set_xlim(0, maximum_queries)
    ax.set_ylim(bottom=0)
    ax.legend(loc="upper left")

    save(
        fig,
        "experiment-b",
        "12_modelled_alt_break_even",
        f"Model: setup + query count x mean query time. A* setup: {astar_setup:.2f} ms; ALT setup: {alt_setup:.2f} ms.\n"
        "Graph and coordinate-file loading excluded; ALT: 4 random landmarks, seed 2026.\n"
        "Setup means come from separate runs; markers show estimated crossovers, not measured query sequences."
    )

    route_work_reduction(counters)
    placement_tradeoff(placement_times, placement_setup)
    landmark_preprocessing_tradeoff(counts, landmark_times, prep_trials)


def route_work_reduction(counters):
    by_pair = {}
    for row in counters:
        pair = (int(row["source"]), int(row["target"]))
        results = by_pair.setdefault(pair, {})
        if row["algorithm"] in results:
            raise ValueError(f"Duplicate counter result for {pair}: {row['algorithm']}")
        results[row["algorithm"]] = row
    if len(by_pair) != 100 or any(set(results) != set(NAMES[a] for a in ORDER) for results in by_pair.values()):
        raise ValueError("Expected the same 100 route pairs for all four algorithms")

    fig, ax = new_plot("Search-work reduction varies across routes",
                       "Dijkstra settlements / algorithm settlements", "Road distance (km, logarithmic scale)")
    for algorithm, marker in zip(ORDER[1:], ["s", "^", "o"]):
        distances = []
        reductions = []
        for results in by_pair.values():
            reference = results[NAMES["dijkstra"]]
            result = results[NAMES[algorithm]]
            if float(result["distance"]) != float(reference["distance"]):
                raise ValueError("Counter distances disagree for a route")
            distances.append(float(reference["distance"]) / 10000)
            reductions.append(int(reference["settled"]) / int(result["settled"]))
        ax.scatter(distances, reductions, marker=marker, s=25, alpha=0.65,
                   color=COLORS[algorithm], label=NAMES[algorithm])
    ax.axhline(1, color="#555555", linestyle="--", linewidth=1)
    for boundary in [5, 20]:
        ax.axvline(boundary, color="#888888", linestyle=":", linewidth=1)
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_yticks([1, 2, 5, 10, 20, 50, 100])
    ax.yaxis.set_major_formatter(FuncFormatter(lambda value, _: f"{value:g}x"))
    ax.legend(loc="upper left", fontsize=9)
    save(fig, "experiment-b", "13_route_work_reduction",
         "Each dot is one saved route for one algorithm (100 routes each); both axes are logarithmic.\n"
         "Above 1x means fewer settlements than Dijkstra; dotted boundaries: 5 / 20 km. Counts, not timing speedups.")


def placement_tradeoff(placement_times, placement_setup):
    storage = {(row["method"], int(row["landmarks"])): float(row["table_mib"]) for row in placement_setup}
    fig, ax = new_plot("ALT: query time versus landmark-table storage",
                       "Average query time (ms per route)", "Distance-table payload (MiB)")
    for method, marker in [("random", "o"), ("farthest", "s")]:
        records = sorted((r for r in placement_times if r["params"]["selectionMethod"] == method),
                         key=lambda r: int(r["params"]["landmarkCount"]))
        memory = [storage[(method, int(r["params"]["landmarkCount"]))] for r in records]
        ax.errorbar(memory, [score(r) for r in records], yerr=[error(r) for r in records],
                    fmt=marker, capsize=4, markersize=7, color=COLORS[method],
                    label="Random" if method == "random" else "Geographical farthest-first")
        for mib, record in zip(memory, records):
            offset = 14 if method == "random" else -22
            ax.annotate(f"{record['params']['landmarkCount']} landmarks", (mib, score(record)),
                        xytext=(10, offset), textcoords="offset points", fontsize=9)
    ax.set_xlim(12, max(storage.values()) * 1.30)
    ax.set_ylim(bottom=0)
    ax.legend(loc="upper right", fontsize=9)
    save(fig, "experiment-b", "14_landmark_time_storage_tradeoff",
         "NY: the same 100 routes and seed 2026; lower time and lower payload are preferable.\n"
         "Payload excludes graph/object overhead; overlapping CIs do not establish equivalence. " + JMH_NOTE)


def landmark_preprocessing_tradeoff(counts, landmark_times, prep_trials):
    query_means = [score(landmark_times[str(k)]) for k in counts]
    query_errors = [error(landmark_times[str(k)]) for k in counts]
    prep_means = [sum(values) / len(values) for values in prep_trials]
    xs = list(range(len(counts)))

    fig, (query_ax, prep_ax) = plt.subplots(1, 2, figsize=(10, 5.6))
    fig.suptitle("ALT: query time and preprocessing cost", fontsize=12)

    query_ax.bar(xs, query_means, yerr=query_errors, capsize=5,
                 color=COLORS["alt"], width=0.64)
    query_ax.set_title("(a) Query time")
    query_ax.set_ylabel("Average time (ms per route)")
    query_ax.set_ylim(0, max(value + err for value, err in zip(query_means, query_errors)) * 1.20)
    for x, value, err in zip(xs, query_means, query_errors):
        query_ax.annotate(f"{value:.2f} ms", (x, value + err), xytext=(0, 8),
                          textcoords="offset points", ha="center", fontsize=9)

    prep_ax.set_title("(b) Preprocessing time")
    prep_ax.set_ylabel("Time (ms)")
    for x, values in enumerate(prep_trials):
        offsets = [(i - (len(values) - 1) / 2) * 0.045 for i in range(len(values))]
        prep_ax.scatter([x + offset for offset in offsets], values, color=COLORS["alt"],
                        s=35, label="Individual run" if x == 0 else None)
        prep_ax.annotate(f"{prep_means[x]:.2f} ms", (x, max(values)), xytext=(0, 10),
                         textcoords="offset points", ha="center", fontsize=9)
    prep_ax.plot(xs, prep_means, linestyle="none", marker="_", markersize=28,
                 markeredgewidth=2, color="#222222", label="Mean")
    prep_ax.set_ylim(0, max(max(values) for values in prep_trials) * 1.20)
    prep_ax.legend(loc="upper left", fontsize=9)

    for ax in (query_ax, prep_ax):
        ax.set_xticks(xs, [str(k) for k in counts])
        ax.set_xlabel("Number of landmarks")
        ax.set_xlim(-0.5, len(counts) - 0.5)
        ax.grid(axis="y", alpha=0.22)
        ax.set_axisbelow(True)

    save(
        fig,
        "experiment-b",
        "15_landmark_query_preprocessing",
        "NY: the same 100 routes; random landmarks, seed 2026; query times exclude preprocessing.\n"
        "Query error bars: JMH 99.9% confidence intervals (3 forks, 10 measurements per fork).\n"
        "Preprocessing: all 5 warmed runs per count and their mean; dots are not confidence intervals."
    )


# both experiments
if __name__ == "__main__":

    experiment_a()
    experiment_b()

    print(f"Created 18 figures in {FIGURES}")
    print("Each figure is saved as PDF.")

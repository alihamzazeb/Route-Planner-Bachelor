
import csv
import json
from pathlib import Path

import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parent.parent
RESULTS = ROOT / "benchmark-data" / "results"
FIGURES = ROOT / "report-figures"

COLORS = {
    "dijkstra": "tab:blue",
    "bidirectional": "tab:orange",
    "astar": "tab:green",
    "alt": "tab:red",
    "duan": "tab:orange",
    "random": "tab:blue",
    "farthest": "tab:green"
}

NAMES = {
    "dijkstra": "Dijkstra",
    "bidirectional": "Bidirectional Dijkstra",
    "astar": "A*",
    "alt": "ALT",
    "duan": "Duan"
}

ORDER = ["dijkstra", "bidirectional", "astar", "alt"]

plt.rcParams.update({
    "font.size": 10,
    "axes.titlesize": 12,
    "axes.labelsize": 10,
    "savefig.dpi": 220
})


# Shared functions

def jmh(filename):
    with (RESULTS / filename).open(encoding="utf-8") as f:
        return json.load(f)


def csv_rows(filename):
    with (RESULTS / filename).open(
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


def save(fig, experiment, filename):
    folder = FIGURES / experiment
    folder.mkdir(parents=True, exist_ok=True)

    for ext in ("pdf", "png"):
        fig.savefig(
            folder / (filename + "." + ext),
            bbox_inches="tight"
        )

    plt.close(fig)


def new_plot(title, ylabel, xlabel=None):
    fig, ax = plt.subplots(figsize=(8, 4.8))

    ax.set_title(title, pad=14)
    ax.set_ylabel(ylabel)

    if xlabel:
        ax.set_xlabel(xlabel)

    ax.grid(axis="y", alpha=0.22)
    ax.set_axisbelow(True)

    return fig, ax


def bars(experiment, filename, title, labels,
         values, ylabel, errors=None, colors=None, units=""):

    fig, ax = new_plot(title, ylabel)

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

    ax.set_xticks(xs, labels)

    ax.set_ylim(
        0,
        max(
            v + (errors[i] if errors else 0)
            for i, v in enumerate(values)
        ) * 1.2
    )

    for x, value in zip(xs, values):
        ax.annotate(
            f"{value:,.2f}{units}",
            (x, value),
            xytext=(0, 8),
            textcoords="offset points",
            ha="center",
            fontsize=9
        )

    fig.tight_layout()
    save(fig, experiment, filename)


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
        " ms"
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
        "Single-source heap allocation",
        labels,
        memory,
        "Allocated MB per source",
        colors=colors,
        units=" MB"
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
        label="Dijkstra",
        color=COLORS["dijkstra"]
    )

    ax.plot(
        queries,
        duan,
        label="Prepared Duan",
        color=COLORS["duan"]
    )

    ax.legend()
    fig.tight_layout()

    save(
        fig,
        "experiment-a",
        "03_modelled_total_time"
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
        " ms"
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
            colors=[COLORS[a] for a in ORDER]
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
        "Average time (ms per route)",
        "Route category"
    )

    for a in ORDER:

        values = [
            score(
                next(
                    r for r in length_data
                    if r["params"]["algorithmName"] == a
                    and r["benchmark"].endswith("." + suffix)
                )
            )
            for suffix in suffixes
        ]

        ax.plot(
            range(3),
            values,
            marker="o",
            linewidth=2,
            label=NAMES[a],
            color=COLORS[a]
        )

    ax.set_xticks(
        range(3),
        [
            "Short (<5 km)",
            "Medium (5–20 km)",
            "Long (≥20 km)"
        ]
    )

    ax.set_yscale("log")
    ax.legend()

    fig.tight_layout()

    save(
        fig,
        "experiment-b",
        "04_route_distance_logscale"
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
        colors=[COLORS[a] for a in ORDER],
        units=" MB"
    )

    # Figure B6: Random landmark count vs query time
    landmark_times = by_param(
        jmh("NY-landmarks-time.json"),
        "landmarkCount"
    )

    counts = [1, 2, 4, 8]

    bars(
        "experiment-b",
        "06_random_landmarks_query_time",
        "ALT: number of random landmarks",
        [str(k) for k in counts],
        [
            score(landmark_times[str(k)])
            for k in counts
        ],
        "Average time (ms per route)",
        [
            error(landmark_times[str(k)])
            for k in counts
        ],
        units=" ms"
    )

    # Figure B7: Landmark preprocessing
    preprocessing = csv_rows(
        "NY-landmarks-preprocessing.csv"
    )

    prep_means = []

    for k in counts:
        values = [
            float(r["preprocessing_ms"])
            for r in preprocessing
            if int(r["landmarks"]) == k
        ]

        prep_means.append(
            sum(values) / len(values)
        )

    bars(
        "experiment-b",
        "07_random_landmarks_preprocessing",
        "ALT: landmark preprocessing cost",
        [str(k) for k in counts],
        prep_means,
        "Preprocessing time (ms)",
        units=" ms"
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
        "Average settled nodes per route"
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
        units=" MiB"
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
        f"{k} {m}"
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
        " ms"
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
        units=" ms"
    )

    # Figure B12: Estimated ALT break-even
    original_prep = csv_rows("NY-preprocessing.csv")

    alt_setup = sum(
        float(r["landmark_selection_ms"])
        + float(r["alt_preprocessing_ms"])
        for r in original_prep
    ) / len(original_prep)

    fig, ax = new_plot(
        "Estimated cumulative runtime: ALT vs Dijkstra",
        "Estimated total time (ms)",
        "Number of point-to-point queries"
    )

    queries = list(range(0, 201))

    dijkstra = [
        n * score(times["dijkstra"])
        for n in queries
    ]

    alt = [
        alt_setup + n * score(times["alt"])
        for n in queries
    ]

    ax.plot(
        queries,
        dijkstra,
        label="Dijkstra",
        color=COLORS["dijkstra"]
    )

    ax.plot(
        queries,
        alt,
        label="ALT (4 random landmarks)",
        color=COLORS["alt"]
    )

    ax.legend()
    fig.tight_layout()

    save(
        fig,
        "experiment-b",
        "12_modelled_alt_break_even"
    )


# both experiments
if __name__ == "__main__":

    experiment_a()
    experiment_b()

    print(f"Created 15 figures in {FIGURES}")
    print("Each figure is saved as PDF and PNG.")

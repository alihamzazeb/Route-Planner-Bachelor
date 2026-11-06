package dk.dtu.routeplanner.core.algorithms;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static dk.dtu.routeplanner.core.algorithms.PathAssertions.assertValidPath;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

class PointToPointAlgorithmsTest {

    @Test
    void handlesParallelEdgesZeroCyclesEqualRoutesAndDisconnectedComponents() {
        Graph graph = new CsrGraph(new int[]{0, 4, 6, 9, 10, 10, 10, 11, 11},
                new int[]{1, 1, 2, 4, 2, 3, 1, 3, 0, 4, 7},
                new double[]{9, 0, 0, 10, 0, 2, 0, 2, 0, 1, 1});
        assertAllAlgorithms(graph, new Random(1));
    }

    @Test
    void seededDirectedGraphsAgreeWithDijkstraSingleSourceDistances() {
        Random random = new Random(2026);
        for (int trial = 0; trial < 25; trial++) {
            int n = 3 + random.nextInt(7);
            int[] offsets = new int[n + 1];
            int[] targets = new int[n * n];
            double[] weights = new double[targets.length];
            int edges = 0;
            for (int from = 0; from < n; from++) {
                for (int to = 0; to < n; to++) {
                    if (random.nextDouble() < 0.3) {
                        targets[edges] = to;
                        weights[edges++] = random.nextInt(11);
                    }
                }
                offsets[from + 1] = edges;
            }
            Graph graph = new CsrGraph(offsets, Arrays.copyOf(targets, edges), Arrays.copyOf(weights, edges));
            assertAllAlgorithms(graph, random);
        }
    }

    @Test
    void searchStopsWhenTheTargetIsSettled() {
        Graph chain = new CsrGraph(new int[]{0, 1, 2, 3, 3}, new int[]{1, 2, 3}, new double[]{1, 1, 1});
        for (PointToPointAlgorithm algorithm : List.of(new Dijkstra(), new AStar((node, target) -> 0))) {
            SearchResult result = algorithm.findShortestPath(chain, 0, 1);
            assertEquals(1, result.distance());
            assertEquals(2, result.settledCounter());
            assertEquals(1, result.relaxedCounter());
        }
    }

    @Test
    void staleQueueEntriesDoNotCountAsExpansions() {
        Graph graph = new CsrGraph(new int[]{0, 2, 3, 4, 4, 4},
                new int[]{1, 2, 3, 1}, new double[]{20, 1, 1, 1});
        for (PointToPointAlgorithm algorithm : List.of(new Dijkstra(), new AStar((node, target) -> 0))) {
            SearchResult result = algorithm.findShortestPath(graph, 0, 4);
            assertEquals(Double.POSITIVE_INFINITY, result.distance());
            assertEquals(0, result.path().length);
            assertEquals(4, result.settledCounter());
            assertEquals(4, result.relaxedCounter());
        }
        SingleSourceResult all = new Dijkstra().computeShortestPaths(graph, 0);
        assertEquals(3, all.distance(3));
        assertEquals(4, all.settledCounter());
        assertEquals(4, all.relaxedCounter());
    }


    private void assertAllAlgorithms(Graph graph, Random random) {
        int n = graph.nodeCount();
        SingleSourceResult[] expected = new SingleSourceResult[n];
        for (int source = 0; source < n; source++) {
            expected[source] = new Dijkstra().computeShortestPaths(graph, source);
            assertMatchesBellmanFord(graph, source, expected[source]);
        }
        RoadNetwork network = new RoadNetwork(graph, reverse(graph));
        int[] longitude = new int[n];
        int[] latitude = new int[n];
        for (int node = 0; node < n; node++) {
            longitude[node] = -74000000 + random.nextInt(10000);
            latitude[node] = 40000000 + random.nextInt(10000);
        }
        StraightLine line = new StraightLine(new Coordinates(longitude, latitude));
        LandmarkPreprocessor preprocessor = new LandmarkPreprocessor(network);
        double c = line.smallestLimit(graph);
        List<Heuristic> heuristics = List.of(
                (node, target) -> c * line.distance(node, target),
                new LandmarkHeuristic(preprocessor.preprocess(new int[0])),
                new LandmarkHeuristic(preprocessor.preprocess(new int[]{0, n / 2, n - 1})));
        assertHeuristicAssumptions(graph, expected, heuristics);
        List<PointToPointAlgorithm> algorithms = List.of(
                new Dijkstra(), new BidirectionalDijkstra(network),
                new AStar((node, target) -> 0),
                new AStar(heuristics.get(0)),
                new AStar(heuristics.get(1)),
                new AStar(heuristics.get(2)));

        for (int source = 0; source < n; source++) {
            for (int target = 0; target < n; target++) {
                String pair = source + " -> " + target;
                for (int variant = 0; variant < algorithms.size(); variant++) {
                    SearchResult actual = algorithms.get(variant).findShortestPath(graph, source, target);
                    assertEquals(expected[source].distance(target), actual.distance(), pair + ", variant=" + variant);
                    assertValidPath(graph, source, target, actual);
                }
            }
        }
    }

    // The other algorithms are compared with Dijkstra, so Dijkstra itself is checked without using it.
    private static void assertMatchesBellmanFord(Graph graph, int source, SingleSourceResult actual) {
        int n = graph.nodeCount();
        double[] distances = new double[n];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        distances[source] = 0;
        for (int pass = 1; pass < n; pass++) {
            for (int node = 0; node < n; node++) {
                for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                    int next = graph.edgeTarget(edge);
                    distances[next] = Math.min(distances[next], distances[node] + graph.edgeWeight(edge));
                }
            }
        }
        for (int node = 0; node < n; node++) {
            assertEquals(distances[node], actual.distance(node), source + " -> " + node);
        }
    }

    private static void assertHeuristicAssumptions(Graph graph, SingleSourceResult[] distances, List<Heuristic> heuristics) {
        for (Heuristic heuristic : heuristics) {
            for (int target = 0; target < graph.nodeCount(); target++) {
                assertEquals(0, heuristic.estimate(target, target));
                for (int node = 0; node < graph.nodeCount(); node++) {
                    if (!Double.isFinite(distances[node].distance(target))) continue;
                    double estimate = heuristic.estimate(node, target);
                    assertTrue(estimate >= 0 && estimate <= distances[node].distance(target) + 1e-9);
                    for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                        int next = graph.edgeTarget(edge);
                        // A dead end cannot be part of a route to this target.
                        if (!Double.isFinite(distances[next].distance(target))) continue;
                        assertTrue(estimate <= graph.edgeWeight(edge) + heuristic.estimate(next, target) + 1e-9,
                                "Inconsistent heuristic on " + node + " -> " + next + ", target=" + target);
                    }
                }
            }
        }
    }

    private static Graph reverse(Graph graph) {
        int n = graph.nodeCount();
        int[] offsets = new int[n + 1];
        for (int node = 0; node < n; node++) {
            for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                offsets[graph.edgeTarget(edge) + 1]++;
            }
        }
        for (int node = 0; node < n; node++) offsets[node + 1] += offsets[node];
        int[] position = offsets.clone();
        int[] targets = new int[offsets[n]];
        double[] weights = new double[targets.length];
        for (int node = 0; node < n; node++) {
            for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                int slot = position[graph.edgeTarget(edge)]++;
                targets[slot] = node;
                weights[slot] = graph.edgeWeight(edge);
            }
        }
        return new CsrGraph(offsets, targets, weights);
    }
}

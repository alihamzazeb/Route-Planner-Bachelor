package dk.dtu.routeplanner.core.algorithms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import static dk.dtu.routeplanner.core.algorithms.PathAssertions.assertValidPath;
import dk.dtu.routeplanner.core.algorithms.alt.GeographicFarthestLandmarkSelector;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkHeuristic;
import dk.dtu.routeplanner.core.algorithms.alt.LandmarkPreprocessor;
import dk.dtu.routeplanner.core.algorithms.alt.RandomLandmarkSelector;
import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.loader.DimacsLoader;
import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;
import dk.dtu.routeplanner.core.model.SearchResult;

class ALTTest {
    private RoadNetwork smallNetwork() {
        Graph forward = new CsrGraph(
                new int[]{0, 2, 4, 5, 6, 7, 7},
                new int[]{1, 2, 2, 3, 3, 4, 1},
                new double[]{2, 10, 2, 5, 1, 3, 1}
        );

        Graph reverse = new CsrGraph(
                new int[]{0, 0, 2, 4, 6, 7, 7},
                new int[]{0, 4, 0, 1, 1, 2, 3},
                new double[]{2, 1, 10, 2, 5, 1, 3}
        );

        return new RoadNetwork(forward, reverse);
    }

    private Graph fiveNodes() {
        return new CsrGraph(new int[]{0, 0, 0, 0, 0, 0}, new int[]{}, new double[]{});
    }

    private StraightLine nodesOnALine() {
        int[] longitudes = {-73990000, -73989000, -73988000, -73987000, -73980000};
        int[] latitudes = {40750000, 40750000, 40750000, 40750000, 40750000};
        return new StraightLine(new Coordinates(longitudes, latitudes));
    }

    @Test
    void randomSelectionUsesValidDistinctNodes() {
        Graph graph = smallNetwork().forward();
        RandomLandmarkSelector selector = new RandomLandmarkSelector();

        int[] first = selector.select(graph, 4, 42L);
        int[] second = selector.select(graph, 4, 42L);

        assertArrayEquals(first, second);
        assertEquals(4, Arrays.stream(first).distinct().count());

        for (int landmark : first) {
            assertTrue(landmark >= 0 && landmark < graph.nodeCount());
        }
    }

    @Test
    void randomSelectionRejectsInvalidCounts() {
        Graph graph = smallNetwork().forward();
        RandomLandmarkSelector selector = new RandomLandmarkSelector();

        assertThrows(IllegalArgumentException.class,
                () -> selector.select(graph, -1, 42L));

        assertThrows(IllegalArgumentException.class,
                () -> selector.select(graph, 7, 42L));

        assertEquals(0, selector.select(graph, 0, 42L).length);
    }

    @Test
    void geographicSelectionPicksFarthestNodesWithoutRepeatingAnyNode() {
        GeographicFarthestLandmarkSelector selector = new GeographicFarthestLandmarkSelector();
        int[] firstThree = selector.select(fiveNodes(), nodesOnALine(), 3, 42L);
        assertArrayEquals(new int[]{0, 4, 3}, firstThree);

        int[] landmarks = selector.select(fiveNodes(), nodesOnALine(), 5, 42L);
        assertArrayEquals(firstThree, Arrays.copyOf(landmarks, 3));
        Arrays.sort(landmarks);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, landmarks);
    }

    @Test
    void geographicSelectionStartsLikeRandomSelection() {
        GeographicFarthestLandmarkSelector geographic = new GeographicFarthestLandmarkSelector();
        RandomLandmarkSelector random = new RandomLandmarkSelector();

        for (long seed = 0; seed < 10; seed++) {
            assertEquals(random.select(fiveNodes(), 1, seed)[0],
                    geographic.select(fiveNodes(), nodesOnALine(), 1, seed)[0]);
        }
    }

    @Test
    void geographicSelectionRejectsInvalidCounts() {
        GeographicFarthestLandmarkSelector selector = new GeographicFarthestLandmarkSelector();

        assertThrows(IllegalArgumentException.class,
                () -> selector.select(fiveNodes(), nodesOnALine(), 0, 42L));

        assertThrows(IllegalArgumentException.class,
                () -> selector.select(fiveNodes(), nodesOnALine(), 6, 42L));
    }

    @Test
    void preprocessingStoresDistancesInBothDirections() {
        LandmarkPreprocessingResult data =
                new LandmarkPreprocessor(smallNetwork())
                        .preprocess(new int[]{1});

        assertEquals(1, data.landmarkCount());
        assertEquals(1, data.landmark(0));

        assertEquals(6.0, data.landmarkFromDistance(0, 4), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY,
                data.landmarkFromDistance(0, 0));

        assertEquals(2.0, data.landmarkToDistance(0, 0), 1e-9);

        assertEquals(1.0, data.landmarkToDistance(0, 4), 1e-9);

        assertEquals(5.0, data.landmarkToDistance(0, 2), 1e-9);

        assertEquals(Double.POSITIVE_INFINITY,
                data.landmarkToDistance(0, 5));
    }

    @Test
    void heuristicUsesTheDirectedLowerBounds() {
        LandmarkPreprocessingResult data =
                new LandmarkPreprocessor(smallNetwork())
                        .preprocess(new int[]{1});

        LandmarkHeuristic heuristic = new LandmarkHeuristic(data);

        assertEquals(6.0, heuristic.estimate(1, 4), 1e-9);
        assertEquals(4.0, heuristic.estimate(2, 4), 1e-9);

        assertEquals(1.0, heuristic.estimate(0, 4), 1e-9);

        assertEquals(0.0, heuristic.estimate(4, 4), 1e-9);
        assertEquals(0.0, heuristic.estimate(5, 4), 1e-9);
    }

    @Test
    void heuristicUsesTheBestBoundOfAllLandmarks() {
        for (int[] landmarks : new int[][]{{1, 3}, {3, 1}}) {
            LandmarkHeuristic heuristic = new LandmarkHeuristic(
                    new LandmarkPreprocessor(smallNetwork()).preprocess(landmarks));

            assertEquals(6.0, heuristic.estimate(1, 4), 1e-9, Arrays.toString(landmarks));
        }
    }

    @Test
    void heuristicDoesNotOverestimate() {
        RoadNetwork network = smallNetwork();

        LandmarkPreprocessingResult data =
                new LandmarkPreprocessor(network)
                        .preprocess(new int[]{0, 1, 3});

        LandmarkHeuristic heuristic = new LandmarkHeuristic(data);
        Dijkstra dijkstra = new Dijkstra();

        for (int source = 0; source < network.forward().nodeCount(); source++) {
            for (int target = 0; target < network.forward().nodeCount(); target++) {

                double h = heuristic.estimate(source, target);

                double distance = dijkstra.findShortestPath(
                        network.forward(), source, target
                ).distance();

                assertTrue(Double.isFinite(h) && h >= 0,
                        "invalid heuristic for " + source + " -> " + target);

                if (Double.isFinite(distance)) {
                    assertTrue(h <= distance + 1e-9,
                            "overestimate for " + source + " -> " + target
                                    + ": h=" + h + ", d=" + distance);
                }
            }
        }
    }

    @Test
    void skipsInfiniteLandmarkTermsOnADirectedDeadEnd() {
        // 2 cannot reach landmark 3. Dropping that bound makes h inconsistent
        // across the zero-cost edge 0 -> 2, while the route to 1 remains valid.
        Graph forward = new CsrGraph(new int[]{0, 2, 3, 3, 3},
                new int[]{1, 2, 3}, new double[]{1, 0, 10});
        Graph reverse = new CsrGraph(new int[]{0, 0, 1, 2, 3},
                new int[]{0, 0, 1}, new double[]{1, 0, 10});
        LandmarkHeuristic heuristic = new LandmarkHeuristic(
                new LandmarkPreprocessor(new RoadNetwork(forward, reverse)).preprocess(new int[]{3}));

        assertEquals(1, heuristic.estimate(0, 1));
        assertEquals(0, heuristic.estimate(2, 1));
        SearchResult result = new AStar(heuristic).findShortestPath(forward, 0, 1);
        assertEquals(1, result.distance());
        assertValidPath(forward, 0, 1, result);
    }

    @Test
    void altAgreesWithDijkstraOnEveryPair() {
        RoadNetwork network = smallNetwork();
        Graph graph = network.forward();

        int[] landmarks = new RandomLandmarkSelector()
                .select(graph, 3, 42L);

        LandmarkPreprocessingResult data =
                new LandmarkPreprocessor(network).preprocess(landmarks);

        AStar alt = new AStar(new LandmarkHeuristic(data));
        Dijkstra dijkstra = new Dijkstra();

        for (int source = 0; source < graph.nodeCount(); source++) {
            for (int target = 0; target < graph.nodeCount(); target++) {

                SearchResult expected =
                        dijkstra.findShortestPath(graph, source, target);

                SearchResult actual =
                        alt.findShortestPath(graph, source, target);

                String pair = source + " -> " + target;

                assertEquals(expected.distance(), actual.distance(), 1e-9, pair);

                assertValidPath(graph, source, target, actual);
            }
        }
    }

    @Test
    void sourceEqualsTargetAndUnreachableTarget() {
        RoadNetwork network = smallNetwork();

        LandmarkPreprocessingResult data =
                new LandmarkPreprocessor(network)
                        .preprocess(new int[]{1, 5});

        AStar alt = new AStar(new LandmarkHeuristic(data));

        SearchResult same =
                alt.findShortestPath(network.forward(), 2, 2);

        assertEquals(0.0, same.distance(), 1e-9);
        assertArrayEquals(new int[]{2}, same.path());

        SearchResult unreachable =
                alt.findShortestPath(network.forward(), 0, 5);

        assertEquals(Double.POSITIVE_INFINITY, unreachable.distance());
        assertEquals(0, unreachable.path().length);
    }

    @Test
    void altAgreesWithDijkstraOnNewYork() throws IOException {
        assumeTrue(Files.isRegularFile(Path.of("../data/USA-road-d.NY.gr")), "New York graph not available");

        RoadNetwork network = new DimacsLoader().load(
                Path.of("../data/USA-road-d.NY.gr")
        );

        Graph graph = network.forward();

        int[] landmarks = new RandomLandmarkSelector()
                .select(graph, 4, 42L);

        LandmarkPreprocessingResult data =
                new LandmarkPreprocessor(network).preprocess(landmarks);

        AStar alt = new AStar(new LandmarkHeuristic(data));
        Dijkstra dijkstra = new Dijkstra();

        Random random = new Random(1234);

        for (int i = 0; i < 20; i++) {

            int source = random.nextInt(graph.nodeCount());
            int target = random.nextInt(graph.nodeCount());

            SearchResult expected =
                    dijkstra.findShortestPath(graph, source, target);

            SearchResult actual =
                    alt.findShortestPath(graph, source, target);

            assertEquals(expected.distance(), actual.distance(), 1e-9,
                    "from " + source + " to " + target);

            assertValidPath(graph, source, target, actual);
        }
    }


}

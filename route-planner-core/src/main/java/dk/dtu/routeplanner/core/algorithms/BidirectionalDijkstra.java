package dk.dtu.routeplanner.core.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.model.SearchResult;

public class BidirectionalDijkstra implements PointToPointAlgorithm {

    private final Graph forwardGraph;
    private final Graph reverse;

    public BidirectionalDijkstra(RoadNetwork network) {
        this.forwardGraph = network.forward();
        this.reverse = network.reverse();
    }

    private record Entry(int node, double distance) { }

    @Override
    public SearchResult findShortestPath(Graph forward, int source, int target) {
        if (forward != forwardGraph) {
            throw new IllegalArgumentException(
                "Forward graph does not belong to this RoadNetwork"
            );
        }

        int n = forward.nodeCount();
        if (source < 0 || source >= n || target < 0 || target >= n) {
            throw new IllegalArgumentException("Invalid source or target node");
        }
        if (source == target) {
            return new SearchResult(0, new int[]{source}, 1, 0); // we already know the answer so we just give it right away
        }

        double[] forwardDist = new double[n];
        double[] backwardDist = new double[n];
        // change to infinity because javas default is 0 and zero in our graph actually means a node
        Arrays.fill(forwardDist, Double.POSITIVE_INFINITY); 
        Arrays.fill(backwardDist, Double.POSITIVE_INFINITY);
        forwardDist[source] = 0;
        backwardDist[target] = 0;


        int[] forwardPrev = new int[n]; 
        int[] backwardNext = new int[n];

        // change to -1 because javas default is 0 and zero in our graph actually means a node. so default it would mean that we can reach all node from 0
        Arrays.fill(forwardPrev, -1);
        Arrays.fill(backwardNext, -1);

        boolean[] forwardSettled = new boolean[n];
        boolean[] backwardSettled = new boolean[n];

        PriorityQueue<Entry> forwardQueue = new PriorityQueue<>(Comparator.comparingDouble(Entry::distance));
        PriorityQueue<Entry> backwardQueue = new PriorityQueue<>(Comparator.comparingDouble(Entry::distance));

        forwardQueue.add(new Entry(source, 0));
        backwardQueue.add(new Entry(target, 0));

        double bestDistance = Double.POSITIVE_INFINITY;
        int meetingNode = -1;
        int settledCounter = 0;
        int relaxedCounter = 0;
        boolean forwardTurn = true;

        while (true) {
            // A node may have several queue entries; skip ones already settled.
            skipSettled(forwardQueue, forwardSettled);
            skipSettled(backwardQueue, backwardSettled);

            if (forwardQueue.isEmpty() || backwardQueue.isEmpty()) {
                break;
            }

            // This bound is essential. Stopping at the first meeting is wrong.
            double forwardMinimum = forwardQueue.peek().distance();
            double backwardMinimum = backwardQueue.peek().distance();
            if (forwardMinimum + backwardMinimum >= bestDistance) {
                break;
            }

            // Take strict turns, one step forward, then one step backward.
            if (forwardTurn) {
                int u = forwardQueue.poll().node();
                forwardSettled[u] = true;
                settledCounter++;

                for (int i = forward.edgeStart(u); i < forward.edgeEnd(u); i++) {
                    relaxedCounter++;
                    int v = forward.edgeTarget(i);
                    double weight = forward.edgeWeight(i);
                    double distance = forwardDist[u] + weight;
                    if (distance < forwardDist[v]) {
                        forwardDist[v] = distance; 
                        forwardPrev[v] = u; // which node discovered v
                        forwardQueue.add(new Entry(v, distance));
                    }

                    // Two tentative paths already form a complete source-target route.
                    if (forwardDist[v] < Double.POSITIVE_INFINITY && backwardDist[v] < Double.POSITIVE_INFINITY) {
                        double route = forwardDist[v] + backwardDist[v];
                        if (route < bestDistance) {
                            bestDistance = route;
                            meetingNode = v;
                        }
                    }
                }
            } else {
                int u = backwardQueue.poll().node();
                backwardSettled[u] = true;
                settledCounter++;

                // A reverse edge u -> v represents an original edge v -> u.
                for (int i = reverse.edgeStart(u); i < reverse.edgeEnd(u); i++) {
                    relaxedCounter++;
                    int v = reverse.edgeTarget(i);
                    double weight = reverse.edgeWeight(i);
                    double distance = backwardDist[u] + weight;
                    if (distance < backwardDist[v]) {
                        backwardDist[v] = distance;
                        backwardNext[v] = u;
                        backwardQueue.add(new Entry(v, distance));
                    }

                    if (forwardDist[v] < Double.POSITIVE_INFINITY
                            && backwardDist[v] < Double.POSITIVE_INFINITY) {
                        double route = forwardDist[v] + backwardDist[v];
                        if (route < bestDistance) {
                            bestDistance = route;
                            meetingNode = v;
                        }
                    }
                }
            }

            forwardTurn = !forwardTurn;
        }

        if (meetingNode == -1) {
            return new SearchResult(Double.POSITIVE_INFINITY, new int[0], settledCounter, relaxedCounter);
        }

        ArrayList<Integer> path = new ArrayList<>();
        for (int v = meetingNode; v != -1; v = forwardPrev[v]) {
            path.add(v);
        }
        Collections.reverse(path);
        for (int v = backwardNext[meetingNode]; v != -1; v = backwardNext[v]) {
            path.add(v);
        }

        return new SearchResult(bestDistance, path.stream().mapToInt(Integer::intValue).toArray(),
                settledCounter, relaxedCounter);
    }

    private static void skipSettled(PriorityQueue<Entry> queue, boolean[] settled) {
        while (!queue.isEmpty() && settled[queue.peek().node()]) {
            queue.poll();
        }
    }
}
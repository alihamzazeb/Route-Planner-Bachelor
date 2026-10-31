
package dk.dtu.routeplanner.core.algorithms.alt;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.StraightLine;

public final class GeographicFarthestLandmarkSelector {

    public int[] select(Graph graph, StraightLine straightLine,
                        int count, long seed) {

        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(straightLine, "straightLine");

        int nodeCount = graph.nodeCount();

        if (count < 1 || count > nodeCount) {
            throw new IllegalArgumentException(
                    "Invalid landmark count: " + count
            );
        }

        int[] landmarks = new int[count];
        boolean[] selected = new boolean[nodeCount];

        double[] nearestDistance = new double[nodeCount];
        Arrays.fill(nearestDistance, Double.POSITIVE_INFINITY);

        // Start with the same node as the random selector.
        int current = new Random(seed).nextInt(nodeCount);

        for (int i = 0; i < count; i++) {

            landmarks[i] = current;
            selected[current] = true;

            if (i == count - 1) {
                break;
            }

            int next = -1;
            double largestNearestDistance = -1;

            for (int node = 0; node < nodeCount; node++) {

                if (selected[node]) {
                    continue;
                }

                double distance =
                        straightLine.distance(current, node);

                nearestDistance[node] = Math.min(
                        nearestDistance[node], distance
                );

                if (nearestDistance[node] > largestNearestDistance) {
                    largestNearestDistance = nearestDistance[node];
                    next = node;
                }
            }

            current = next;
        }

        return landmarks;
    }
}

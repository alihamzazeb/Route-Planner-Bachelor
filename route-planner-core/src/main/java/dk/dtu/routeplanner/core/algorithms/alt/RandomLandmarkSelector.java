package dk.dtu.routeplanner.core.algorithms.alt;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import dk.dtu.routeplanner.core.graph.Graph;

public final class RandomLandmarkSelector {

    public int[] select(Graph graph, int count, long seed) {

        Objects.requireNonNull(graph, "graph");

        int nodeCount = graph.nodeCount();

        if (count < 0 || count > nodeCount) {
            throw new IllegalArgumentException("Landmark count must be between 0 and " + nodeCount);
        }

        int[] landmarks = new int[count];
        Set<Integer> chosen = new HashSet<>();
        Random random = new Random(seed); 

        int index = 0;

        while (index < count) {

            int candidate = random.nextInt(nodeCount);

            if (chosen.add(candidate)) {
                landmarks[index] = candidate;
                index++;
            }
        }

        return landmarks;
    }
}

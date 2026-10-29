package dk.dtu.routeplanner.core.algorithms.alt;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import dk.dtu.routeplanner.core.graph.Graph;

public final class RandomLandmarkSelector {
    // returning random landmarks using seed

    public int[] select(Graph graph, int count, long seed) {

        Objects.requireNonNull(graph, "graph"); // just a check to see if we have a graph

        int nodeCount = graph.nodeCount();

        // make sure valid number of landmark is given
        if (count < 0 || count > nodeCount) {
            throw new IllegalArgumentException("Landmark count must be between 0 and " + nodeCount);
        }

        int[] landmarks = new int[count]; // array wit total number of landmarks
        Set<Integer> chosen = new HashSet<>(); // to make sure that the random number is not repeated. hashset is quick (ALI skriv om det i rapporten)
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
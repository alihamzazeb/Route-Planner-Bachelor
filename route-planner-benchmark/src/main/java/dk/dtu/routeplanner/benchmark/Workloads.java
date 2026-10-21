package dk.dtu.routeplanner.benchmark;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Workloads {

    public static int[][] generatePairs(int nodeCount, int count, long seed) {
        if (count < 0){
            throw new IllegalArgumentException("count needs to be positive");
        }
        if (nodeCount < 2){
            throw new IllegalArgumentException("Needs atleast 2 nodes");
        }

        long possiblePairs = (long) nodeCount * (nodeCount - 1);

        if (count > possiblePairs) {
            throw new IllegalArgumentException("Too many unique pairs requested");
        }

        Random random = new Random(seed);
        
        int[][] pairs = new int[count][2];

        Set<String> seen = new HashSet<>();
        int accepted = 0;

        while (accepted < count) {

            int source = random.nextInt(nodeCount);
            int target = random.nextInt(nodeCount);

            while (source == target){
                target = random.nextInt(nodeCount);
            }
            String key = source + ":" + target;


            if (seen.add(key)) {
                pairs[accepted][0] = source;
                pairs[accepted][1] = target;
                accepted++;
            }

        }
        return pairs;

    }
}
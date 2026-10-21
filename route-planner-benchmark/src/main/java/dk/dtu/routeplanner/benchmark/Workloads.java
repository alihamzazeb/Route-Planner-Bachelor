package dk.dtu.routeplanner.benchmark;

import java.util.Random;

public class Workloads {

    public static int[][] generatePairs(int nodeCount, int count, long seed) {
        if (count < 0){
            throw new IllegalArgumentException("count needs to be positive");
        }
        if (nodeCount < 2){
            throw new IllegalArgumentException("Needs atleast 2 nodes");
        }

        Random random = new Random(seed);
        
        int[][] pairs = new int[count][2];

        for (int i = 0; i < count; i++) {

            int source = random.nextInt(nodeCount);
            int target = random.nextInt(nodeCount);

            while (source == target){
                target = random.nextInt(nodeCount);
            }

            pairs[i][0]=source;
            pairs[i][1]=target;

        }
        return pairs;

    }
}
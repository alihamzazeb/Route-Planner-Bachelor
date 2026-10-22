package dk.dtu.routeplanner.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;

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



    public static int[][] generateReachablePairs(Graph graph, int count, long seed) {

        if (count <= 0 || count > Integer.MAX_VALUE / 10) {
            throw new IllegalArgumentException("Invalid pair count");
        }

        int[][] candidates = generatePairs(graph.nodeCount(), count * 10, seed);

        int[][] reachablePairs = new int[count][2];

        Dijkstra dijkstra = new Dijkstra();

        int accepted = 0;

        for (int[] pair : candidates) {

            int source = pair[0];
            int target = pair[1];

            SearchResult result = dijkstra.findShortestPath(graph, source, target);

            if (Double.isFinite(result.distance())) {

                reachablePairs[accepted] = pair;
                accepted++;

                if (accepted == count) {
                    break;
                }
            }
        }

        if (accepted != count) {
            throw new IllegalStateException("Only found " + accepted + " reachable pairs");
        }

        return reachablePairs;
    }


    public static void savePairs(Path file, int[][] pairs) throws IOException {

        List<String> lines = new ArrayList<>();
        lines.add("source,target");

        for (int[] pair : pairs) {
            lines.add(pair[0] + "," + pair[1]);
        }

        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }


    public static int[][] readPairs(Path file, int nodeCount, int expectedCount)throws IOException {
        List<String> lines =
                Files.readAllLines(file, StandardCharsets.UTF_8);

        if (lines.size() != expectedCount + 1 ||
                !lines.get(0).equals("source,target")) {
            throw new IllegalArgumentException("Invalid workload CSV");
        }

        int[][] pairs = new int[expectedCount][2];
        Set<String> seen = new HashSet<>();

        for (int i = 1; i < lines.size(); i++) {

            String[] values = lines.get(i).split(",", -1);

            if (values.length != 2) {
                throw new IllegalArgumentException("Invalid row: " + i);
            }

            int source = Integer.parseInt(values[0]);
            int target = Integer.parseInt(values[1]);

            if (source < 0 || source >= nodeCount ||
                    target < 0 || target >= nodeCount ||
                    source == target ||
                    !seen.add(source + ":" + target)) {
                throw new IllegalArgumentException("Invalid pair: " + i);
            }

            pairs[i - 1][0] = source;
            pairs[i - 1][1] = target;
        }

        return pairs;
    }



}
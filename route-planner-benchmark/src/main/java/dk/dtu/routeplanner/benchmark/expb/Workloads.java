package dk.dtu.routeplanner.benchmark.expb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Workloads {

    public static void savePairs(Path file, int[][] pairs) throws IOException {

        List<String> lines = new ArrayList<>();
        lines.add("source,target");

        for (int[] pair : pairs) {
            lines.add(pair[0] + "," + pair[1]);
        }

        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    public static int[][] readPairs(Path file, int nodeCount, int expectedCount) throws IOException {

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        if (lines.size() != expectedCount + 1 || !lines.get(0).equals("source,target")) {
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

            if (source < 0 || source >= nodeCount || target < 0 || target >= nodeCount || source == target|| !seen.add(source + ":" + target)) {
                throw new IllegalArgumentException("Invalid pair: " + i);
            }

            pairs[i - 1][0] = source;
            pairs[i - 1][1] = target;
        }

        return pairs;
    }
}
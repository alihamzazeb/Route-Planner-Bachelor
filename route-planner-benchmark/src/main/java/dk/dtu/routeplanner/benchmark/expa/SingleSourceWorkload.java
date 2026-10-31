
package dk.dtu.routeplanner.benchmark.expa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SingleSourceWorkload {

    public static final int SOURCE_COUNT = 10;

    public static final Path NY_SOURCES =
            Path.of("benchmark-data/workloads/NY-10sources-seed42.csv");

    private SingleSourceWorkload() {
    }

    public static int[] read(Path file, int nodeCount) throws IOException {

        List<String> lines = Files.readAllLines(file);

        if (lines.size() != SOURCE_COUNT + 1
                || !"source".equals(lines.get(0))) {
            throw new IllegalArgumentException(
                    "Expected 'source' plus " + SOURCE_COUNT
                            + " rows in " + file
            );
        }

        int[] sources = new int[SOURCE_COUNT];
        Set<Integer> unique = new HashSet<>();

        for (int i = 0; i < SOURCE_COUNT; i++) {

            int source = Integer.parseInt(
                    lines.get(i + 1).trim()
            );

            if (source < 0 || source >= nodeCount
                    || !unique.add(source)) {
                throw new IllegalArgumentException(
                        "Invalid or repeated source on row " + (i + 2)
                );
            }

            sources[i] = source;
        }

        return sources;
    }
}

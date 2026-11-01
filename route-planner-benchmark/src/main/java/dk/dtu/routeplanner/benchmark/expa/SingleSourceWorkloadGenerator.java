
package dk.dtu.routeplanner.benchmark.expa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dk.dtu.routeplanner.core.loader.DimacsLoader;

public final class SingleSourceWorkloadGenerator {
    
    private SingleSourceWorkloadGenerator() {}

    public static void main(String[] args) throws Exception {

        int nodeCount = new DimacsLoader().load(Path.of("data/USA-road-d.NY.gr")).forward().nodeCount();

        Random random = new Random(42L);
        Set<Integer> sources = new LinkedHashSet<>();

        while (sources.size() < SingleSourceWorkload.SOURCE_COUNT) {
            sources.add(random.nextInt(nodeCount));
        }

        List<String> lines = new ArrayList<>();
        lines.add("source");

        for (int source : sources) {
            lines.add(Integer.toString(source));
        }

        Path file = SingleSourceWorkload.NY_SOURCES;

        Files.createDirectories(file.getParent());
        Files.write(file, lines);

        System.out.println("Generated " + sources.size() + " source nodes in " + file);
    }
}

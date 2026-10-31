
package dk.dtu.routeplanner.core.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import dk.dtu.routeplanner.core.graph.Coordinates;
import dk.dtu.routeplanner.core.graph.CsrGraph;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.RoadNetwork;

public class DimacsLoader {

    public RoadNetwork load(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);

        int nodeCount = 0;
        int arcCount = 0;

        for (String line : lines) {
            if (line.startsWith("p")) {
                String[] parts = line.split(" ");
                nodeCount = Integer.parseInt(parts[2]);
                arcCount = Integer.parseInt(parts[3]);
                break;
            }
        }

        int[] sources = new int[arcCount];
        int[] targets = new int[arcCount];
        double[] weights = new double[arcCount];

        int arcsRead = 0;

        for (String line : lines) {
            if (line.startsWith("a")) {
                String[] parts = line.split(" ");

                sources[arcsRead] = Integer.parseInt(parts[1]) - 1;
                targets[arcsRead] = Integer.parseInt(parts[2]) - 1;
                weights[arcsRead] = Double.parseDouble(parts[3]);

                arcsRead++;
            }
        }

        Graph forward = buildCsr(nodeCount, sources, targets, weights);
        Graph reverse = buildCsr(nodeCount, targets, sources, weights);

        return new RoadNetwork(forward, reverse);
    }

    private static Graph buildCsr(
            int nodeCount,
            int[] sources,
            int[] targets,
            double[] weights
    ) {

        int edgeCount = sources.length;
        int[] counts = new int[nodeCount];

        for (int source : sources) {
            counts[source]++;
        }

        int[] offsets = new int[nodeCount + 1];

        for (int i = 1; i <= nodeCount; i++) {
            offsets[i] = offsets[i - 1] + counts[i - 1];
        }

        int[] cursor = Arrays.copyOf(offsets, nodeCount);
        int[] orderedTargets = new int[edgeCount];
        double[] orderedWeights = new double[edgeCount];

        for (int i = 0; i < edgeCount; i++) {
            int slot = cursor[sources[i]]++;

            orderedTargets[slot] = targets[i];
            orderedWeights[slot] = weights[i];
        }

        return new CsrGraph(offsets, orderedTargets, orderedWeights);
    }

    public Coordinates loadCoordinates(Path filePath) throws IOException {

        List<String> lines = Files.readAllLines(filePath);

        int nodeCount = 0;

        for (String line : lines) {
            if (line.startsWith("p")) {
                String[] parts = line.split(" ");
                nodeCount = Integer.parseInt(parts[4]);
                break;
            }
        }

        int[] longitude = new int[nodeCount];
        int[] latitude = new int[nodeCount];

        for (String line : lines) {
            if (line.startsWith("v")) {
                String[] parts = line.split(" ");

                int node = Integer.parseInt(parts[1]) - 1;

                longitude[node] = Integer.parseInt(parts[2]);
                latitude[node] = Integer.parseInt(parts[3]);
            }
        }

        return new Coordinates(longitude, latitude);
    }
}

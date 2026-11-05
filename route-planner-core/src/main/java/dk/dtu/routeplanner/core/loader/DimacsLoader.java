
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

        int nodeCount = -1;
        int arcCount = 0;

        for (String line : lines) {
            line = line.strip();
            if (line.startsWith("p")) {
                String[] parts = line.split("\\s+");
                nodeCount = Integer.parseInt(parts[2]);
                arcCount = Integer.parseInt(parts[3]);
                break;
            }
        }
        if (nodeCount < 0 || arcCount < 0) {
            throw new IOException("Missing graph header or invalid graph size");
        }

        int[] sources = new int[arcCount];
        int[] targets = new int[arcCount];
        double[] weights = new double[arcCount];

        int arcsRead = 0;

        for (String line : lines) {
            line = line.strip();
            if (line.startsWith("a")) {
                if (arcsRead >= arcCount) {
                    throw new IOException("More arcs than declared in the header");
                }
                String[] parts = line.split("\\s+");

                sources[arcsRead] = Integer.parseInt(parts[1]) - 1; // the files start from 1 but arrays start from 0
                targets[arcsRead] = Integer.parseInt(parts[2]) - 1;
                weights[arcsRead] = Double.parseDouble(parts[3]);

                arcsRead++;
            }
        }
        if (arcsRead != arcCount) {
            throw new IOException("Fewer arcs than declared in the header");
        }

        Graph forward = buildCsr(nodeCount, sources, targets, weights);

        // reverse
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

        // we count how many arcs each node has. so we know how many slots we need to allocate for each node in the graph.
        int[] counts = new int[nodeCount];

        for (int source : sources) {
            counts[source]++;
        }

        // we make an offsets array which will tell us where each node starts in the graph.
        // we want to put all of node 0's edges together in the array, and then node 1, then node 2.....
        int[] offsets = new int[nodeCount + 1];

        for (int i = 1; i <= nodeCount; i++) {
            offsets[i] = offsets[i - 1] + counts[i - 1];
        }

        // the edges for the nodes are scattered and not sorted. so we need to know what slot has been used for each of the nodes.
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

        int nodeCount = -1;

        for (String line : lines) {
            line = line.strip();
            if (line.startsWith("p")) {
                String[] parts = line.split("\\s+");
                nodeCount = Integer.parseInt(parts[4]);
                break;
            }
        }
        if (nodeCount < 0) {
            throw new IOException("Missing coordinate header or invalid node count");
        }

        int[] longitude = new int[nodeCount];
        int[] latitude = new int[nodeCount];
        boolean[] seen = new boolean[nodeCount];
        int nodesRead = 0;

        for (String line : lines) {
            line = line.strip();
            if (line.startsWith("v")) {
                String[] parts = line.split("\\s+");

                int node = Integer.parseInt(parts[1]) - 1;
                if (node < 0 || node >= nodeCount || seen[node]) {
                    throw new IOException("Invalid or duplicate coordinate node ID");
                }

                longitude[node] = Integer.parseInt(parts[2]);
                latitude[node] = Integer.parseInt(parts[3]);
                seen[node] = true;
                nodesRead++;
            }
        }
        if (nodesRead != nodeCount) {
            throw new IOException("Missing coordinates for one or more nodes");
        }

        return new Coordinates(longitude, latitude);
    }
}

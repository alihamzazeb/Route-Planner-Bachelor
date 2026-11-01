
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
        List<String> lines = Files.readAllLines(filePath); // read all lines from the file

        // the files already has a line where it says the total number of nodes and edges.
        // we just find the line which starst with "p" and then split it so we can save each value in its variable
        int nodeCount = 0;
        int arcCount = 0;

        for (String line : lines) {
            if (line.startsWith("p")) {
                String[] parts = line.split(" ");
                nodeCount = Integer.parseInt(parts[2]);
                arcCount = Integer.parseInt(parts[3]);
                break; // once we have found the "p" then there is no need to go through all lines becuase there is only one "p"
            }
        }

        // we need to store all arcs so we make list for each "a" in the file
        int[] sources = new int[arcCount];
        int[] targets = new int[arcCount];
        double[] weights = new double[arcCount];

        int arcsRead = 0;

        for (String line : lines) {
            if (line.startsWith("a")) {
                String[] parts = line.split(" ");

                sources[arcsRead] = Integer.parseInt(parts[1]) - 1; // the files start from 1 but arrays start from 0
                targets[arcsRead] = Integer.parseInt(parts[2]) - 1;
                weights[arcsRead] = Double.parseDouble(parts[3]);

                arcsRead++;
            }
        }

        Graph forward = buildCsr(nodeCount, sources, targets, weights);

        // reverse
        Graph reverse = buildCsr(nodeCount, targets, sources, weights);

        return new RoadNetwork(forward, reverse); // both graphs are created and they explain the same road network
    }

    private static Graph buildCsr(
            int nodeCount,
            int[] sources,
            int[] targets,
            double[] weights
    ) {

        int edgeCount = sources.length;

        // we count how many arcs each node has. so we know how many slots we need to allocate for each node in the graph.
        // we can do this by iterating through the sources array and incrementing the count for each source node.
        int[] counts = new int[nodeCount]; // stores how many edges each node has

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

        List<String> lines = Files.readAllLines(filePath); // read all lines from the file

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

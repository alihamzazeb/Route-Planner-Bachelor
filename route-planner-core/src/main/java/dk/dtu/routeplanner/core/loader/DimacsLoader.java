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
                String[] pParts = line.split(" ");
                nodeCount = Integer.parseInt(pParts[2]);
                arcCount = Integer.parseInt(pParts[3]);
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
                String[] aParts = line.split(" ");
                sources[arcsRead] = Integer.parseInt(aParts[1]) - 1; // the files start from 1 but arrays start from 0
                targets[arcsRead] = Integer.parseInt(aParts[2]) - 1;
                weights[arcsRead] = Double.parseDouble(aParts[3]);
                arcsRead++;
            }
        }

        // we count how many arcs each node has. so we know how many slots we need to allocate for each node in the forward graph. we can do this by iterating through the sources array and incrementing the count for each source node.
        int[] counts = new int[nodeCount]; // stores how many edges each node has
        for (int i = 0; i < arcCount; i++) {
            counts[sources[i]]++;
        }

        // we make an offsets array which will tell us where each node starts in the forward graph.
        // we want to put all of node 0's edges together in the array, and then node 1, then node 2.....
        int[] offsets = new int[nodeCount + 1]; 
        for (int i = 1; i < offsets.length; i++) {
            offsets[i] = offsets[i - 1] + counts[i - 1];
        }

        // the edges for the nodes are scattered and not sorted. so we need to know what slot has been used for each of the nodes.
        int[] cursor = Arrays.copyOf(offsets, nodeCount);
        int[] fwdTargets = new int[arcCount];
        double[] fwdWeights = new double[arcCount];

        for (int i = 0; i < arcCount; i++) {
            int slot = cursor[sources[i]];
            fwdTargets[slot] = targets[i];
            fwdWeights[slot] = weights[i];
            cursor[sources[i]]++;
        }


        // reverse

        int[] revCounts = new int[nodeCount];
        for (int i = 0; i < arcCount; i++) {
            revCounts[targets[i]]++;
        }

        int[] revOffsets = new int[nodeCount + 1];
        for (int i = 1; i < revOffsets.length; i++) {
            revOffsets[i] = revOffsets[i - 1] + revCounts[i - 1];
        }

        int[] revCursor = Arrays.copyOf(revOffsets, nodeCount);
        int[] revTargets = new int[arcCount];
        double[] revWeights = new double[arcCount];

        for (int i = 0; i < arcCount; i++) {
            int slot = revCursor[targets[i]];
            revTargets[slot] = sources[i];
            revWeights[slot] = weights[i];
            revCursor[targets[i]]++;
        }


        Graph forward = new CsrGraph(offsets, fwdTargets, fwdWeights);
        Graph reverse = new CsrGraph(revOffsets, revTargets, revWeights);
        return new RoadNetwork(forward, reverse); // both graphs are created and they explain the same road network

    }


    public Coordinates loadCoordinates(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath); // read all lines from the file

        int nodeCount = 0;
        for (String line : lines) {
            if (line.startsWith("p")) {
                String[] pParts = line.split(" ");
                nodeCount = Integer.parseInt(pParts[4]);
                break; 
            }
        }

        int[] longitude = new int[nodeCount];
        int[] latitude = new int[nodeCount];

        for (String line : lines) {
            if (line.startsWith("v")) {
                String[] vParts = line.split(" ");
                int node = Integer.parseInt(vParts[1])-1;
                longitude[node] = Integer.parseInt(vParts[2]);
                latitude[node] = Integer.parseInt(vParts[3]);
            }
        }
        return new Coordinates(longitude, latitude);
    }

}
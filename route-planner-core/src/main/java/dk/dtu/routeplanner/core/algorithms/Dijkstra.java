package dk.dtu.routeplanner.core.algorithms;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SingleSourceResult;


public class Dijkstra {

    public SingleSourceResult computeShortestPaths(Graph graph, int source){
        double[] dist = new double[graph.nodeCount()]; 
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[source]=0;
        int settledCounter=0;
        int relaxedCounter=0;

        boolean[] settled = new boolean[graph.nodeCount()];
        
        PriorityQueue<Entry> queue = 
            new PriorityQueue<>(Comparator.comparingDouble(Entry::dist));// we compare the distance when we poll
        
        queue.add(new Entry(source, 0));

        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            int node = entry.node();
            if (settled[node]) {
                continue;
            }
            settled[node]=true;
            settledCounter++;

            for (int i = graph.edgeStart(node); i < graph.edgeEnd(node); i++) {
                int neighbour = graph.edgeTarget(i);
                double weight = graph.edgeWeight(i);

                double newDist=dist[node]+weight;
                
                relaxedCounter++;
                if (dist[neighbour]>newDist) {
                    dist[neighbour] = newDist;

                    queue.add(new Entry(neighbour, dist[neighbour]));
                }
            }
        }
        return new SingleSourceResult(settledCounter, relaxedCounter, dist);
    }

    private record Entry(int node, double dist){

    }

}
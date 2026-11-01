package dk.dtu.routeplanner.core.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.graph.StraightLine;
import dk.dtu.routeplanner.core.model.SearchResult;

public class AStar implements PointToPointAlgorithm{
    private final Heuristic heuristic;

    // guess = c * straight line distance
    public AStar(double c, StraightLine straightLine) {
        this((node, target) -> c * straightLine.distance(node, target));
    }

    public AStar(Heuristic heuristic) {
        this.heuristic = heuristic;
    }

    private double guess(int node, int target) {
        return heuristic.estimate(node, target);
    }

    private record Entry(int node, double priority){

    }


    @Override
    public SearchResult findShortestPath(Graph graph, int source, int target){
            double[] dist = new double[graph.nodeCount()];
            int[] prev = new int[graph.nodeCount()]; 

            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(prev, -1);

            dist[source]=0;
            int settledCounter=0;
            int relaxedCounter=0;

            ArrayList<Integer> path = new ArrayList<>();
            int nodePointer=target;
            boolean[] settled = new boolean[graph.nodeCount()];
            
            PriorityQueue<Entry> queue = 
                new PriorityQueue<>(Comparator.comparingDouble(Entry::priority));
            
            queue.add(new Entry(source, 0+guess(source, target)));

            while (!queue.isEmpty()) {
                Entry entry = queue.poll();
                int node = entry.node();
                if (settled[node]) {
                    continue;
                }
                settled[node]=true;
                settledCounter++;

                if (node == target) {
                    break;
                }

                for (int i = graph.edgeStart(node); i < graph.edgeEnd(node); i++) {
                    int neighbour = graph.edgeTarget(i);
                    double weight = graph.edgeWeight(i);

                    double newDist=dist[node]+weight;
                    
                    relaxedCounter++;
                    if (dist[neighbour]>newDist) {
                        dist[neighbour] = newDist;
                        prev[neighbour] = node;

                        queue.add(new Entry(neighbour, dist[neighbour]+guess(neighbour, target)));
                    }
                }
            }
 
            if (dist[target]==Double.POSITIVE_INFINITY){
                return new SearchResult(Double.POSITIVE_INFINITY, new int[0], settledCounter, relaxedCounter);
            }

            while (!(nodePointer==source)) {
                path.add(nodePointer);

                nodePointer=prev[nodePointer];
            }
            path.add(source);

            Collections.reverse(path);
            int[] pathArray = path.stream().mapToInt(Integer::intValue).toArray();


            return new SearchResult(dist[target], pathArray, settledCounter, relaxedCounter);
        }
    
}

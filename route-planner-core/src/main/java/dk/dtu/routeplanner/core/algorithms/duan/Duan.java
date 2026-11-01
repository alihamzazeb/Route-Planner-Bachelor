package dk.dtu.routeplanner.core.algorithms.duan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

import dk.dtu.routeplanner.core.algorithms.PointToPointAlgorithm;
import dk.dtu.routeplanner.core.algorithms.SingleSourceAlgorithm;
import dk.dtu.routeplanner.core.graph.Graph;
import dk.dtu.routeplanner.core.model.SearchResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

public class Duan implements SingleSourceAlgorithm, PointToPointAlgorithm {

    public enum DegreeMode {
        TRANSFORM,
        BOUNDED
    }

    private final DegreeMode degreeMode;
    private final Graph preparedFor;
    private final DuanGraph preparedGraph;

    public Duan() {
        this(DegreeMode.TRANSFORM);
    }

    public Duan(DegreeMode degreeMode) {
        this.degreeMode = Objects.requireNonNull(degreeMode, "degreeMode");
        this.preparedFor = null;
        this.preparedGraph = null;
    }

    public Duan(Graph graph, DegreeMode degreeMode) {
        this.degreeMode = Objects.requireNonNull(degreeMode, "degreeMode");
        this.preparedFor = Objects.requireNonNull(graph, "graph");
        this.preparedGraph = DuanGraph.prepareAll(graph, degreeMode);
    }

    public record GraphInfo(int originalNodes, int compactEdges, int internalNodes,
                            int internalEdges, int maxDegree, boolean transformed) {}

    public GraphInfo graphInfo() {
        if (preparedGraph == null) {
            throw new IllegalStateException("Construct Duan with a graph to inspect its preparation");
        }
        int nodes = preparedGraph.nodeCount();
        int edges = nodes == 0 ? 0 : preparedGraph.edgeEnd(nodes - 1);
        return new GraphInfo(preparedGraph.originalToInternal.length, preparedGraph.compactEdgeCount(),
                nodes, edges, preparedGraph.maxDegree, preparedGraph.transformed);
    }

    @Override
    public SingleSourceResult computeShortestPaths(Graph graph, int source) {
        return createSearch(graph, source).run();
    }

    @Override
    public SearchResult findShortestPath(Graph graph, int source, int target) {
        Objects.requireNonNull(graph, "graph");
        if (target < 0 || target >= graph.nodeCount()) {
            throw new IllegalArgumentException("Invalid target node: " + target);
        }
        Search search = createSearch(graph, source);
        SingleSourceResult result = search.run();
        return new SearchResult(result.distance(target), search.pathTo(target),
                result.settledCounter(), result.relaxedCounter());
    }

    private Search createSearch(Graph graph, int source) {
        Objects.requireNonNull(graph, "graph");
        if (source < 0 || source >= graph.nodeCount()) {
            throw new IllegalArgumentException("Invalid source node: " + source);
        }
        if (preparedGraph != null) {
            if (graph != preparedFor) {
                throw new IllegalArgumentException("Duan was prepared for a different graph instance");
            }
            DuanGraph.Reachability reachable = preparedGraph.reachableFrom(source);
            return new Search(preparedGraph, preparedGraph.originalToInternal[source],
                    reachable.originalNodes(), reachable.internalNodes());
        }
        DuanGraph prepared = DuanGraph.prepare(graph, source, degreeMode);
        return new Search(prepared, prepared.source, prepared.reachableCount, prepared.nodeCount());
    }

    record Entry(int node, double dist, int hops) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return compare(dist, hops, node, other);
        }

        static int compare(double dist, int hops, int node, Entry other) {
            int comparison = Double.compare(dist, other.dist);
            if (comparison == 0) {
                comparison = Integer.compare(hops, other.hops);
            }
            if (comparison == 0) {
                comparison = Integer.compare(node, other.node);
            }
            return comparison;
        }
    }

    static final Entry INFINITY =
            new Entry(Integer.MAX_VALUE, Double.POSITIVE_INFINITY, Integer.MAX_VALUE);

    private record BoundedResult(Entry bound, List<Integer> completed) {}
    private record PivotResult(List<Integer> pivots, List<Integer> visited) {}

    private static class Search {
        private final DuanGraph graph;
        private final int source;
        private final int reachableOriginalNodes;
        private final int reachableInternalNodes;
        private final double[] dist;
        private final int[] hops;
        private final int[] prev;
        private final int[] prevEdge;
        private final int[] children;
        private final int[] subtreeSize;
        private final int k;
        private final int t;
        private final int levels;
        private final Workspace[] workspaces;
        private long relaxedCounter;

        Search(DuanGraph graph, int source, int reachableOriginalNodes, int reachableInternalNodes) {
            this.graph = graph;
            this.source = source;
            this.reachableOriginalNodes = reachableOriginalNodes;
            this.reachableInternalNodes = reachableInternalNodes;
            int n = graph.nodeCount();
            dist = new double[n];
            hops = new int[n];
            prev = new int[n];
            prevEdge = new int[n];
            children = new int[n];
            subtreeSize = new int[n];
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(hops, Integer.MAX_VALUE);
            Arrays.fill(prev, -1);
            Arrays.fill(prevEdge, -1);
            dist[source] = 0;
            hops[source] = 0;

            // k, t and levels like in the paper
            double log = Math.log(Math.max(2, n)) / Math.log(2);
            k = Math.max(1, (int) Math.cbrt(log));
            t = Math.max(1, (int) Math.pow(log, 2.0 / 3.0));
            levels = (int) Math.ceil(log / t);
            workspaces = new Workspace[levels + 1];
        }

        SingleSourceResult run() {
            BoundedResult result = bmssp(levels, INFINITY, List.of(source));
            if (result.completed().size() != reachableInternalNodes) {
                throw new IllegalStateException("Top-level BMSSP did not finish");
            }
            double[] originalDistances = new double[graph.originalToInternal.length];
            Arrays.fill(originalDistances, Double.POSITIVE_INFINITY);
            for (int node = 0; node < originalDistances.length; node++) {
                int internal = graph.originalToInternal[node];
                if (internal >= 0) {
                    originalDistances[node] = dist[internal];
                }
            }
            return new SingleSourceResult(reachableOriginalNodes,
                    Math.toIntExact(relaxedCounter), originalDistances);
        }

        private int[] pathTo(int target) {
            int internal = graph.originalToInternal[target];
            if (internal < 0 || !Double.isFinite(dist[internal])) {
                return new int[0];
            }
            ArrayDeque<Integer> path = new ArrayDeque<>();
            for (int remaining = graph.nodeCount(); remaining > 0 && internal >= 0; remaining--) {
                int original = graph.internalToOriginal[internal];
                if (path.isEmpty() || path.getFirst() != original) {
                    path.addFirst(original);
                }
                if (internal == source) {
                    return path.stream().mapToInt(Integer::intValue).toArray();
                }
                internal = prev[internal];
            }
            throw new IllegalStateException("Duan predecessor chain did not reach the source");
        }

        private Workspace workspace(int level) {
            if (workspaces[level] == null) {
                workspaces[level] = new Workspace(graph.nodeCount());
            }
            Workspace workspace = workspaces[level];
            workspace.completed.clear();
            workspace.visited.clear();
            workspace.frontier.clear();
            workspace.next.clear();
            workspace.prepend.clear();
            return workspace;
        }

        private BoundedResult bmssp(int level, Entry bound, List<Integer> sources) {
            Workspace workspace = workspace(level);
            // level 0 is the base case
            if (level == 0) {
                return baseCase(bound, sources, workspace.completed);
            }

            PivotResult found = findPivots(bound, sources, workspace);
            int batchSize = (int) (1L << ((level - 1) * t));
            DuanQueue queue = new DuanQueue(batchSize, bound, workspace.queueEntries);
            for (int pivot : found.pivots()) {
                queue.insert(entry(pivot));
            }

            long limit = k * (1L << (level * t));
            VertexSet completed = workspace.completed;
            Entry nextBound = bound;

            while (completed.size() < limit && !queue.isEmpty()) {
                DuanQueue.Batch batch = queue.pull();
                BoundedResult part = bmssp(level - 1, batch.bound(), batch.sources());
                nextBound = part.bound();
                completed.addAll(part.completed());

                VertexSet prepend = workspace.prepend;
                prepend.clear();
                // relax the edges of the nodes that just got completed
                for (int node : part.completed()) {
                    for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                        if (!relax(node, edge, INFINITY)) {
                            continue;
                        }
                        int neighbour = graph.edgeTarget(edge);
                        Entry value = entry(neighbour);
                        if (value.compareTo(batch.bound()) >= 0 && value.compareTo(bound) < 0) {
                            queue.insert(value);
                        } else if (value.compareTo(nextBound) >= 0
                                && value.compareTo(batch.bound()) < 0) {
                            prepend.add(neighbour);
                        }
                    }
                }

                for (int node : batch.sources()) {
                    Entry value = entry(node);
                    if (value.compareTo(nextBound) >= 0 && value.compareTo(batch.bound()) < 0) {
                        prepend.add(node);
                    }
                }
                List<Entry> records = new ArrayList<>(prepend.size());
                for (int node : prepend.values) {
                    records.add(entry(node));
                }
                queue.batchPrepend(records);
            }

            if (queue.isEmpty()) {
                nextBound = bound;
            }
            for (int node : found.visited()) {
                if (entry(node).compareTo(nextBound) < 0) {
                    completed.add(node);
                }
            }
            queue.clear();
            return new BoundedResult(nextBound, completed.values);
        }

        private PivotResult findPivots(Entry bound, List<Integer> sources, Workspace workspace) {
            VertexSet visited = workspace.visited;
            VertexSet frontier = workspace.frontier;
            VertexSet next = workspace.next;
            visited.addAll(sources);
            frontier.addAll(sources);
            // relax k rounds from the sources, like bellman-ford
            for (int step = 0; step < k; step++) {
                next.clear();
                for (int node : frontier.values) {
                    for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                        if (relax(node, edge, INFINITY)) {
                            int neighbour = graph.edgeTarget(edge);
                            if (entry(neighbour).compareTo(bound) < 0) {
                                next.add(neighbour);
                            }
                        }
                    }
                }
                visited.addAll(next.values);
                if (visited.size() > (long) k * sources.size()) {
                    return new PivotResult(sources, visited.values);
                }
                VertexSet previous = frontier;
                frontier = next;
                next = previous;
            }

            // sources with at least k nodes in their tree become pivots
            for (int node : visited.values) {
                children[node] = 0;
                subtreeSize[node] = 1;
            }
            for (int node : visited.values) {
                if (hasForestParent(node, visited)) {
                    children[prev[node]]++;
                }
            }

            ArrayDeque<Integer> leaves = new ArrayDeque<>();
            for (int node : visited.values) {
                if (children[node] == 0) {
                    leaves.add(node);
                }
            }
            while (!leaves.isEmpty()) {
                int node = leaves.remove();
                if (hasForestParent(node, visited)) {
                    int parent = prev[node];
                    subtreeSize[parent] += subtreeSize[node];
                    if (--children[parent] == 0) {
                        leaves.add(parent);
                    }
                }
            }

            List<Integer> pivots = new ArrayList<>();
            for (int node : sources) {
                if (!hasForestParent(node, visited) && subtreeSize[node] >= k) {
                    pivots.add(node);
                }
            }
            return new PivotResult(pivots, visited.values);
        }

        private boolean hasForestParent(int node, VertexSet visited) {
            // the prev edge only counts if it still matches the distances
            int parent = prev[node];
            return parent >= 0 && visited.contains(parent)
                    && hops[parent] + 1 == hops[node]
                    && dist[parent] + graph.edgeWeight(prevEdge[node]) == dist[node];
        }

        private BoundedResult baseCase(Entry bound, List<Integer> sources, VertexSet completed) {
            if (sources.size() != 1) {
                throw new IllegalStateException("Base case needs exactly one source");
            }
            // small dijkstra that stops after k + 1 nodes
            PriorityQueue<Entry> queue = new PriorityQueue<>();
            queue.add(entry(sources.get(0)));
            Entry last = bound;

            while (!queue.isEmpty() && completed.size() < k + 1) {
                Entry current = queue.poll();
                int node = current.node();
                if (current.compareTo(entry(node)) != 0 || completed.contains(node)) {
                    continue;
                }
                completed.add(node);
                last = current;

                for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                    if (relax(node, edge, bound)) {
                        int neighbour = graph.edgeTarget(edge);
                        if (!completed.contains(neighbour)) {
                            queue.add(entry(neighbour));
                        }
                    }
                }
            }

            if (completed.size() <= k) {
                return new BoundedResult(bound, completed.values);
            }
            completed.removeLast();
            return new BoundedResult(last, completed.values);
        }

        private Entry entry(int node) {
            return new Entry(node, dist[node], hops[node]);
        }

        private boolean relax(int node, int edge, Entry bound) {
            relaxedCounter++;
            int neighbour = graph.edgeTarget(edge);
            double weight = graph.edgeWeight(edge);
            double newDist = dist[node] + weight;
            if (!Double.isFinite(newDist)) {
                throw new ArithmeticException("Path distance exceeds the finite double range");
            }
            int newHops = hops[node] + 1;
            if (Entry.compare(newDist, newHops, neighbour, bound) >= 0) {
                return false;
            }
            int comparison = Double.compare(newDist, dist[neighbour]);
            if (comparison == 0) {
                comparison = Integer.compare(newHops, hops[neighbour]);
            }
            // on a tie the smaller node wins, and the same node can relax again
            if (comparison > 0 || (comparison == 0 && node > prev[neighbour])) {
                return false;
            }
            dist[neighbour] = newDist;
            hops[neighbour] = newHops;
            prev[neighbour] = node;
            prevEdge[neighbour] = edge;
            return true;
        }
    }

    private static class Workspace {
        final VertexSet completed;
        final VertexSet visited;
        final VertexSet frontier;
        final VertexSet next;
        final VertexSet prepend;
        final DuanQueue.Item[] queueEntries;

        Workspace(int n) {
            completed = new VertexSet(n);
            visited = new VertexSet(n);
            frontier = new VertexSet(n);
            next = new VertexSet(n);
            prepend = new VertexSet(n);
            queueEntries = new DuanQueue.Item[n];
        }
    }

    private static class VertexSet {
        final List<Integer> values = new ArrayList<>();
        private final boolean[] present;

        VertexSet(int n) {
            present = new boolean[n];
        }

        void add(int node) {
            if (!present[node]) {
                present[node] = true;
                values.add(node);
            }
        }

        void addAll(List<Integer> nodes) {
            for (int node : nodes) {
                add(node);
            }
        }

        boolean contains(int node) {
            return present[node];
        }

        int size() {
            return values.size();
        }

        void removeLast() {
            present[values.remove(values.size() - 1)] = false;
        }

        void clear() {
            for (int node : values) {
                present[node] = false;
            }
            values.clear();
        }
    }
}

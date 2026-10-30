package dk.dtu.routeplanner.core.algorithms.duan;

import java.util.Arrays;

import dk.dtu.routeplanner.core.graph.Graph;

final class DuanGraph implements Graph {
    private final int[] offsets;
    private final int[] targets;
    private final double[] weights;
    final int[] originalToInternal;
    final int[] internalToOriginal;
    final int source;
    final int reachableCount;

    private DuanGraph(int[] offsets, int[] targets, double[] weights,
            int[] originalToInternal, int[] internalToOriginal, int source, int reachableCount) {
        this.offsets = offsets;
        this.targets = targets;
        this.weights = weights;
        this.originalToInternal = originalToInternal;
        this.internalToOriginal = internalToOriginal;
        this.source = source;
        this.reachableCount = reachableCount;
    }

    static DuanGraph prepare(Graph graph, int source) {
        int n = graph.nodeCount();
        int[] mapping = new int[n];
        Arrays.fill(mapping, -1);
        int[] originals = new int[n];
        originals[0] = source;
        mapping[source] = 0;
        int reachable = 1;

        for (int head = 0; head < reachable; head++) {
            int node = originals[head];
            for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                int target = graph.edgeTarget(edge);
                double weight = graph.edgeWeight(edge);
                if (target < 0 || target >= n || !Double.isFinite(weight) || weight < 0) {
                    throw new IllegalArgumentException("Duan needs valid targets and finite, nonnegative weights");
                }
                if (mapping[target] == -1) {
                    mapping[target] = reachable;
                    originals[reachable++] = target;
                }
            }
        }

        int[] seen = new int[reachable];
        Arrays.fill(seen, -1);
        int[] compactOffsets = new int[reachable + 1];
        int edgeCount = 0;
        for (int from = 0; from < reachable; from++) {
            int original = originals[from];
            compactOffsets[from] = edgeCount;
            for (int edge = graph.edgeStart(original); edge < graph.edgeEnd(original); edge++) {
                int to = mapping[graph.edgeTarget(edge)];
                if (to != from && seen[to] != from) {
                    seen[to] = from;
                    edgeCount = Math.incrementExact(edgeCount);
                }
            }
        }
        compactOffsets[reachable] = edgeCount;

        int[] compactTargets = new int[edgeCount];
        double[] compactWeights = new double[edgeCount];
        int[] position = new int[reachable];
        int[] incoming = new int[reachable];
        Arrays.fill(seen, -1);
        for (int from = 0; from < reachable; from++) {
            int original = originals[from];
            int next = compactOffsets[from];
            for (int edge = graph.edgeStart(original); edge < graph.edgeEnd(original); edge++) {
                int to = mapping[graph.edgeTarget(edge)];
                if (to == from) {
                    continue;
                }
                double weight = graph.edgeWeight(edge);
                if (seen[to] != from) {
                    seen[to] = from;
                    position[to] = next;
                    compactTargets[next] = to;
                    compactWeights[next++] = weight == 0 ? 0 : weight;
                    incoming[to]++;
                } else if (weight < compactWeights[position[to]]) {
                    compactWeights[position[to]] = weight;
                }
            }
        }

        int[] firstCopy = new int[reachable + 1];
        for (int node = 0; node < reachable; node++) {
            int outgoing = compactOffsets[node + 1] - compactOffsets[node];
            int copies = Math.max(1, Math.max(incoming[node], outgoing));
            firstCopy[node + 1] = Math.addExact(firstCopy[node], copies);
        }
        int internalCount = firstCopy[reachable];
        int[] offsets = new int[Math.addExact(internalCount, 1)];
        int[] internalToOriginal = new int[internalCount];
        for (int node = 0; node < reachable; node++) {
            int copies = firstCopy[node + 1] - firstCopy[node];
            int outgoing = compactOffsets[node + 1] - compactOffsets[node];
            for (int copy = 0; copy < copies; copy++) {
                int id = firstCopy[node] + copy;
                internalToOriginal[id] = originals[node];
                int degree = (copies > 1 ? 1 : 0) + (copy < outgoing ? 1 : 0);
                offsets[id + 1] = Math.addExact(offsets[id], degree);
            }
        }

        int[] targets = new int[offsets[internalCount]];
        double[] weights = new double[targets.length];
        Arrays.fill(incoming, 0);
        for (int node = 0; node < reachable; node++) {
            int copies = firstCopy[node + 1] - firstCopy[node];
            if (copies > 1) {
                for (int copy = 0; copy < copies; copy++) {
                    int id = firstCopy[node] + copy;
                    targets[offsets[id]] = firstCopy[node] + (copy + 1) % copies;
                }
            }
            for (int edge = compactOffsets[node]; edge < compactOffsets[node + 1]; edge++) {
                int from = firstCopy[node] + edge - compactOffsets[node];
                int targetNode = compactTargets[edge];
                int to = firstCopy[targetNode] + incoming[targetNode]++;
                int outputEdge = offsets[from] + (copies > 1 ? 1 : 0);
                targets[outputEdge] = to;
                weights[outputEdge] = compactWeights[edge];
            }
            mapping[originals[node]] = firstCopy[node];
        }
        return new DuanGraph(offsets, targets, weights, mapping, internalToOriginal,
                mapping[source], reachable);
    }

    @Override
    public int nodeCount() {
        return offsets.length - 1;
    }

    @Override
    public int edgeStart(int node) {
        return offsets[node];
    }

    @Override
    public int edgeEnd(int node) {
        return offsets[node + 1];
    }

    @Override
    public int edgeTarget(int edge) {
        return targets[edge];
    }

    @Override
    public double edgeWeight(int edge) {
        return weights[edge];
    }
}

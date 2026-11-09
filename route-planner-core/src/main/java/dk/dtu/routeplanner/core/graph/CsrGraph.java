package dk.dtu.routeplanner.core.graph;

import java.util.Objects;

public class CsrGraph implements Graph {

    // the edges of node i run from offsets[i] up to but not including offsets[i + 1]
    private final int[] offsets;
    private final int[] targets;
    private final double[] weights;

    public CsrGraph(int[] offsets, int[] targets, double[] weights) {

        Objects.requireNonNull(offsets, "offsets");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(weights, "weights");

        if (offsets.length == 0) {
            throw new IllegalArgumentException("Offsets array cannot be empty");
        }

        if (targets.length != weights.length) {
            throw new IllegalArgumentException(
                "Targets and weights must have the same length"
            );
        }

        int nodeCount = offsets.length - 1;

        if (offsets[0] != 0 || offsets[nodeCount] != targets.length) {
            throw new IllegalArgumentException("Invalid CSR offsets");
        }

        for (int i = 0; i < nodeCount; i++) {
            if (offsets[i] > offsets[i + 1]) {
                throw new IllegalArgumentException(
                    "CSR offsets must be nondecreasing"
                );
            }
        }

        for (int i = 0; i < targets.length; i++) {

            if (targets[i] < 0 || targets[i] >= nodeCount) {
                throw new IllegalArgumentException(
                    "Invalid target node at edge " + i
                );
            }

            if (!Double.isFinite(weights[i]) || weights[i] < 0) {
                throw new IllegalArgumentException(
                    "Invalid edge weight at edge " + i
                );
            }
        }

        this.offsets = offsets.clone();
        this.targets = targets.clone();
        this.weights = weights.clone();
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
        return offsets[node+1];
    }

    @Override
    public int edgeTarget(int i) {
        return targets[i];
    }

    @Override
    public double edgeWeight(int i) {
        return weights[i];
    }
    
}

package dk.dtu.routeplanner.core.graph;

public class CsrGraph implements Graph {

    private final int[] offsets;
    private final int[] targets;
    private final double[] weights;

    public CsrGraph(int[] offsets, int[] targets, double[] weights) {
        this.offsets = offsets;
        this.targets = targets;
        this.weights = weights;
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

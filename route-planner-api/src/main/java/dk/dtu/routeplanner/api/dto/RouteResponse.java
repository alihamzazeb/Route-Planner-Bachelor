package dk.dtu.routeplanner.api.dto;

public class RouteResponse {

    private final String algorithm;
    private final int source;
    private final int target;
    private final boolean reachable;
    private final Double distance;
    private final int[] path;
    private final double[][] coordinates;
    private final int settledNodes;
    private final int relaxedEdges;

    public RouteResponse(
            String algorithm,
            int source,
            int target,
            boolean reachable,
            Double distance,
            int[] path,
            double[][] coordinates,
            int settledNodes,
            int relaxedEdges
    ) {
        this.algorithm = algorithm;
        this.source = source;
        this.target = target;
        this.reachable = reachable;
        this.distance = distance;
        this.path = path.clone();
        this.coordinates = coordinates == null
                ? new double[0][]
                : copyCoordinates(coordinates);
        this.settledNodes = settledNodes;
        this.relaxedEdges = relaxedEdges;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getSource() {
        return source;
    }

    public int getTarget() {
        return target;
    }

    public boolean isReachable() {
        return reachable;
    }

    public Double getDistance() {
        return distance;
    }

    public int[] getPath() {
        return path.clone();
    }

    public int getSettledNodes() {
        return settledNodes;
    }

    public int getRelaxedEdges() {
        return relaxedEdges;
    }

    public double[][] getCoordinates() {
        return copyCoordinates(coordinates);
    }

    private static double[][] copyCoordinates(double[][] values) {
        double[][] copy = new double[values.length][];

        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i].clone();
        }

        return copy;
    }

    
}
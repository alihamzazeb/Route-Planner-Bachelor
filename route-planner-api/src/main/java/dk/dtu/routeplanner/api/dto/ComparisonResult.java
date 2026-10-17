
package dk.dtu.routeplanner.api.dto;

public record ComparisonResult(
        String algorithm,
        boolean reachable,
        Double distance,
        int pathNodes,
        int settledNodes,
        int relaxedEdges,
        double timeMs
) {
}

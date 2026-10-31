package dk.dtu.routeplanner.api.dto;

public record SingleSourceResponse(
        String algorithm,
        int source,
        int nodeCount,
        int reachableNodes,
        int unreachableNodes,
        int settledNodes,
        int relaxedEdges,
        double timeMs,
        double sourceLongitude,
        double sourceLatitude
) {
}

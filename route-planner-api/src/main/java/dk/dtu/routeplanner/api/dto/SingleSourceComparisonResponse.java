package dk.dtu.routeplanner.api.dto;

import java.util.List;

public record SingleSourceComparisonResponse(
        int source,
        SingleSourceResponse result,
        List<SingleSourceResponse> results,
        int comparedNodes,
        int distanceMismatches
) {
}

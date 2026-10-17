
package dk.dtu.routeplanner.api.dto;

import java.util.List;

public record ComparisonResponse(
        int source,
        int target,
        RouteResponse route,
        List<ComparisonResult> results
) {
}

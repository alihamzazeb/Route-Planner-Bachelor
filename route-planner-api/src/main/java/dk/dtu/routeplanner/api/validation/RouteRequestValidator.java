package dk.dtu.routeplanner.api.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RouteRequestValidator {

    public static void validateSource(int source, int nodeCount) {

        if (source < 0 || source >= nodeCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid source node: " + source
            );
        }

    }

    public static void validateNodes(int source, int target, int nodeCount) {
        validateSource(source, nodeCount);

        if (target < 0 || target >= nodeCount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid target node: " + target
            );
        }
    }
}

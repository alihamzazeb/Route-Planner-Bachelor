package dk.dtu.routeplanner.core.algorithms.alt;

import dk.dtu.routeplanner.core.algorithms.Heuristic;
import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;

public class LandmarkHeuristic implements Heuristic {

    private final LandmarkPreprocessingResult data;

    public LandmarkHeuristic(LandmarkPreprocessingResult data) {
        this.data = data;
    }

    @Override
    public double estimate(int node, int target) {

        double best = 0.0;

        for (int i = 0; i < data.landmarkCount(); i++) {

            double landmarkToTarget = data.landmarkFromDistance(i, target);
            double landmarkToNode = data.landmarkFromDistance(i, node);
            double nodeToLandmark = data.landmarkToDistance(i, node);
            double targetToLandmark = data.landmarkToDistance(i, target);

            // Forward bound: d(L,target) - d(L,node). Skip infinite distances.
            if (Double.isFinite(landmarkToTarget) && Double.isFinite(landmarkToNode)) {
                double lowerBound = landmarkToTarget - landmarkToNode;
                best = Math.max(best, lowerBound);
            }

            // Reverse bound: d(node,L) - d(target,L).
            if (Double.isFinite(nodeToLandmark) && Double.isFinite(targetToLandmark)) {
                double lowerBound = nodeToLandmark - targetToLandmark;
                best = Math.max(best, lowerBound);
            }

        }

        return best;
    }
}

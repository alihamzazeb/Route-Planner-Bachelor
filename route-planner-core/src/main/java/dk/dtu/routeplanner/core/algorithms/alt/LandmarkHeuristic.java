package dk.dtu.routeplanner.core.algorithms.alt;

import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;

public class LandmarkHeuristic {

    private final LandmarkPreprocessingResult data;

    public LandmarkHeuristic(LandmarkPreprocessingResult data) {
        this.data = data;
    }

    public double estimate(int node, int target) {

        double best = 0.0;

        for (int i = 0; i < data.landmarkCount(); i++) {

            // Calculate the lower bounds using landmark i
            double landmarkToTarget = data.landmarkFromDistance(i, target);
            double landmarkToNode = data.landmarkFromDistance(i, node);
            double nodeToLandmark = data.landmarkToDistance(i, node);
            double targetToLandmark = data.landmarkToDistance(i, target);

            if (Double.isFinite(landmarkToTarget) && Double.isFinite(landmarkToNode)) {
                double lowerBound = landmarkToTarget - landmarkToNode;
                best = Math.max(best, lowerBound);
            }

            if (Double.isFinite(nodeToLandmark) && Double.isFinite(targetToLandmark)) {
                double lowerBound = nodeToLandmark - targetToLandmark;
                best = Math.max(best, lowerBound);
            }

        }

        return best;
    }
}
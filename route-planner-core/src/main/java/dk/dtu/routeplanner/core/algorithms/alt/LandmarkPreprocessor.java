package dk.dtu.routeplanner.core.algorithms.alt;

import dk.dtu.routeplanner.core.algorithms.Dijkstra;
import dk.dtu.routeplanner.core.graph.RoadNetwork;
import dk.dtu.routeplanner.core.model.LandmarkPreprocessingResult;
import dk.dtu.routeplanner.core.model.SingleSourceResult;

public class LandmarkPreprocessor {

    private final RoadNetwork network;

    public LandmarkPreprocessor(RoadNetwork network) {
        this.network = network;
    }

    public LandmarkPreprocessingResult preprocess(int[] landmarks) {

        Dijkstra dijkstra = new Dijkstra();

        // we make these arrays using SingleSourceResult so we can get the whole djikstra result
        SingleSourceResult[] fromLandmark = new SingleSourceResult[landmarks.length];
        SingleSourceResult[] toLandmark = new SingleSourceResult[landmarks.length];

        // Loop through every landmark
        for (int i=0; i < landmarks.length; i++) {

            int landmark = landmarks[i];
            // Run forward and reverse Dijkstra
            SingleSourceResult from = dijkstra.computeShortestPaths(network.forward(), landmark);
            SingleSourceResult to = dijkstra.computeShortestPaths(network.reverse(), landmark);


            // Store both results
            fromLandmark[i] = from;
            toLandmark[i] = to;

        }
        return new LandmarkPreprocessingResult(fromLandmark, toLandmark, landmarks);

    }
}
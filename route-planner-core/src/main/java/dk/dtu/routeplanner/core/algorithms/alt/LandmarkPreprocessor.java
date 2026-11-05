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

        SingleSourceResult[] fromLandmark = new SingleSourceResult[landmarks.length];
        SingleSourceResult[] toLandmark = new SingleSourceResult[landmarks.length];

        for (int i=0; i < landmarks.length; i++) {

            int landmark = landmarks[i];
            SingleSourceResult from = dijkstra.computeShortestPaths(network.forward(), landmark);
            SingleSourceResult to = dijkstra.computeShortestPaths(network.reverse(), landmark);


            fromLandmark[i] = from;
            toLandmark[i] = to;

        }
        return new LandmarkPreprocessingResult(fromLandmark, toLandmark, landmarks);

    }
}
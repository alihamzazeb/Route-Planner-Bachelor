package dk.dtu.routeplanner.core.model;

public class LandmarkPreprocessingResult {

    private final int[] landmarks;
    private final SingleSourceResult[] fromLandmark;
    private final SingleSourceResult[] toLandmark;

    public LandmarkPreprocessingResult(SingleSourceResult[] fromLandmark, SingleSourceResult[] toLandmark, int[] landmarks) {
        this.fromLandmark = fromLandmark;
        this.toLandmark = toLandmark;
        this.landmarks = landmarks;
    }

    public double landmarkFromDistance(int landmarkIndex, int node){
        return fromLandmark[landmarkIndex].distance(node);
    }

    public double landmarkToDistance(int landmarkIndex, int node){
        return toLandmark[landmarkIndex].distance(node);
    }

    public int landmark(int landmarkIndex){
        return landmarks[landmarkIndex];
    }    

    public int landmarkCount() {
        return landmarks.length;
    }
}
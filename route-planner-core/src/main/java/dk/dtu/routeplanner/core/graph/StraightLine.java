package dk.dtu.routeplanner.core.graph;

public class StraightLine {
    private final double squeeze;
    private final Coordinates coordinates;


    public StraightLine(Coordinates coordinates){
        this.squeeze = squeezeFor(coordinates);
        this.coordinates = coordinates;

    }

    
    private static double squeezeFor(Coordinates coordinates) {
        // A degree of longitude gets shorter further from the equator, so east-west
        // differences are scaled by the cosine of the data's middle latitude.
        int[] latitudes = coordinates.latitude();
        int smallest=latitudes[0];
        int largest=latitudes[0];


        for (int i = 1; i < latitudes.length; i++) {
            if (latitudes[i]<smallest){
                smallest=latitudes[i];
            }
            if (latitudes[i]>largest){
                largest=latitudes[i];
            } 
        }

        double middle = (smallest + largest) / 2.0;

        // coordinates are stored in millionths of a degree
        double middleDegrees=middle/1000000.0;

        double squeeze = Math.cos(Math.toRadians(middleDegrees));

        return squeeze;
    }


    public double distance(int a, int b){
        // get the longi and latitude of the current and target node
        int latitudeA=coordinates.latitude()[a];
        int latitudeB=coordinates.latitude()[b];
        int longitudeA=coordinates.longitude()[a];
        int longitudeB=coordinates.longitude()[b];

        int east = longitudeB - longitudeA;
        int north = latitudeB - latitudeA;

        double eastSqueeze = east * squeeze;

        // pythagoras
        double straightLine = Math.sqrt(Math.pow(eastSqueeze, 2) + Math.pow(north, 2));

        return straightLine;
    }

    // Choose c so c * distance(u,v) <= weight(u,v) for every edge.
    // The triangle inequality then makes the heuristic consistent.
    public double smallestLimit(Graph graph) {
        double smallest = Double.MAX_VALUE;

        for (int u = 0; u < graph.nodeCount(); u++) {
            for (int i = graph.edgeStart(u); i < graph.edgeEnd(u); i++) {
                int v = graph.edgeTarget(i);
                double weight = graph.edgeWeight(i);
                double straight = distance(u, v);

                // Both ends are at the same spot, so this edge puts no limit on c.
                if (straight == 0) {
                    continue;
                }

                double limit = weight/straight;

                if (smallest>limit) {
                    smallest = limit;
                }
            }
        }
        return smallest;
    } 
    
}

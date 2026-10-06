package dk.dtu.routeplanner.core.graph;

public class StraightLine {
    private final double squeeze;
    private final Coordinates coordinates;


    public StraightLine(Coordinates coordinates){
        this.squeeze = squeezeFor(coordinates);
        this.coordinates = coordinates;

    }

    
    private static double squeezeFor(Coordinates coordinates) {

        // find the smallest and largest latitude. Loop over coordinates.latitude() and keep track of both.
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


        // turn it into degrees. Divide by 1000000
        double middleDegrees=middle/1000000.0;

        double squeeze= Math.cos(Math.toRadians(middleDegrees));

        return squeeze;
    }

    public double distance(int a, int b){
        int latitudeA=coordinates.latitude()[a];
        int latitudeB=coordinates.latitude()[b];
        int longitudeA=coordinates.longitude()[a];
        int longitudeB=coordinates.longitude()[b];


        int east= longitudeB-longitudeA;
        int north= latitudeB-latitudeA;

        double eastSqueeze= east*squeeze;

        double straightLine= Math.sqrt(Math.pow(eastSqueeze, 2) + Math.pow(north, 2));

        return straightLine;
    }


    public double smallestLimit(Graph graph) {
        double smallest = Double.MAX_VALUE;

        for (int u = 0; u < graph.nodeCount(); u++) {
            for (int i = graph.edgeStart(u); i < graph.edgeEnd(u); i++) {
                int v = graph.edgeTarget(i); // where this road goes
                double weight = graph.edgeWeight(i);// this road's weight
                double straight = distance(u, v);

                // cant divide by 0, skip this road
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

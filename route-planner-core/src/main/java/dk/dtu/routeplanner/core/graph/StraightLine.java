package dk.dtu.routeplanner.core.graph;

public class StraightLine {
    private final double squeeze;
    private final Coordinates coordinates;


    public StraightLine(Coordinates coordinates){
        this.squeeze = squeezeFor(coordinates);
        this.coordinates = coordinates;

    }

    
    private static double squeezeFor(Coordinates coordinates) {
        /*
        * 1 degree of latitude is approximately the same around the globe.
        * but 1 degree longitude depends on what latitude is.
        * 1 degree longitude get smaller the more higher the latitude is
        */


        // find the smallest and largest latitude.
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

        // average of smallest and largest
        double middle = (smallest + largest) / 2.0;

        // turn it into degrees. Divide by 1000000
        double middleDegrees=middle/1000000.0;

        // using cos() to find the difference between 1 degree latitude and longitude at 40 latitude (middle is new york)
        double squeeze = Math.cos(Math.toRadians(middleDegrees));

        return squeeze;
    }

    public double distance(int a, int b){
        // get the longi and latitude of the current and target node
        int latitudeA=coordinates.latitude()[a];
        int latitudeB=coordinates.latitude()[b];
        int longitudeA=coordinates.longitude()[a];
        int longitudeB=coordinates.longitude()[b];

        // east is side
        // nort is up/down
        int east = longitudeB - longitudeA;
        int north = latitudeB - latitudeA;

        double eastSqueeze = east * squeeze;

        // pythagoras
        double straightLine = Math.sqrt(Math.pow(eastSqueeze, 2) + Math.pow(north, 2));

        return straightLine;
    }

    // calculate c limit
    public double smallestLimit(Graph graph) {
        /*
         * check all node pairs and see if the straightline is smaller or equal to the actual distance
         */
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

                // check wether the straigth line is <= the actual distance.
                // 1 or more means straightline <=
                double limit = weight/straight;

                // get the smallest c value in the whole
                if (smallest>limit) {
                    smallest = limit;
                }
            }
        }
        return smallest;
    } 
    
}

package com.example.mapmatchingproject;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.PointsCollection;
import com.example.mapmatchingproject.entities.RoadSegment;
import com.example.mapmatchingproject.matchers.HMMMapMatcher;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class MapMatchingProjectApplication {


    public static void main(String[] args) throws Exception {

//        ApplicationContext context = SpringApplication.run(MapMatchingProjectApplication.class, args);

        PointsCollection pointsCollection = new PointsCollection();
        List<Point> gpsPoints = pointsCollection.getPointList();

        double south = pointsCollection.getSouth().getLat();
        double west = pointsCollection.getWest().getLon();
        double north = pointsCollection.getNorth().getLat();
        double east = pointsCollection.getEast().getLon();

        List<Point> roadPoints = Util.getRoadPointsFromOverpass(south, west, north, east);
        System.out.println("Завантажено " + roadPoints.size() + " точок дороги з Overpass API.");

//        System.out.println("Euclidean.html Matching Results:");
//        List<Point> euclideanMatched = new ArrayList<>();

        List<RoadSegment> segments = RoadSegment.buildSegmentsFromGeometry(Util.getJSONFromOverpass(south, west, north, east));

//        EuclideanMatcher matcher = new EuclideanMatcher(roadPoints, segments);
//
//        LocalDateTime startEuclidean = LocalDateTime.now();
//        for (Point gps : gpsPoints) {
////            Point matched = matcher.match(gps);
//            Point matched = matcher.matchToRoad(gps);
//            euclideanMatched.add(matched);
//            System.out.println("GPS: " + gps + " => Matched: " + matched);
//        }
//        long euclideanTime = LocalDateTime.now().getNano() - startEuclidean.getNano();
//        System.out.println("Time of euclidean approach: " + euclideanTime);
//        System.out.println("\nOSRM.html Matching Results:");
//
//        LocalDateTime startOSRM = LocalDateTime.now();
//        var result = OSRMMapMatcher.match(pointsCollection.getPointList());
//        List<Point> OSRMResult = Util.extractMatchedPoints(result.toString());
//        long osrmTime = LocalDateTime.now().getNano() - startOSRM.getNano();
//        System.out.println(OSRMResult);
//        System.out.println("Time of osrm: " + osrmTime);
//
//
//        MapGenerator.generateHtmlMap(pointsCollection.getPointList(), euclideanMatched, "Euclidean.html");
//        MapGenerator.generateHtmlMap(pointsCollection.getPointList(), OSRMResult, "OSRM.html");



        HMMMapMatcher hmmMatcher = new HMMMapMatcher(segments);
        long startHMM = System.currentTimeMillis();
        List<Point> hmmMatchedPoints = hmmMatcher.match(gpsPoints); // The algorithm runs here
        System.out.println("Time of HMM: " + (System.currentTimeMillis() - startHMM) + " ms");
        MapGenerator.generateHtmlMap(gpsPoints, hmmMatchedPoints, "HMM.html");
    }




}

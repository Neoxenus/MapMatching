package com.example.mapmatchingproject;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.PointsCollection;
import com.example.mapmatchingproject.entities.RoadSegment;
import com.example.mapmatchingproject.matchers.EuclideanMatcher;
import com.example.mapmatchingproject.matchers.OSRMMapMatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SpringBootApplication
public class MapMatchingProjectApplication {


    public static void main(String[] args) throws Exception {

        ApplicationContext context = SpringApplication.run(MapMatchingProjectApplication.class, args);

        PointsCollection pointsCollection = new PointsCollection();
        List<Point> gpsPoints = pointsCollection.getPointList();

        double south = pointsCollection.getSouth().getLat();
        double west = pointsCollection.getWest().getLon();
        double north = pointsCollection.getNorth().getLat();
        double east = pointsCollection.getEast().getLon();

        List<Point> roadPoints = getRoadPointsFromOverpass(south, west, north, east);
        System.out.println("Завантажено " + roadPoints.size() + " точок дороги з Overpass API.");

        System.out.println("Euclidean.html Matching Results:");
        List<Point> euclideanMatched = new ArrayList<>();

        List<RoadSegment> segments = RoadSegment.buildSegmentsFromGeometry(getJSONFromOverpass(south, west, north, east));

        EuclideanMatcher matcher = new EuclideanMatcher(roadPoints, segments);

        for (Point gps : gpsPoints) {
//            Point matched = matcher.match(gps);
            Point matched = matcher.matchToRoad(gps);
            euclideanMatched.add(matched);
            System.out.println("GPS: " + gps + " => Matched: " + matched);
        }

        System.out.println("\nOSRM.html Matching Results:");
        var result = OSRMMapMatcher.match(pointsCollection.getPointList());
        List<Point> OSRMResult = extractMatchedPoints(result.toString());
        System.out.println(OSRMResult);


        MapGenerator.generateHtmlMap(pointsCollection.getPointList(), euclideanMatched, "Euclidean.html");
        MapGenerator.generateHtmlMap(pointsCollection.getPointList(), OSRMResult, "OSRM.html");

    }

    public static List<Point> extractMatchedPoints(String jsonString) {
        List<Point> points = new ArrayList<>();

        JSONArray matches = new JSONArray(jsonString);
        if (matches.length() == 0) return points;

        JSONObject match = matches.getJSONObject(0);
        JSONObject geometry = match.getJSONObject("geometry");
        JSONArray coordinates = geometry.getJSONArray("coordinates");

        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray coord = coordinates.getJSONArray(i);
            double lon = coord.getDouble(0);
            double lat = coord.getDouble(1);
            points.add(new Point(lat, lon));
        }

        return points;
    }


    private static JSONArray getJSONFromOverpass(double south, double west, double north, double east) throws IOException{


        String query = String.format(Locale.US,"""
                [out:json];
                way
                    ["highway"~"motorway|trunk|primary|secondary|tertiary|residential"]
                    (%f,%f,%f,%f);
                out geom;
                """, south, west, north, east);

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URL url = new URL("https://overpass-api.de/api/interpreter?data=" + encodedQuery);

        JSONObject response = OSRMMapMatcher.connect(url);
        return response.getJSONArray("elements");
    }
    private static List<Point> getRoadPointsFromOverpass(double south, double west, double north, double east) throws IOException {
        List<Point> roadPoints = new ArrayList<>();
        JSONArray elements = getJSONFromOverpass(south, west, north, east);
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            if (element.has("geometry")) {
                JSONArray geometry = element.getJSONArray("geometry");
                for (int j = 0; j < geometry.length(); j++) {
                    JSONObject pointJson = geometry.getJSONObject(j);
                    double lat = pointJson.getDouble("lat");
                    double lon = pointJson.getDouble("lon");
                    roadPoints.add(new Point(lat, lon));
                }
            }
        }

        return roadPoints;
    }


}

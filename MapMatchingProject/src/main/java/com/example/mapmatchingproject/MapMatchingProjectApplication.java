package com.example.mapmatchingproject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

@SpringBootApplication
public class MapMatchingProjectApplication {


    public static void main(String[] args) throws Exception {
        // 1. Load GPS points
        PointsCollection pointsCollection = new PointsCollection();
        List<Point> gpsPoints = pointsCollection.getPointList();

        // Встанови межі координат (bounding box) під свої GPS-дані
        double south = pointsCollection.getSouth().getLat();
        double west = pointsCollection.getWest().getLon();
        double north = pointsCollection.getNorth().getLat();
        double east = pointsCollection.getEast().getLon();

//        List<Point> roadPoints = getRoadPointsFromOverpass(south, west, north, east);
//        System.out.println("Завантажено " + roadPoints.size() + " точок дороги з Overpass API.");



//        EuclideanMatcher matcher = new EuclideanMatcher(roadPoints);
        System.out.println("Euclidean.html Matching Results:");
        List<Point> euclideanMatched = new ArrayList<>();

        List<RoadSegment> segments = RoadSegment.buildSegmentsFromGeometry(getJSONFromOverpass(south, west, north, east));

        for (Point gps : gpsPoints) {
//            Point matched = matcher.match(gps);
            Point matched = EuclideanMatcher.matchToRoad(gps, segments);
            euclideanMatched.add(matched);
            System.out.println("GPS: " + gps + " => Matched: " + matched);
        }

        // 4. OSRM.html API
        System.out.println("\nOSRM.html Matching Results:");
        var result = OSRMMapMatcher.match(pointsCollection.getPointList());
        List<Point> OSRMResult = extractMatchedPoints(result.toString());
        System.out.println(OSRMResult);


        MapGenerator.generateHtmlMap(pointsCollection.getPointList(), euclideanMatched, "Euclidean.html");
        MapGenerator.generateHtmlMap(pointsCollection.getPointList(), OSRMResult, "OSRM.html");

//        System.out.println(result.toString(2));
    }

    public static List<Point> extractMatchedPoints(String jsonString) {
        List<Point> points = new ArrayList<>();

        JSONArray matches = new JSONArray(jsonString);
        if (matches.length() == 0) return points;

        JSONObject match = matches.getJSONObject(0); // беремо перший matching result
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
        JSONArray elements = response.getJSONArray("elements");
        return elements;
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

package com.example.mapmatchingproject;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.matchers.OSRMMapMatcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class Util {
    private static final String OVERPASS_API_QUERY = """
                [out:json];
                way
                    ["highway"~"motorway|trunk|primary|secondary|tertiary|residential"]
                    (%f,%f,%f,%f);
                out geom;
                """;
    private static final String OVERPASS_API_URL = "https://overpass-api.de/api/interpreter?data=";

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


    public static JSONArray getJSONFromOverpass(double south, double west, double north, double east) throws IOException {


        String query = String.format(Locale.US, OVERPASS_API_QUERY, south, west, north, east);

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URL url = new URL(OVERPASS_API_URL + encodedQuery);

        JSONObject response = Util.connect(url);
        return response.getJSONArray("elements");
    }
    public static List<Point> getRoadPointsFromOverpass(double south, double west, double north, double east) throws IOException {
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
    public static JSONObject connect(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        Scanner scanner = new Scanner(conn.getInputStream());
        StringBuilder json = new StringBuilder();
        while (scanner.hasNext()) {
            json.append(scanner.nextLine());
        }
        scanner.close();

        return new JSONObject(json.toString());
    }

}

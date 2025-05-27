package com.example.mapmatchingproject.matchers;

import com.example.mapmatchingproject.entities.Point;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Scanner;

public class OSRMMapMatcher {

    private static final String OSRM_API = "http://router.project-osrm.org/match/v1/driving/";

    public static JSONArray match(List<Point> points) throws IOException {
        StringBuilder coords = new StringBuilder();
        for (Point p : points) {
            coords.append(p.lon).append(",").append(p.lat).append(";");
        }
        coords.setLength(coords.length() - 1);

        String urlStr = OSRM_API + coords.toString() + "?geometries=geojson";
        URL url = new URL(urlStr);

        JSONObject response = connect(url);
        return response.getJSONArray("matchings");
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

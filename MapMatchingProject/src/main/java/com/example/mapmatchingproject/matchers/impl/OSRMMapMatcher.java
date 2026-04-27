package com.example.mapmatchingproject.matchers.impl;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.matchers.MapMatcher;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
@RequiredArgsConstructor
public class OSRMMapMatcher implements MapMatcher {

    private static final String OSRM_API_URL = "http://router.project-osrm.org/match/v1/driving/";
    private final RestTemplate restTemplate;

    @Override
    public List<Point> match(List<Point> rawTrace) {
        try {
            StringBuilder coords = new StringBuilder();
            for (Point p : rawTrace) {
                coords.append(p.getLon()).append(",").append(p.getLat()).append(";");
            }
            coords.setLength(coords.length() - 1);

            String urlStr = OSRM_API_URL + coords + "?geometries=geojson";
            String responseStr = restTemplate.getForObject(urlStr, String.class);
            JSONObject response = new JSONObject(responseStr);
            JSONArray matchings = response.getJSONArray("matchings");

            return extractMatchedPoints(matchings);
        } catch (Exception e) {
            throw new RuntimeException("Помилка під час виконання OSRM Map Matching", e);
        }
    }

    @Override
    public String getMatcherName() {
        return "OSRM";
    }

    private List<Point> extractMatchedPoints(JSONArray matches) {
        List<Point> points = new ArrayList<>();
        if (matches.isEmpty()) return points;

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
}
package com.example.mapmatchingproject.matchers.impl;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.RoadSegment;
import com.example.mapmatchingproject.matchers.MapMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OSRMMapMatcher implements MapMatcher {

    @Value("${mapmatching.osrm.url}")
    private String osrmApiUrl;
    private final RestTemplate restTemplate;

    @Override
    public void initContext(List<RoadSegment> segments) {
        log.info("OSRMMapMatcher is ready (uses external graph).");
    }

    @Override
    public List<Point> match(List<Point> rawTrace) {
        try {
            StringBuilder coords = new StringBuilder();
            for (Point p : rawTrace) {
                coords.append(p.getLon()).append(",").append(p.getLat()).append(";");
            }
            coords.setLength(coords.length() - 1);

            String urlStr = osrmApiUrl + coords + "?geometries=geojson";
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
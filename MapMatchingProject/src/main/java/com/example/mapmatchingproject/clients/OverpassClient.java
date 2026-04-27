package com.example.mapmatchingproject.clients;

import com.example.mapmatchingproject.entities.Point;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class OverpassClient {
    private static final String OVERPASS_API_QUERY = """
                [out:json];
                way
                    ["highway"~"motorway|trunk|primary|secondary|tertiary|residential"]
                    (%f,%f,%f,%f);
                out geom;
                """;
    private static final String OVERPASS_API_URL = "https://overpass-api.de/api/interpreter";

    private final RestTemplate restTemplate;

    public OverpassClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public JSONArray getJSONFromOverpass(double south, double west, double north, double east) {
        String query = String.format(Locale.US, OVERPASS_API_QUERY, south, west, north, east);

        URI uri = UriComponentsBuilder.fromHttpUrl(OVERPASS_API_URL)
                .queryParam("data", query)
                .build()
                .encode()
                .toUri();


        String responseStr = restTemplate.getForObject(uri, String.class);

        JSONObject response = new JSONObject(responseStr);
        return response.getJSONArray("elements");
    }

    public List<Point> getRoadPointsFromOverpass(double south, double west, double north, double east) {
        List<Point> roadPoints = new ArrayList<>();
        JSONArray elements = getJSONFromOverpass(south, west, north, east);

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            if (element.has("geometry")) {
                JSONArray geometry = element.getJSONArray("geometry");
                for (int j = 0; j < geometry.length(); j++) {
                    JSONObject pointJson = geometry.getJSONObject(j);
                    roadPoints.add(new Point(pointJson.getDouble("lat"), pointJson.getDouble("lon")));
                }
            }
        }
        return roadPoints;
    }
}
package com.example.mapmatchingproject.clients;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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
    @Value("${mapmatching.overpass.url}")
    private String OVERPASS_API_URL;

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
}
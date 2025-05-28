package com.example.mapmatchingproject.matchers;

import com.example.mapmatchingproject.Util;
import com.example.mapmatchingproject.entities.Point;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;

public class OSRMMapMatcher {

    private static final String OSRM_API_URL = "http://router.project-osrm.org/match/v1/driving/";

    public static JSONArray match(List<Point> points) throws IOException {
        StringBuilder coords = new StringBuilder();
        for (Point p : points) {
            coords.append(p.lon).append(",").append(p.lat).append(";");
        }
        coords.setLength(coords.length() - 1);

        String urlStr = OSRM_API_URL + coords.toString() + "?geometries=geojson";
//        URL url = new URL(urlStr);
        JSONObject response = Util.connect(new URL(urlStr));
        return response.getJSONArray("matchings");
    }


}

package com.example.mapmatchingproject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class MapGenerator {
    public static void generateHtmlMap(List<Point> gpsPoints, List<Point> matchedPoints, String filename) throws IOException {
        StringBuilder markersJs = new StringBuilder();

        for (Point point : gpsPoints) {
            markersJs.append(String.format(Locale.US,
                    "L.circleMarker([%.6f, %.6f], {color: 'blue'}).addTo(map);\n",
                    point.getLat(), point.getLon()));
        }

        for (Point point : matchedPoints) {
            markersJs.append(String.format(Locale.US,
                    "L.circleMarker([%.6f, %.6f], {color: 'red'}).addTo(map);\n",
                    point.getLat(), point.getLon()));
        }

        // Центруємо карту на першу GPS-точку
        Point center = gpsPoints.get(0);

        String html = String.format(Locale.US, """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <title>Map Matching Visualization</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet/dist/leaflet.css" />
            <style> #map { height: 100vh; } </style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://unpkg.com/leaflet/dist/leaflet.js"></script>
        <script>
            var map = L.map('map').setView([%.6f, %.6f], 16);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19
            }).addTo(map);

            %s
        </script>
        </body>
        </html>
        """, center.getLat(), center.getLon(), markersJs.toString());

        Files.writeString(Path.of("src/main/out/" + filename), html);
        System.out.println("✅ HTML map saved: " + filename);
    }

}

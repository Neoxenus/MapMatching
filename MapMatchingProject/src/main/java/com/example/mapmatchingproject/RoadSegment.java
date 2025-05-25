package com.example.mapmatchingproject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public record RoadSegment(Point a, Point b) {
    public Point project(Point p) {
        double ax = a.getLon(), ay = a.getLat();
        double bx = b.getLon(), by = b.getLat();
        double px = p.getLon(), py = p.getLat();

        double dx = bx - ax;
        double dy = by - ay;

        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) return a; // якщо a==b

        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t)); // обмеження проекції в межах відрізка

        double projX = ax + t * dx;
        double projY = ay + t * dy;

        return new Point(projY, projX); // пам’ятаємо: lat = Y, lon = X
    }

//    public static List<RoadSegment> buildSegmentsFromGeometry(List<Point> geometryPoints) {
//        List<RoadSegment> segments = new ArrayList<>();
//        for (int i = 1; i < geometryPoints.size(); i++) {
//            segments.add(new RoadSegment(geometryPoints.get(i - 1), geometryPoints.get(i)));
//        }
//        return segments;
//    }
    public static List<RoadSegment> buildSegmentsFromGeometry(JSONArray waysArray) {
        List<RoadSegment> segments = new ArrayList<>();

        for (int i = 0; i < waysArray.length(); i++) {
            JSONObject way = waysArray.getJSONObject(i);

            if (!way.has("geometry")) continue; // без геометрії — пропускаємо

            JSONArray geometry = way.getJSONArray("geometry");
            if (geometry.length() < 2) continue; // має бути принаймні 2 точки

            // Побудова сегментів у межах одного way
            for (int j = 1; j < geometry.length(); j++) {
                JSONObject pointA = geometry.getJSONObject(j - 1);
                JSONObject pointB = geometry.getJSONObject(j);

                Point a = new Point(pointA.getDouble("lat"), pointA.getDouble("lon"));
                Point b = new Point(pointB.getDouble("lat"), pointB.getDouble("lon"));

                segments.add(new RoadSegment(a, b));
            }
        }

        return segments;
    }

}

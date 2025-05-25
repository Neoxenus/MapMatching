package com.example.mapmatchingproject;

import java.util.List;

public class EuclideanMatcher {
    private final List<Point> roadPoints;

    public EuclideanMatcher(List<Point> roadPoints) {
        this.roadPoints = roadPoints;
    }

    public Point match(Point gpsPoint) {
        Point closest = null;
        double minDist = Double.MAX_VALUE;
        for (Point rp : roadPoints) {
            double dist = gpsPoint.distanceTo(rp);
            if (dist < minDist) {
                minDist = dist;
                closest = rp;
            }
        }
        return closest;
    }
    public static Point matchToRoad(Point gpsPoint, List<RoadSegment> segments) {
        Point bestMatch = null;
        double minDist = Double.MAX_VALUE;

        for (RoadSegment segment : segments) {
            Point projected = segment.project(gpsPoint);
            double dist = gpsPoint.distanceTo(projected);
            if (dist < minDist) {
                minDist = dist;
                bestMatch = projected;
            }
        }

        return bestMatch;
    }

}

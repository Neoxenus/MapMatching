package com.example.mapmatchingproject.matchers;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.RoadSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EuclideanMatcher {
    private final List<Point> roadPoints;
    List<RoadSegment> segments;

    @Autowired
    public EuclideanMatcher(List<Point> roadPoints, List<RoadSegment> segments) {
        this.roadPoints = roadPoints;
        this.segments = segments;
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
    public Point matchToRoad(Point gpsPoint) {
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

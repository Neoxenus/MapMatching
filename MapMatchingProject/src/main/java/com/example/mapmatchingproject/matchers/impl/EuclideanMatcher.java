package com.example.mapmatchingproject.matchers.impl;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.RoadSegment;
import com.example.mapmatchingproject.matchers.MapMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EuclideanMatcher implements MapMatcher {

    List<RoadSegment> segments;

    @Autowired
    public EuclideanMatcher(List<RoadSegment> segments) {
        this.segments = segments;
    }


    @Override
    public List<Point> match(List<Point> rawTrace) {
        return rawTrace.stream()
                .map(this::matchToRoad)
                .collect(Collectors.toList());
    }

    @Override
    public String getMatcherName() {
        return "Euclidean";
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

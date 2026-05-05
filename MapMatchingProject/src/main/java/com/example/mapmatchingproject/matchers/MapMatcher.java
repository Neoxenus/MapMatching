package com.example.mapmatchingproject.matchers;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.RoadSegment;

import java.util.List;

public interface MapMatcher {
    void initContext(List<RoadSegment> segments);
    List<Point> match(List<Point> rawTrace);
    String getMatcherName();
}
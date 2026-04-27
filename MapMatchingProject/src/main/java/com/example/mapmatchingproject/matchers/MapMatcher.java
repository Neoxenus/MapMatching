package com.example.mapmatchingproject.matchers;

import com.example.mapmatchingproject.entities.Point;

import java.util.List;

public interface MapMatcher {
    List<Point> match(List<Point> rawTrace);
    String getMatcherName();
}
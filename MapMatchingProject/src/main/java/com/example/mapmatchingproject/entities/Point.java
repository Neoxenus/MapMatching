package com.example.mapmatchingproject.entities;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Point {
    public double lat;
    public double lon;

    public Point(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }
    public Point(Point point) {
        this.lat = point.getLat();
        this.lon = point.getLon();
    }
    public Point(Point point1, Point point2) {
        this.lat = (point1.getLat() + point2.getLat())/2;
        this.lon = (point1.getLon() + point2.getLon())/2;
    }
    public double distanceTo(Point other) {
        double dx = this.lat - other.lat;
        double dy = this.lon - other.lon;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return lat + "," + lon;
    }
}

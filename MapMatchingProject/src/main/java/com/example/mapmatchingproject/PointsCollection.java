package com.example.mapmatchingproject;

import lombok.Getter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class PointsCollection {
    private List<Point> pointList;

    public PointsCollection() {

        try {
            pointList = loadPointsFromCSV("src/main/resources/gps_points.csv");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public List<Point> getExtendedPointList(){
        List<Point> extended = new ArrayList<>();
        for (int i = 0; i < pointList.size(); i++) {
            extended.add(pointList.get(i));
            if(i == 0)
                continue;
            extended.add(new Point(pointList.get(i-1), pointList.get(i)));
        }
        return extended;
    }

    private static List<Point> loadPointsFromCSV(String filePath) throws IOException {
        List<Point> points = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            points.add(new Point(lat, lon));
        }
        reader.close();
        return points;
    }

    public Point getNorth(){
        Point point = pointList.stream().max((a, b) -> (int) (a.getLat() - b.getLat())).orElse(null);
        Point newPoint = new Point(point);
        newPoint.setLat(newPoint.getLat() + 0.5);
        return newPoint;
    }
    public Point getSouth(){
        Point point = pointList.stream().min((a, b) -> (int) (a.getLat() - b.getLat())).orElse(null);
        Point newPoint = new Point(point);
        newPoint.setLat(newPoint.getLat() - 0.5);
        return newPoint;
    }
    public Point getWest(){
        Point point = pointList.stream().min((a, b) -> (int) (a.getLon() - b.getLon())).orElse(null);
        Point newPoint = new Point(point);
        newPoint.setLon(newPoint.getLon() - 0.5);
        return newPoint;
    }
    public Point getEast(){
        Point point = pointList.stream().max((a, b) -> (int) (a.getLon() - b.getLon())).orElse(null);
        Point newPoint = new Point(point);
        newPoint.setLon(newPoint.getLon() + 0.5);
        return newPoint;
    }
}

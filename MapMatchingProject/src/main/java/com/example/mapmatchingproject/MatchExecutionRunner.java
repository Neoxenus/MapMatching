package com.example.mapmatchingproject;

import com.example.mapmatchingproject.clients.OverpassClient;
import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.PointsCollection;
import com.example.mapmatchingproject.entities.RoadSegment;
import com.example.mapmatchingproject.matchers.MapMatcher;
import com.example.mapmatchingproject.matchers.impl.EuclideanMatcher;
import com.example.mapmatchingproject.matchers.impl.HMMMapMatcher;
import com.example.mapmatchingproject.matchers.impl.OSRMMapMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchExecutionRunner implements CommandLineRunner {


    private final OverpassClient overpassClient;
    private final RestTemplate restTemplate;


    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Data initialisation ===");

        PointsCollection pointsCollection = new PointsCollection();
        List<Point> gpsPoints = pointsCollection.getPointList();

        double south = pointsCollection.getSouth().getLat();
        double west = pointsCollection.getWest().getLon();
        double north = pointsCollection.getNorth().getLat();
        double east = pointsCollection.getEast().getLon();

        System.out.println("Loading data from Overpass API...");
        List<Point> roadPoints = overpassClient.getRoadPointsFromOverpass(south, west, north, east);
        System.out.println("Loaded " + roadPoints.size() + " road points from Overpass API.");

        List<RoadSegment> segments = RoadSegment.buildSegmentsFromGeometry(overpassClient.getJSONFromOverpass(south, west, north, east));


        List<MapMatcher> matchers = new ArrayList<>();
        matchers.add(new EuclideanMatcher(segments));
        matchers.add(new HMMMapMatcher(segments));
        matchers.add(new OSRMMapMatcher(restTemplate));

        System.out.println("\n=== Running algorithms ===");

        for (MapMatcher matcher : matchers) {
            System.out.println("Starting: " + matcher.getMatcherName());

            long startTime = System.currentTimeMillis();
            List<Point> matchedPoints = matcher.match(gpsPoints);
            long timeTaken = System.currentTimeMillis() - startTime;

            System.out.println("Execution time " + matcher.getMatcherName() + ": " + timeTaken + " ms");

            String filename = matcher.getMatcherName() + ".html";
            MapGenerator.generateHtmlMap(gpsPoints, matchedPoints, filename);
        }

        System.out.println("\n=== All algorithms have been successfully finished ===");
    }
}
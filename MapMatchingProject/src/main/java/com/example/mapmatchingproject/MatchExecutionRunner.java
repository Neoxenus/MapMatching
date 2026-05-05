package com.example.mapmatchingproject;

import com.example.mapmatchingproject.clients.OverpassClient;
import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.PointsCollection;
import com.example.mapmatchingproject.entities.RoadSegment;
import com.example.mapmatchingproject.matchers.MapMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchExecutionRunner implements CommandLineRunner {


    private final OverpassClient overpassClient;

    private final List<MapMatcher> matchers;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Data initialisation ===");

        PointsCollection pointsCollection = new PointsCollection();
        List<Point> gpsPoints = pointsCollection.getPointList();

        double south = pointsCollection.getSouth().getLat();
        double west = pointsCollection.getWest().getLon();
        double north = pointsCollection.getNorth().getLat();
        double east = pointsCollection.getEast().getLon();

        List<RoadSegment> segments = RoadSegment.buildSegmentsFromGeometry(
                overpassClient.getJSONFromOverpass(south, west, north, east)
        );

        log.info("=== Running algorithms ===");

        for (MapMatcher matcher : matchers) {
            log.info("---- Starting: {} ----" , matcher.getMatcherName());

            matcher.initContext(segments);

            long startTime = System.currentTimeMillis();
            List<Point> matchedPoints = matcher.match(gpsPoints);
            long timeTaken = System.currentTimeMillis() - startTime;

            log.info("Execution time {}: {} ms", matcher.getMatcherName(), timeTaken);

            String filename = matcher.getMatcherName() + ".html";
            MapGenerator.generateHtmlMap(gpsPoints, matchedPoints, filename);
        }

        log.info("=== All algorithms have been successfully finished ===");
    }
}
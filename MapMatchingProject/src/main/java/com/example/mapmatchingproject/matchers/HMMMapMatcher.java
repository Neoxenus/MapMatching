package com.example.mapmatchingproject.matchers;

import com.example.mapmatchingproject.Util;
import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.RoadSegment;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.*;

public class HMMMapMatcher {

    // --- CONSTANTS ---
    private static final double SIGMA = 4.07;
    private static final double BETA = 10.0;
    private static final double SEARCH_RADIUS_M = 50.0;
    private static final double METERS_PER_DEGREE = 111000.0;

    private final RoutingService router;
    private final SpatialIndex spatialIndex;

    public HMMMapMatcher(List<RoadSegment> allSegments) {
        this.spatialIndex = new DefaultSpatialIndex(allSegments);
        // We use the Table Service now
        this.router = new OSRMTableRoutingService();
        System.out.println("[HMM] Initialized with " + allSegments.size() + " road segments.");
    }

    public List<Point> match(List<Point> gpsTrace) {
        if (gpsTrace.isEmpty()) return new ArrayList<>();

        long startTime = System.currentTimeMillis();
        System.out.println("[HMM] Starting match for " + gpsTrace.size() + " GPS points.");

        // 1. Get Candidates
        List<TimeStep> timeSteps = new ArrayList<>();
        for (int i = 0; i < gpsTrace.size(); i++) {
            Point p = gpsTrace.get(i);
            List<Candidate> candidates = spatialIndex.findCandidates(p, SEARCH_RADIUS_M);
            timeSteps.add(new TimeStep(p, candidates));
            System.out.printf("[HMM] Point %d: Found %d candidates within %.1fm%n", i, candidates.size(), SEARCH_RADIUS_M);
        }

        // 2. Initialize First Step
        Map<Candidate, Double> previousProbabilities = new HashMap<>();
        List<Map<Candidate, Candidate>> pathBackPointers = new ArrayList<>();

        TimeStep firstStep = timeSteps.get(0);
        for (Candidate candidate : firstStep.candidates) {
            double emissionProb = emissionProbability(firstStep.observation, candidate);
            previousProbabilities.put(candidate, Math.log(emissionProb));
        }

        // 3. Viterbi Forward Pass
        for (int t = 1; t < timeSteps.size(); t++) {
            TimeStep currentStep = timeSteps.get(t);
            TimeStep prevStep = timeSteps.get(t - 1);

            System.out.printf("[HMM] Step %d/%d: Batch fetching matrix %d x %d... ",
                    t, timeSteps.size() - 1, prevStep.candidates.size(), currentStep.candidates.size());

            // --- OPTIMIZATION: Fetch ALL distances in ONE call ---
            double[][] distanceMatrix = router.getDistanceMatrix(prevStep.candidates, currentStep.candidates);
            System.out.println("Done.");

            Map<Candidate, Double> currentProbabilities = new HashMap<>();
            Map<Candidate, Candidate> backPointer = new HashMap<>();

            double linearDist = distanceMeters(prevStep.observation, currentStep.observation);
            boolean anyPathFound = false;

            // Loop through Current Candidates (Columns in matrix)
            for (int currIdx = 0; currIdx < currentStep.candidates.size(); currIdx++) {
                Candidate currCand = currentStep.candidates.get(currIdx);
                double maxProb = Double.NEGATIVE_INFINITY;
                Candidate bestPrev = null;

                double emissionLog = Math.log(emissionProbability(currentStep.observation, currCand));

                // Loop through Previous Candidates (Rows in matrix)
                for (int prevIdx = 0; prevIdx < prevStep.candidates.size(); prevIdx++) {
                    Candidate prevCand = prevStep.candidates.get(prevIdx);

                    if (!previousProbabilities.containsKey(prevCand)) continue;

                    // LOOKUP from Matrix instead of API call
                    double routeDist = distanceMatrix[prevIdx][currIdx];

                    if (routeDist < 0) continue; // Invalid route

                    double transitionLog = Math.log(transitionProbability(linearDist, routeDist));
                    double totalProb = previousProbabilities.get(prevCand) + transitionLog + emissionLog;

                    if (totalProb > maxProb) {
                        maxProb = totalProb;
                        bestPrev = prevCand;
                    }
                }

                if (bestPrev != null) {
                    currentProbabilities.put(currCand, maxProb);
                    backPointer.put(currCand, bestPrev);
                    anyPathFound = true;
                }
            }

            if (!anyPathFound && !currentStep.candidates.isEmpty()) {
                System.out.println("[HMM] WARNING: Chain broken at step " + t + ". Restarting.");
                for (Candidate c : currentStep.candidates) {
                    currentProbabilities.put(c, Math.log(emissionProbability(currentStep.observation, c)));
                }
            }

            previousProbabilities = currentProbabilities;
            pathBackPointers.add(backPointer);
        }

        // 4. Backtrack
        List<Point> result = new ArrayList<>();
        Candidate bestLastState = null;
        double maxFinalProb = Double.NEGATIVE_INFINITY;

        for (Map.Entry<Candidate, Double> entry : previousProbabilities.entrySet()) {
            if (entry.getValue() > maxFinalProb) {
                maxFinalProb = entry.getValue();
                bestLastState = entry.getKey();
            }
        }

        Candidate currentParams = bestLastState;
        for (int t = timeSteps.size() - 1; t >= 0; t--) {
            if (currentParams != null) {
                result.add(0, currentParams.snappedPoint);
                if (t > 0 && t - 1 < pathBackPointers.size()) {
                    currentParams = pathBackPointers.get(t - 1).get(currentParams);
                }
            } else {
                Candidate fallback = getClosestCandidate(timeSteps.get(t));
                if (fallback != null) result.add(0, fallback.snappedPoint);
                else result.add(0, timeSteps.get(t).observation);
            }
        }

        System.out.println("[HMM] Finished. Total time: " + (System.currentTimeMillis() - startTime) + "ms");
        return result;
    }

    // --- Helpers ---

    private Candidate getClosestCandidate(TimeStep step) {
        if (step.candidates == null || step.candidates.isEmpty()) return null;
        Candidate best = null;
        double minDst = Double.MAX_VALUE;
        for (Candidate c : step.candidates) {
            double d = step.observation.distanceTo(c.snappedPoint);
            if (d < minDst) { minDst = d; best = c; }
        }
        return best;
    }

    private double emissionProbability(Point obs, Candidate candidate) {
        double dist = distanceMeters(obs, candidate.snappedPoint);
        return (1.0 / (Math.sqrt(2 * Math.PI) * SIGMA)) * Math.exp(-0.5 * Math.pow(dist / SIGMA, 2));
    }

    private double transitionProbability(double linearDist, double routeDist) {
        double diff = Math.abs(linearDist - routeDist);
        return (1.0 / BETA) * Math.exp(-diff / BETA);
    }

    private double distanceMeters(Point p1, Point p2) {
        return p1.distanceTo(p2) * METERS_PER_DEGREE;
    }

    // --- Inner Classes ---

    public static class Candidate {
        public Point snappedPoint;
        public RoadSegment segment;
        public Candidate(Point snappedPoint, RoadSegment segment) {
            this.snappedPoint = snappedPoint;
            this.segment = segment;
        }
    }

    private static class TimeStep {
        Point observation;
        List<Candidate> candidates;
        public TimeStep(Point observation, List<Candidate> candidates) {
            this.observation = observation;
            this.candidates = candidates;
        }
    }

    private interface RoutingService {
        double[][] getDistanceMatrix(List<Candidate> sources, List<Candidate> destinations);
    }

    private interface SpatialIndex {
        List<Candidate> findCandidates(Point p, double radiusMeters);
    }

    private class DefaultSpatialIndex implements SpatialIndex {
        private final List<RoadSegment> segments;
        public DefaultSpatialIndex(List<RoadSegment> segments) { this.segments = segments; }

        @Override
        public List<Candidate> findCandidates(Point p, double radiusMeters) {
            List<Candidate> results = new ArrayList<>();
            double radiusDegrees = radiusMeters / METERS_PER_DEGREE;
            for (RoadSegment seg : segments) {
                Point projected = seg.project(p);
                if (p.distanceTo(projected) <= radiusDegrees) {
                    results.add(new Candidate(projected, seg));
                }
            }
            return results;
        }
    }

    private class OSRMTableRoutingService implements RoutingService {
        private static final String OSRM_TABLE_URL = "http://router.project-osrm.org/table/v1/driving/";

        @Override
        public double[][] getDistanceMatrix(List<Candidate> sources, List<Candidate> destinations) {
            int rows = sources.size();
            int cols = destinations.size();
            double[][] matrix = new double[rows][cols];

            // Initialize with -1 (error state)
            for(double[] row : matrix) Arrays.fill(row, -1.0);

            if (rows == 0 || cols == 0) return matrix;

            try {
                // 1. Build Coordinates List (All unique sources + All unique destinations)
                StringBuilder coords = new StringBuilder();

                // Add Sources
                for (Candidate c : sources) {
                    coords.append(String.format(Locale.US, "%.6f,%.6f;", c.snappedPoint.lon, c.snappedPoint.lat));
                }
                // Add Destinations
                for (Candidate c : destinations) {
                    coords.append(String.format(Locale.US, "%.6f,%.6f;", c.snappedPoint.lon, c.snappedPoint.lat));
                }
                // Remove last semicolon
                coords.setLength(coords.length() - 1);

                // 2. Build Indices
                StringBuilder srcIndices = new StringBuilder();
                for (int i = 0; i < rows; i++) srcIndices.append(i).append(";");
                srcIndices.setLength(srcIndices.length() - 1);

                StringBuilder dstIndices = new StringBuilder();
                for (int i = 0; i < cols; i++) dstIndices.append(rows + i).append(";");
                dstIndices.setLength(dstIndices.length() - 1);

                // 3. Construct URL
                String urlStr = OSRM_TABLE_URL + coords +
                        "?sources=" + srcIndices +
                        "&destinations=" + dstIndices +
                        "&annotations=distance";

                // 4. Call API
                long start = System.currentTimeMillis();
                JSONObject response = Util.connect(new URL(urlStr));

                if (System.currentTimeMillis() - start > 1000) {
                    System.out.println("   [OSRM-TABLE] Slow batch call: " + (System.currentTimeMillis() - start) + "ms");
                }

                // 5. Parse Result
                if (response.getString("code").equals("Ok")) {
                    JSONArray distances = response.getJSONArray("distances");

                    // OSRM returns distances[source_index][dest_index]
                    for (int i = 0; i < rows; i++) {
                        JSONArray rowArr = distances.getJSONArray(i);
                        for (int j = 0; j < cols; j++) {
                            // Check for null (unreachable)
                            if (!rowArr.isNull(j)) {
                                matrix[i][j] = rowArr.getDouble(j);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("   [OSRM-TABLE-ERR] " + e.getMessage());
            }
            return matrix;
        }
    }
}
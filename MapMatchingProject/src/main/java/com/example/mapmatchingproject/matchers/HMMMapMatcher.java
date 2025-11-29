package com.example.mapmatchingproject.matchers;

import com.example.mapmatchingproject.entities.Point;
import com.example.mapmatchingproject.entities.RoadSegment;

import java.util.*;

/**
 * Fully local HMM Matcher.
 * - Removed OSRM API calls.
 * - Implemented internal Graph and Dijkstra pathfinding.
 */
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
        // NEW: Use the Graph-based local router
        this.router = new GraphRoutingService(allSegments);
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

            System.out.printf("[HMM] Step %d/%d: Computing local graph routes (%d x %d)... ",
                    t, timeSteps.size() - 1, prevStep.candidates.size(), currentStep.candidates.size());

            // Fetch matrix locally
            double[][] distanceMatrix = router.getDistanceMatrix(prevStep.candidates, currentStep.candidates);
            System.out.println("Done.");

            Map<Candidate, Double> currentProbabilities = new HashMap<>();
            Map<Candidate, Candidate> backPointer = new HashMap<>();

            double linearDist = distanceMeters(prevStep.observation, currentStep.observation);
            boolean anyPathFound = false;

            for (int currIdx = 0; currIdx < currentStep.candidates.size(); currIdx++) {
                Candidate currCand = currentStep.candidates.get(currIdx);
                double maxProb = Double.NEGATIVE_INFINITY;
                Candidate bestPrev = null;
                double emissionLog = Math.log(emissionProbability(currentStep.observation, currCand));

                for (int prevIdx = 0; prevIdx < prevStep.candidates.size(); prevIdx++) {
                    Candidate prevCand = prevStep.candidates.get(prevIdx);
                    if (!previousProbabilities.containsKey(prevCand)) continue;

                    double routeDist = distanceMatrix[prevIdx][currIdx];
                    if (routeDist < 0) continue;

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

    /**
     * LOCAL GRAPH ROUTING SERVICE
     * Implements Dijkstra's algorithm in-memory.
     */
    private class GraphRoutingService implements RoutingService {
        // Map from a unique Point ID (string) to a list of connected edges
        private final Map<String, List<Edge>> adjacencyList = new HashMap<>();
        // Helper to store actual Point objects for IDs
        private final Map<String, Point> nodeRegistry = new HashMap<>();

        public GraphRoutingService(List<RoadSegment> segments) {
            buildGraph(segments);
        }

        private void buildGraph(List<RoadSegment> segments) {
            for (RoadSegment seg : segments) {
                Point a = seg.a();
                Point b = seg.b();
                double weight = distanceMeters(a, b);

                String idA = getId(a);
                String idB = getId(b);

                nodeRegistry.putIfAbsent(idA, a);
                nodeRegistry.putIfAbsent(idB, b);

                adjacencyList.computeIfAbsent(idA, k -> new ArrayList<>()).add(new Edge(idB, weight));
                // Assuming undirected graph for roads (simplification)
                adjacencyList.computeIfAbsent(idB, k -> new ArrayList<>()).add(new Edge(idA, weight));
            }
        }

        private String getId(Point p) {
            // Round to ~1m precision to merge connected nodes
            return String.format(Locale.US, "%.5f,%.5f", p.lat, p.lon);
        }

        @Override
        public double[][] getDistanceMatrix(List<Candidate> sources, List<Candidate> destinations) {
            int rows = sources.size();
            int cols = destinations.size();
            double[][] matrix = new double[rows][cols];

            // For each source, calculate distance to all destinations
            for (int i = 0; i < rows; i++) {
                Candidate src = sources.get(i);

                // Optimization: Instead of full Dijkstra for every cell,
                // we run Dijkstra from the Source's segment endpoints once per source.
                Map<String, Double> distsFromA = runDijkstra(getId(src.segment.a()));
                Map<String, Double> distsFromB = runDijkstra(getId(src.segment.b()));

                double distSrcToA = distanceMeters(src.snappedPoint, src.segment.a());
                double distSrcToB = distanceMeters(src.snappedPoint, src.segment.b());

                for (int j = 0; j < cols; j++) {
                    Candidate dst = destinations.get(j);

                    if (src.segment.equals(dst.segment)) {
                        // Same segment: simple distance
                        matrix[i][j] = distanceMeters(src.snappedPoint, dst.snappedPoint);
                        continue;
                    }

                    String idDstA = getId(dst.segment.a());
                    String idDstB = getId(dst.segment.b());
                    double distDstToA = distanceMeters(dst.snappedPoint, dst.segment.a());
                    double distDstToB = distanceMeters(dst.snappedPoint, dst.segment.b());

                    // Find shortest path combination:
                    // (Src -> A -> ... -> DstA -> Dst)
                    // (Src -> A -> ... -> DstB -> Dst)
                    // (Src -> B -> ... -> DstA -> Dst)
                    // (Src -> B -> ... -> DstB -> Dst)

                    double d1 = getPathDist(distSrcToA, distsFromA, idDstA, distDstToA);
                    double d2 = getPathDist(distSrcToA, distsFromA, idDstB, distDstToB);
                    double d3 = getPathDist(distSrcToB, distsFromB, idDstA, distDstToA);
                    double d4 = getPathDist(distSrcToB, distsFromB, idDstB, distDstToB);

                    double min = Math.min(Math.min(d1, d2), Math.min(d3, d4));
                    matrix[i][j] = (min == Double.MAX_VALUE) ? -1.0 : min;
                }
            }
            return matrix;
        }

        private double getPathDist(double startOffset, Map<String, Double> graphDists, String targetNode, double endOffset) {
            Double graphDist = graphDists.get(targetNode);
            if (graphDist == null) return Double.MAX_VALUE;
            return startOffset + graphDist + endOffset;
        }

        private Map<String, Double> runDijkstra(String startId) {
            Map<String, Double> distances = new HashMap<>();
            PriorityQueue<PathNode> pq = new PriorityQueue<>(Comparator.comparingDouble(n -> n.dist));

            distances.put(startId, 0.0);
            pq.add(new PathNode(startId, 0.0));

            // Limit search range to improve performance (e.g., 2km)
            double MAX_SEARCH_DIST = 2000.0;

            while (!pq.isEmpty()) {
                PathNode current = pq.poll();

                if (current.dist > distances.getOrDefault(current.id, Double.MAX_VALUE)) continue;
                if (current.dist > MAX_SEARCH_DIST) continue;

                List<Edge> neighbors = adjacencyList.get(current.id);
                if (neighbors == null) continue;

                for (Edge edge : neighbors) {
                    double newDist = current.dist + edge.weight;
                    if (newDist < distances.getOrDefault(edge.target, Double.MAX_VALUE)) {
                        distances.put(edge.target, newDist);
                        pq.add(new PathNode(edge.target, newDist));
                    }
                }
            }
            return distances;
        }

        private record Edge(String target, double weight) {}
        private record PathNode(String id, double dist) {}
    }
}
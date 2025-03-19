package com.edtradeplanner.service;

import com.edtradeplanner.config.PlannerConfig;
import com.edtradeplanner.model.*;
import com.edtradeplanner.model.dto.RouteRequestDto;
import com.edtradeplanner.model.dto.RouteResponseDto;
import com.edtradeplanner.util.DistanceCalculator;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutePlannerService {
    
    private final SystemService systemService;
    private final CommodityService commodityService;
    private final FileService fileService;
    private final ChunkService chunkService;
    private final PlannerConfig plannerConfig;
    
    /**
     * Plan a route based on the provided request parameters.
     */
    @Async("routePlannerTaskExecutor")
    public CompletableFuture<RouteResponseDto> planRoute(RouteRequestDto requestDto) {
        try {
            // Process the uploaded file
            List<CommodityNeed> commodityNeeds = fileService.processCommodityNeedsFile(requestDto.getNeedsFile());
            
            // Determine coordinates of home system
            Coordinates homeCoords;
            String homeSystemName;
            
            if (requestDto.isUseCoordinates()) {
                homeCoords = new Coordinates(
                        requestDto.getHomeX(),
                        requestDto.getHomeY(),
                        requestDto.getHomeZ()
                );
                homeSystemName = String.format("Custom (%.1f, %.1f, %.1f)",
                        requestDto.getHomeX(), requestDto.getHomeY(), requestDto.getHomeZ());
            } else {
                homeCoords = systemService.getSystemCoordinates(requestDto.getHomeSystem());
                
                if (homeCoords.getX() == 0 && homeCoords.getY() == 0 && homeCoords.getZ() == 0 && 
                        (requestDto.getHomeSystem() == null || requestDto.getHomeSystem().isBlank())) {
                    // Default to Sol if no home system provided
                    homeSystemName = "Sol (default)";
                } else {
                    // Get original case of system name
                    String lowerCaseName = requestDto.getHomeSystem().trim().toLowerCase();
                    homeSystemName = systemService.getAllSystems().stream()
                            .filter(sys -> sys.getName().toLowerCase().equals(lowerCaseName))
                            .findFirst()
                            .map(StarSystem::getName)
                            .orElse(requestDto.getHomeSystem());
                }
            }
            
            // Get or build commodity map based on requested settings
            Map<String, List<StationInfo>> commodityMap = commodityService.getCommodityMap(
                    requestDto.isSkipCarriers(), 
                    requestDto.isLargePadOnly()
            );
            
            // Create chunks from commodity needs
            List<Chunk> chunks = chunkService.buildChunks(commodityNeeds, requestDto.getCargoCapacity());
            
            // Determine if we have economy data to enhance routing
            boolean useEconomyData = !commodityService.getCommodityStationEconomyMap().isEmpty();
            
            // Use economy-aware bin packing
            List<List<Chunk>> bins = chunkService.economyAwareBinPacking(
                    chunks,
                    requestDto.getCargoCapacity(),
                    useEconomyData ? commodityService.getCommodityStationEconomyMap() : null,
                    useEconomyData ? commodityService.getCommodityCoOccurrence() : null
            );
            
            String optimizationMethod = useEconomyData ? "economy-aware" : "basic";
            log.info("Using {} bin packing strategy", optimizationMethod);
            
            // Plan routes for each bin
            List<Route> routes = new ArrayList<>();
            double grandTotal = 0.0;
            
            for (int i = 0; i < bins.size(); i++) {
                List<Chunk> binChunks = bins.get(i);
                int binNumber = i + 1;
                
                // Plan economy-aware route for this bin

                RouteResult routeResult = planEconomyAwareRoute(
                    binChunks, 
                    commodityMap, 
                    homeCoords, 
                    requestDto.getMaxRange(),
                    useEconomyData ? commodityService.getCommodityStationEconomyMap() : null
                );
                
                // Create route object
                Route route = Route.builder()
                        .binNumber(binNumber)
                        .totalDistance(Math.round(routeResult.getTotalDistance() * 100.0) / 100.0)
                        .legs(routeResult.getLegs())
                        .build();
                
                routes.add(route);
                grandTotal += routeResult.getTotalDistance();
            }
            
            // Create home system info
            HomeSystem homeSystem = HomeSystem.builder()
                    .name(homeSystemName)
                    .coords(homeCoords)
                    .build();
            
            // Create optimization metadata
            Map<String, Object> optimization = Map.of("method", optimizationMethod);
            
            // Return the response
            return CompletableFuture.completedFuture(
                    RouteResponseDto.builder()
                            .success(true)
                            .routes(routes)
                            .totalDistance(Math.round(grandTotal * 100.0) / 100.0)
                            .shipCapacity(requestDto.getCargoCapacity())
                            .binsCount(bins.size())
                            .homeSystem(homeSystem)
                            .originalChunks(chunks)
                            .optimization(optimization)
                            .build()
            );
            
        } catch (IOException e) {
            log.error("Error processing commodity needs file", e);
            return CompletableFuture.completedFuture(
                    new RouteResponseDto(false, "Error processing commodity needs file: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error planning route", e);
            return CompletableFuture.completedFuture(
                    new RouteResponseDto(false, "Error planning route: " + e.getMessage())
            );
        }
    }
    
    /**
     * Plan an optimized route using station-economy awareness if available.
     */
    private RouteResult planEconomyAwareRoute(
            List<Chunk> binChunks, 
            Map<String, List<StationInfo>> commodityMap, 
            Coordinates homeCoords, 
            double maxRange,
            Map<String, Set<String>> commodityStationEconomyMap) {
        
        Set<String> neededCommodities = binChunks.stream()
                .map(Chunk::getCommodity)
                .collect(Collectors.toSet());
        
        List<RouteLeg> routeLegs = new ArrayList<>();
        Coordinates currentPos = homeCoords;
        double totalDist = 0.0;
        
        while (!neededCommodities.isEmpty()) {
            // Gather station coverage
            Map<StationKey, Set<String>> stationCandidates = new HashMap<>();
            
            for (String commodity : neededCommodities) {
                List<StationInfo> stationsForCommodity = commodityMap.getOrDefault(commodity, List.of());
                
                for (StationInfo stationInfo : stationsForCommodity) {
                    StationKey stationKey = new StationKey(
                            stationInfo.getSystem(),
                            stationInfo.getStation(),
                            stationInfo.getCoords(),
                            stationInfo.getPrefPenalty()
                    );
                    
                    stationCandidates.computeIfAbsent(stationKey, k -> new HashSet<>()).add(commodity);
                }
            }
            
            if (stationCandidates.isEmpty()) {
                // No station can supply these commodities
                for (String commodity : neededCommodities) {
                    routeLegs.add(RouteLeg.builder()
                            .startPos(currentPos)
                            .commodity(commodity)
                            .action("NO_STATION_FOUND")
                            .distance(0.0)
                            .build());
                }
                neededCommodities.clear();
                break;
            }
            
            // Find the best station
            Map.Entry<StationKey, Set<String>> bestStation = findBestStation(
                    stationCandidates, 
                    currentPos, 
                    maxRange,
                    neededCommodities,
                    commodityMap,
                    commodityStationEconomyMap
            );
            
            if (bestStation == null) {
                // All stations outside max range
                for (String commodity : neededCommodities) {
                    routeLegs.add(RouteLeg.builder()
                            .startPos(currentPos)
                            .commodity(commodity)
                            .action("NO_STATION_FOUND")
                            .distance(0.0)
                            .build());
                }
                neededCommodities.clear();
                break;
            }
            
            // Extract station info for the leg
            StationKey stationKey = bestStation.getKey();
            Set<String> coveredCommodities = bestStation.getValue();
            
            double travelDist = DistanceCalculator.distance(currentPos, stationKey.coords);
            
            // Format the commodities in a way the UI can parse
            String formattedCovset = "{" + String.join(", ", coveredCommodities) + "}";
            
            // Add the leg
            routeLegs.add(RouteLeg.builder()
                    .startPos(currentPos)
                    .endPos(stationKey.coords)
                    .systemName(stationKey.systemName)
                    .stationName(stationKey.stationName)
                    .commodity(formattedCovset)
                    .distance(Math.round(travelDist * 100.0) / 100.0)
                    .action("PICKUP_RATIO")
                    .build());
            
            // Update for next iteration
            currentPos = stationKey.coords;
            totalDist += travelDist;
            neededCommodities.removeAll(coveredCommodities);
        }
        
        // Return home
        double distHome = DistanceCalculator.distance(currentPos, homeCoords);
        routeLegs.add(RouteLeg.builder()
                .startPos(currentPos)
                .endPos(homeCoords)
                .commodity("RETURN_HOME")
                .distance(Math.round(distHome * 100.0) / 100.0)
                .action("RETURN")
                .build());
        
        totalDist += distHome;
        
        return new RouteResult(routeLegs, totalDist);
    }
    
    /**
     * Find the best station from a set of candidates based on various criteria.
     */
    private Map.Entry<StationKey, Set<String>> findBestStation(
            Map<StationKey, Set<String>> stationCandidates,
            Coordinates currentPos,
            double maxRange,
            Set<String> neededCommodities,
            Map<String, List<StationInfo>> commodityMap,
            Map<String, Set<String>> commodityStationEconomyMap) {
        
        Map.Entry<StationKey, Set<String>> bestStation = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        // Score all candidate stations
        for (Map.Entry<StationKey, Set<String>> entry : stationCandidates.entrySet()) {
            StationKey stationKey = entry.getKey();
            Set<String> coveredCommodities = entry.getValue();
            
            double stationDist = DistanceCalculator.distance(currentPos, stationKey.coords);
            
            // Skip stations beyond max range
            if (stationDist > maxRange) {
                continue;
            }
            
            // Base score is coverage / distance
            int coverageCount = coveredCommodities.size();
            double score = coverageCount / (stationDist + stationKey.prefPenalty + 1.0);
            
            // If economy data is available, enhance scoring
            if (commodityStationEconomyMap != null && !commodityStationEconomyMap.isEmpty()) {
                String systemName = stationKey.systemName;
                String stationName = stationKey.stationName;
                
                // Try to find the station entry with economy info
                for (String commodity : coveredCommodities) {
                    List<StationInfo> stationEntries = commodityMap.getOrDefault(commodity, List.of()).stream()
                            .filter(s -> s.getSystem().equals(systemName) && s.getStation().equals(stationName))
                            .collect(Collectors.toList());
                    
                    if (!stationEntries.isEmpty()) {
                        StationInfo station = stationEntries.get(0);
                        // Get station-economy combo
                        String stationEconomyCombo = station.getStationEconomyCombo();
                        
                        // Calculate how many needed commodities can be found at this station type
                        int comboMatchCount = 0;
                        for (String comm : neededCommodities) {
                            Set<String> stationEconomyCombos = commodityStationEconomyMap.getOrDefault(comm, Set.of());
                            if (stationEconomyCombos.contains(stationEconomyCombo)) {
                                comboMatchCount++;
                            }
                        }
                        
                        // Enhance score with economy matching
                        double matchRatio = (double) comboMatchCount / Math.max(1, neededCommodities.size());
                        score *= (1.0 + 0.5 * matchRatio);
                        break;
                    }
                }
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestStation = entry;
            }
        }
        
        return bestStation;
    }
    
    /**
     * Simple class to hold a route planning result.
     */
    @Value
    @AllArgsConstructor
    private static class RouteResult {
        List<RouteLeg> legs;
        double totalDistance;
    }
    
    /**
     * Simple class to use as a map key for stations.
     */
    @lombok.Value
    private static class StationKey {
        String systemName;
        String stationName;
        Coordinates coords;
        int prefPenalty;
    }
}
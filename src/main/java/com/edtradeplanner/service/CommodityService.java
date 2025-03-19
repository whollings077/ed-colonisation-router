package com.edtradeplanner.service;

import com.edtradeplanner.config.PlannerConfig;
import com.edtradeplanner.model.*;
import com.edtradeplanner.util.StationScoreUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommodityService {

    private final SystemService systemService;
    private final PlannerConfig plannerConfig;
    
    // Maps commodity names to lists of station info
    private Map<String, List<StationInfo>> commodityMap = new ConcurrentHashMap<>();
    
    // Maps commodities to station-economy combinations
    private Map<String, Set<String>> commodityStationEconomyMap = new ConcurrentHashMap<>();
    
    // Maps commodities to co-occurrence counts with other commodities
    private Map<String, Map<String, Integer>> commodityCoOccurrence = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        loadCommodityData();
    }
    
    /**
     * Load and initialize all commodity-related data
     */
    private void loadCommodityData() {
        try {
            // Build the commodity map with default settings
            buildCommodityMap(
                    systemService.getAllSystems(), 
                    plannerConfig.isSkipCarriersDefault(), 
                    plannerConfig.isLargePadOnlyDefault()
            );
            log.info("Built commodity map with {} commodities", commodityMap.size());
            
            // Load station-economy data
            loadCommodityEconomyMap();
            log.info("Loaded station-economy data for {} commodities", commodityStationEconomyMap.size());
            
            // Analyze commodity co-occurrence
            analyzeCommodityCoOccurrence();
            log.info("Analyzed co-occurrence patterns for commodities");
            
        } catch (Exception e) {
            log.error("Error initializing commodity data", e);
            commodityMap = new ConcurrentHashMap<>();
            commodityStationEconomyMap = new ConcurrentHashMap<>();
            commodityCoOccurrence = new ConcurrentHashMap<>();
        }
    }
    
    /**
     * Build a map of commodities to stations that sell them.
     */
    public Map<String, List<StationInfo>> buildCommodityMap(
            List<StarSystem> systemsList, 
            boolean skipCarriers, 
            boolean largePadOnly) {
        
        Map<String, List<StationInfo>> result = new ConcurrentHashMap<>();
        
        for (StarSystem sysData : systemsList) {
            double sx = sysData.getCoords().getX();
            double sy = sysData.getCoords().getY();
            double sz = sysData.getCoords().getZ();
            String systemName = sysData.getName();
            String systemEconomy = sysData.getEconomy();
            List<Station> stations = sysData.getStations();

            if (stations == null) {
                continue;
            }

            for (Station st : stations) {
                String stType = st.getType();
                
                // Skip carriers if requested
                if (skipCarriers && stType != null && 
                        (stType.toLowerCase().contains("carrier") || stType.toLowerCase().contains("drake"))) {
                    continue;
                }
                
                // Skip if large-pad-only is requested
                if (largePadOnly) {
                    Map<String, Integer> pads = st.getLandingPads();
                    if (pads == null || pads.getOrDefault("large", 0) < 1) {
                        continue;
                    }
                }

                int prefPen = StationScoreUtil.stationPreferencePenalty(stType);
                String stationName = st.getName();
                
                // Use station economy if available, fall back to system economy
                String stationEconomy = st.getEconomy() != null ? st.getEconomy() : systemEconomy;
                
                // Create combined station type + economy identity
                String stationEconomyCombo = stType + " " + stationEconomy;
                
                Market market = st.getMarket();
                if (market == null || market.getCommodities() == null) {
                    continue;
                }
                
                List<Commodity> comms = market.getCommodities();
                for (Commodity c : comms) {
                    String cName = c.getName();
                    int supply = c.getSupply();
                    
                    if (cName == null || cName.isEmpty() || supply < 1) {
                        continue;
                    }
                    
                    StationInfo entry = StationInfo.builder()
                            .system(systemName)
                            .station(stationName)
                            .coords(new Coordinates(sx, sy, sz))
                            .prefPenalty(prefPen)
                            .stationType(stType)
                            .economy(stationEconomy)
                            .stationEconomyCombo(stationEconomyCombo)
                            .build();
                    
                    result.computeIfAbsent(cName, k -> new ArrayList<>()).add(entry);
                }
            }
        }
        
        // Update the service's commodity map
        this.commodityMap = result;
        return result;
    }
    
    /**
     * Load and parse combined station-economy data.
     */
    private void loadCommodityEconomyMap() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(plannerConfig.getCommodityDataPath())) {
            if (is == null) {
                log.warn("Could not find commodity data file: {}", plannerConfig.getCommodityDataPath());
                return;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\t");
                    if (parts.length >= 3) {
                        String commodity = parts[0];
                        Set<String> stationEconomyCombos = Stream.of(parts[1].split(", "))
                                .collect(Collectors.toSet());
                        
                        commodityStationEconomyMap.put(commodity, stationEconomyCombos);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error loading commodity station-economy map", e);
        }
    }
    
    /**
     * Analyze which commodities frequently appear together at the same stations.
     */
    private void analyzeCommodityCoOccurrence() {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        
        for (StarSystem system : systemService.getAllSystems()) {
            if (system.getStations() == null) continue;
            
            for (Station station : system.getStations()) {
                if (station.getMarket() == null || station.getMarket().getCommodities() == null) continue;
                
                List<String> commodities = station.getMarket().getCommodities().stream()
                        .filter(c -> c.getSupply() > 0)
                        .map(Commodity::getName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                
                // Count co-occurrences
                for (int i = 0; i < commodities.size(); i++) {
                    String comm1 = commodities.get(i);
                    
                    for (int j = i + 1; j < commodities.size(); j++) {
                        String comm2 = commodities.get(j);
                        
                        result.computeIfAbsent(comm1, k -> new HashMap<>())
                                .compute(comm2, (k, v) -> v == null ? 1 : v + 1);
                        
                        result.computeIfAbsent(comm2, k -> new HashMap<>())
                                .compute(comm1, (k, v) -> v == null ? 1 : v + 1);
                    }
                }
            }
        }
        
        this.commodityCoOccurrence = result;
    }
    
    // Getters for maps
    public Map<String, List<StationInfo>> getCommodityMap() {
        return commodityMap;
    }
    
    public Map<String, List<StationInfo>> getCommodityMap(boolean skipCarriers, boolean largePadOnly) {
        // If requested settings match current map settings, return the existing map
        if (skipCarriers == plannerConfig.isSkipCarriersDefault() && 
            largePadOnly == plannerConfig.isLargePadOnlyDefault()) {
            return commodityMap;
        }
        
        // Otherwise, rebuild the map with the requested settings
        return buildCommodityMap(systemService.getAllSystems(), skipCarriers, largePadOnly);
    }
    
    public Map<String, Set<String>> getCommodityStationEconomyMap() {
        return commodityStationEconomyMap;
    }
    
    public Map<String, Map<String, Integer>> getCommodityCoOccurrence() {
        return commodityCoOccurrence;
    }
}
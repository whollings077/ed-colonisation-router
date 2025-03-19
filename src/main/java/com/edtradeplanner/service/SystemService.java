package com.edtradeplanner.service;

import com.edtradeplanner.config.PlannerConfig;
import com.edtradeplanner.model.Coordinates;
import com.edtradeplanner.model.StarSystem;  // Import renamed to StarSystem to avoid ambiguity
import com.edtradeplanner.model.dto.SystemSearchResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {
    
    private final ResourceLoader resourceLoader;
    private final PlannerConfig plannerConfig;
    private final ObjectMapper objectMapper;
    
    private List<StarSystem> systemsList = new ArrayList<>();
    private Map<String, Coordinates> systemCoordsMap = new HashMap<>();
    
    @PostConstruct
    public void initialize() {
        loadSystemData();
    }
    
    private void loadSystemData() {
        try {
            InputStream inputStream = resourceLoader.getResource("classpath:" + plannerConfig.getSystemsJsonPath()).getInputStream();
            systemsList = objectMapper.readValue(inputStream, new TypeReference<List<StarSystem>>() {});
            
            // Build system coordinates map
            systemCoordsMap = systemsList.stream()
                    .filter(system -> system.getName() != null && system.getCoords() != null)
                    .collect(Collectors.toMap(
                            system -> system.getName().trim().toLowerCase(),
                            StarSystem::getCoords,
                            (existing, replacement) -> existing
                    ));
            
            log.info("Loaded {} systems and built coordinates map with {} entries", 
                    systemsList.size(), systemCoordsMap.size());
            
        } catch (IOException e) {
            log.error("Error loading system data", e);
            systemsList = new ArrayList<>();
            systemCoordsMap = new HashMap<>();
        }
    }
    
    @Cacheable("systemSearch")
    public List<SystemSearchResponseDto> searchSystems(String query, int limit) {
        return systemsList.stream()
                .filter(system -> system.getName() != null && 
                        system.getName().toLowerCase().contains(query))
                .limit(limit)
                .map(system -> {
                    return new SystemSearchResponseDto(system.getName(), system.getCoords());
                })
                .collect(Collectors.toList());
    }
    
    public List<StarSystem> getAllSystems() {
        return systemsList;
    }
    
    public Coordinates getSystemCoordinates(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return new Coordinates(0, 0, 0); // Default to Sol
        }
        
        return systemCoordsMap.getOrDefault(
                systemName.trim().toLowerCase(), 
                new Coordinates(0, 0, 0)
        );
    }
}
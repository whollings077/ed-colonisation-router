package com.edtradeplanner.controller;

import com.edtradeplanner.config.PlannerConfig;
import com.edtradeplanner.model.dto.SystemSearchResponseDto;
import com.edtradeplanner.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/systems")
@RequiredArgsConstructor
public class SystemController {
    
    private final SystemService systemService;
    private final PlannerConfig plannerConfig;
    
    @GetMapping("/search")
    public ResponseEntity<List<SystemSearchResponseDto>> searchSystems(@RequestParam(name = "q", defaultValue = "") String query) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        
        log.debug("Searching for systems with query: {}", query);
        List<SystemSearchResponseDto> matchingSystems = systemService.searchSystems(
                query.trim().toLowerCase(), 
                plannerConfig.getMaxResultsLimit()
        );
        
        return ResponseEntity.ok(matchingSystems);
    }
}

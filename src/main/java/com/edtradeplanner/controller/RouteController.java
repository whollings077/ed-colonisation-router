package com.edtradeplanner.controller;

import com.edtradeplanner.config.PlannerConfig;
import com.edtradeplanner.model.dto.RouteRequestDto;
import com.edtradeplanner.model.dto.RouteResponseDto;
import com.edtradeplanner.service.RoutePlannerService;
import com.edtradeplanner.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RouteController {
    
    private final RoutePlannerService routePlannerService;
    private final PlannerConfig plannerConfig;
    
    @PostMapping(path = "/plan-route", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<RouteResponseDto>> planRoute(@ModelAttribute RouteRequestDto requestDto) {
        // Validate file
        MultipartFile file = requestDto.getNeedsFile();
        if (file == null || file.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(
                            new RouteResponseDto(false, "No needs file provided")
                    )
            );
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !FileUtil.isAllowedExtension(originalFilename, plannerConfig)) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(
                            new RouteResponseDto(false, "Invalid file format. Please upload a CSV file.")
                    )
            );
        }
        
        // Apply defaults from configuration if not provided
        if (requestDto.getCargoCapacity() <= 0) {
            requestDto.setCargoCapacity(plannerConfig.getDefaultCargoCapacity());
        }
        
        if (requestDto.getMaxRange() <= 0) {
            requestDto.setMaxRange(plannerConfig.getDefaultMaxRange());
        }
        
        // Apply defaults for optional boolean flags
        requestDto.setSkipCarriers(plannerConfig.isSkipCarriersDefault());
        requestDto.setLargePadOnly(plannerConfig.isLargePadOnlyDefault());
        
        log.info("Planning route from {} with cargo capacity: {}, max range: {}",
                requestDto.isUseCoordinates() ? "custom coordinates" : requestDto.getHomeSystem(),
                requestDto.getCargoCapacity(),
                requestDto.getMaxRange());
        
        return routePlannerService.planRoute(requestDto)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    log.error("Error planning route", ex);
                    return ResponseEntity.badRequest().body(
                            new RouteResponseDto(false, "Error planning route: " + ex.getMessage())
                    );
                });
    }
}
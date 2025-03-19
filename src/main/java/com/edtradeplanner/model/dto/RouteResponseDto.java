package com.edtradeplanner.model.dto;

import com.edtradeplanner.model.Chunk;
import com.edtradeplanner.model.HomeSystem;
import com.edtradeplanner.model.Route;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteResponseDto {
    private boolean success;
    private String error;
    private List<Route> routes;
    private double totalDistance;
    private int shipCapacity;
    private int binsCount;
    private HomeSystem homeSystem;
    private List<Chunk> originalChunks;
    private Map<String, Object> optimization;
    
    public RouteResponseDto(boolean success, String error) {
        this.success = success;
        this.error = error;
    }
}
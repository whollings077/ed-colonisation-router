package com.edtradeplanner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteLeg {
    private Coordinates startPos;
    private Coordinates endPos;
    private String systemName;
    private String stationName;
    private String commodity;
    private double distance;
    private String action;
}
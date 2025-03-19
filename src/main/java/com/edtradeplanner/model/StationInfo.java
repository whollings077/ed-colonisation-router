package com.edtradeplanner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationInfo {
    private String system;
    private String station;
    private Coordinates coords;
    private int prefPenalty;
    private String stationType;
    private String economy;
    private String stationEconomyCombo;
}
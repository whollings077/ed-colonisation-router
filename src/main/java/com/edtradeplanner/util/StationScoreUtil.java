package com.edtradeplanner.util;

public class StationScoreUtil {
    
    /**
     * Assign a station "penalty" based on station type
     */
    public static int stationPreferencePenalty(String stationType) {
        String stLower = stationType.toLowerCase();
        
        if (stLower.contains("starport")) {
            return 0;
        } else if (stLower.contains("asteroid")) {
            return 10;
        } else if (stLower.contains("planetary")) {
            return 20;
        } else if (stLower.contains("outpost")) {
            return 25;
        } else {
            return 15;
        }
    }
}
package com.edtradeplanner.util;

import com.edtradeplanner.model.Coordinates;

public class DistanceCalculator {
    
    /**
     * Calculate the Euclidean distance between two 3D points
     */
    public static double distance(Coordinates a, Coordinates b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Calculate the Euclidean distance between two 3D points
     */
    public static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

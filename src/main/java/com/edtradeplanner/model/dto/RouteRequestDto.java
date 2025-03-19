package com.edtradeplanner.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequestDto {
    private int cargoCapacity;
    private double maxRange;
    private String homeSystem;
    private boolean useCoordinates;
    private double homeX;
    private double homeY;
    private double homeZ;
    private boolean skipCarriers;
    private boolean largePadOnly;
    private MultipartFile needsFile;
}
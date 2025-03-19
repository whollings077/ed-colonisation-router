package com.edtradeplanner.model.dto;

import com.edtradeplanner.model.Coordinates;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSearchResponseDto {
    private String name;
    private Coordinates coords;
}
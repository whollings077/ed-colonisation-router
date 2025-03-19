// PlannerConfig.java
package com.edtradeplanner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "tradeplanner")
@Data
public class PlannerConfig {
    private String systemsJsonPath = "even_smaller_stations.json";
    private String commodityDataPath = "commodity_data.txt";
    private boolean skipCarriersDefault = true;
    private boolean largePadOnlyDefault = true;
    private int defaultCargoCapacity = 704;
    private double defaultMaxRange = 166.0;
    private int maxResultsLimit = 10;
    private List<String> allowedExtensions = List.of("csv");
    private String tempFilePrefix = "ed-trade-planner-";
    private String tempFileSuffix = ".tmp";
}






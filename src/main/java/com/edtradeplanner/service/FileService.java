package com.edtradeplanner.service;

import com.edtradeplanner.config.PlannerConfig;
import com.edtradeplanner.model.CommodityNeed;
import com.edtradeplanner.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    
    private final PlannerConfig plannerConfig;
    
    /**
     * Process the uploaded commodity needs file and extract commodity needs.
     */
    public List<CommodityNeed> processCommodityNeedsFile(MultipartFile file) throws IOException {
        File tempFile = null;
        try {
            tempFile = FileUtil.createSecureTempFile(file, plannerConfig);
            Map<String, Integer> needs = new HashMap<>();
            
            // Parse CSV file
            try (FileReader reader = new FileReader(tempFile);
                 CSVParser csvParser = new CSVParser(reader, CSVFormat.Builder.create()
                 .setHeader()
                 .setIgnoreHeaderCase(true)
                 .setTrim(true)
                 .build())) {
                
                for (CSVRecord record : csvParser) {
                    String commodityName = record.get("Commodity");
                    int quantity = Integer.parseInt(record.get("QuantityNeeded"));
                    
                    // Aggregate quantities for duplicate commodities
                    needs.put(commodityName, needs.getOrDefault(commodityName, 0) + quantity);
                }
            }
            
            // Convert to list of CommodityNeed objects
            List<CommodityNeed> result = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : needs.entrySet()) {
                if (entry.getValue() > 0) {
                    result.add(new CommodityNeed(entry.getKey(), entry.getValue()));
                }
            }
            
            log.info("Processed commodity needs file with {} unique commodities", result.size());
            return result;
            
        } finally {
            // Clean up the temporary file
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath(), e);
                }
            }
        }
    }
}


package com.edtradeplanner.util;

import com.edtradeplanner.config.PlannerConfig;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FileUtil {
    
    /**
     * Check if file extension is allowed
     */
    public static boolean isAllowedExtension(String filename, PlannerConfig config) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        return config.getAllowedExtensions().contains(extension);
    }
    
    /**
     * Create a secure temporary file
     */
    public static File createSecureTempFile(MultipartFile file, PlannerConfig config) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalFilename != null ? originalFilename : "");
        
        String securePrefix = config.getTempFilePrefix() + UUID.randomUUID().toString() + "-";
        String secureSuffix = config.getTempFileSuffix();
        
        Path tempFile = Files.createTempFile(securePrefix, secureSuffix);
        file.transferTo(tempFile.toFile());
        
        return tempFile.toFile();
    }
}
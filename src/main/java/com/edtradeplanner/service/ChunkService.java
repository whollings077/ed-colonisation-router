// ChunkService.java
package com.edtradeplanner.service;

import com.edtradeplanner.model.Chunk;
import com.edtradeplanner.model.CommodityNeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkService {
    
    /**
     * Split commodity needs into chunks of up to the specified capacity.
     */
    public List<Chunk> buildChunks(List<CommodityNeed> commodityNeeds, int capacity) {
        List<Chunk> chunks = new ArrayList<>();
        AtomicInteger binCounter = new AtomicInteger(0);
        
        // No bin assignment yet (will be done in bin packing)
        for (CommodityNeed need : commodityNeeds) {
            String commodityName = need.getCommodity();
            int quantity = need.getQuantityNeeded();
            
            if (quantity <= 0) {
                continue;
            }
            
            // Split large quantities into multiple chunks
            while (quantity > capacity) {
                chunks.add(new Chunk(commodityName, capacity, 0));
                quantity -= capacity;
            }
            
            if (quantity > 0) {
                chunks.add(new Chunk(commodityName, quantity, 0));
            }
        }
        
        log.debug("Built {} chunks from {} commodity needs", chunks.size(), commodityNeeds.size());
        return chunks;
    }
    
    /**
     * Enhanced bin packing that considers station-economy combinations and co-occurrence patterns.
     */
    public List<List<Chunk>> economyAwareBinPacking(
            List<Chunk> chunks, 
            int capacity,
            Map<String, Set<String>> commodityStationEconomyMap,
            Map<String, Map<String, Integer>> coOccurrence) {
        
        // Group chunks by commodity for faster access
        Map<String, List<Chunk>> commodityToChunks = chunks.stream()
                .collect(Collectors.groupingBy(Chunk::getCommodity));
        
        // Create bins and track remaining capacity
        List<List<Chunk>> bins = new ArrayList<>();
        List<Integer> leftover = new ArrayList<>();  // Track remaining capacity in each bin
        
        // Use economy grouping if available
        if (commodityStationEconomyMap != null && !commodityStationEconomyMap.isEmpty()) {
            Set<String> neededCommodities = chunks.stream()
                    .map(Chunk::getCommodity)
                    .collect(Collectors.toSet());
            
            // Group by most similar station-economy
            Map<String, Set<String>> commodityGroups = new HashMap<>();
            for (String commodity : neededCommodities) {
                if (commodityStationEconomyMap.containsKey(commodity)) {
                    // Create a group ID based on the first economy-station combo
                    Set<String> combos = commodityStationEconomyMap.get(commodity);
                    if (!combos.isEmpty()) {
                        String groupId = combos.iterator().next();
                        commodityGroups.computeIfAbsent(groupId, k -> new HashSet<>()).add(commodity);
                    }
                }
            }
            
            // Process each group by size (largest first)
            List<Map.Entry<String, Set<String>>> sortedGroups = commodityGroups.entrySet().stream()
                    .sorted((e1, e2) -> {
                        int size1 = e1.getValue().stream()
                                .flatMap(c -> commodityToChunks.getOrDefault(c, List.of()).stream())
                                .mapToInt(Chunk::getSize)
                                .sum();
                        
                        int size2 = e2.getValue().stream()
                                .flatMap(c -> commodityToChunks.getOrDefault(c, List.of()).stream())
                                .mapToInt(Chunk::getSize)
                                .sum();
                        
                        return Integer.compare(size2, size1);  // Largest first
                    })
                    .collect(Collectors.toList());
            
            // Place chunks by group
            for (Map.Entry<String, Set<String>> groupEntry : sortedGroups) {
                Set<String> commodities = groupEntry.getValue();
                
                // Gather and sort chunks for this group
                List<Chunk> groupChunks = commodities.stream()
                        .flatMap(c -> commodityToChunks.getOrDefault(c, List.of()).stream())
                        .sorted(Comparator.comparing(Chunk::getSize).reversed())  // Largest first
                        .collect(Collectors.toList());
                
                // Place each chunk in the best bin
                for (Chunk chunk : groupChunks) {
                    placeChunkInBestBin(chunk, bins, leftover, capacity, coOccurrence);
                }
            }
            
            // Process remaining chunks that weren't in any group
            Set<String> processedCommodities = commodityGroups.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
            
            Set<String> remainingCommodities = new HashSet<>(neededCommodities);
            remainingCommodities.removeAll(processedCommodities);
            
            List<Chunk> remainingChunks = remainingCommodities.stream()
                    .flatMap(c -> commodityToChunks.getOrDefault(c, List.of()).stream())
                    .sorted(Comparator.comparing(Chunk::getSize).reversed())
                    .collect(Collectors.toList());
            
            // Place remaining chunks
            for (Chunk chunk : remainingChunks) {
                placeChunkInBestBin(chunk, bins, leftover, capacity, coOccurrence);
            }
            
        } else {
            // Simple approach without economy data - just process chunks by size (largest first)
            chunks.stream()
                    .sorted(Comparator.comparing(Chunk::getSize).reversed())
                    .forEach(chunk -> placeChunkInBestBin(chunk, bins, leftover, capacity, coOccurrence));
        }
        
        // Assign bin numbers to chunks
        for (int i = 0; i < bins.size(); i++) {
            int binNumber = i + 1;
            for (Chunk chunk : bins.get(i)) {
                chunk.setBin(binNumber);
            }
        }
        
        log.info("Packed {} chunks into {} bins", chunks.size(), bins.size());
        return bins;
    }
    
    /**
     * Helper method to place a chunk in the best bin.
     */
    private void placeChunkInBestBin(
            Chunk chunk, 
            List<List<Chunk>> bins, 
            List<Integer> leftover, 
            int capacity,
            Map<String, Map<String, Integer>> coOccurrence) {
        
        int bestBinIdx = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        // Try to find the best bin for this chunk
        for (int binIdx = 0; binIdx < bins.size(); binIdx++) {
            List<Chunk> binChunks = bins.get(binIdx);
            int remainingSpace = leftover.get(binIdx);
            
            if (remainingSpace < chunk.getSize()) {
                continue;  // Skip bins that don't have enough space
            }
            
            // Basic bin fitting score - higher if chunk fits well
            double fitScore = 1.0 - ((double) chunk.getSize() / remainingSpace);
            
            // Co-occurrence score if data is available
            double coScore = 0;
            if (coOccurrence != null) {
                for (Chunk binChunk : binChunks) {
                    String binCommodity = binChunk.getCommodity();
                    String chunkCommodity = chunk.getCommodity();
                    
                    Map<String, Integer> coOccurrences = coOccurrence.get(binCommodity);
                    if (coOccurrences != null) {
                        coScore += coOccurrences.getOrDefault(chunkCommodity, 0);
                    }
                }
            }
            
            // Final score is weighted combination (or just fit score if no co-occurrence data)
            double totalScore = fitScore;
            if (coScore > 0) {
                totalScore = (coScore * 0.7) + (fitScore * 0.3);
            }
            
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestBinIdx = binIdx;
            }
        }
        
        // If no bin is found, create a new one
        if (bestBinIdx == -1) {
            List<Chunk> newBin = new ArrayList<>();
            newBin.add(chunk);
            bins.add(newBin);
            leftover.add(capacity - chunk.getSize());
        } else {
            bins.get(bestBinIdx).add(chunk);
            leftover.set(bestBinIdx, leftover.get(bestBinIdx) - chunk.getSize());
        }
    }
}
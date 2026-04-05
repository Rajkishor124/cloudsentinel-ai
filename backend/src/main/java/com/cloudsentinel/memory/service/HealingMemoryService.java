package com.cloudsentinel.memory.service;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.detector.model.AnomalyAlert.AnomalyType;
import com.cloudsentinel.feedback.model.FeedbackEntry;
import com.cloudsentinel.memory.model.HealingMemoryEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Persistent memory system for the self-healing AI.
 * Stores healing experiences and retrieves relevant past episodes
 * to inform current decisions.
 */
@Slf4j
@Service
public class HealingMemoryService {

    // In-memory storage (would be database/Redis in production)
    private final List<HealingMemoryEntry> memoryStore = new ArrayList<>();
    private final Map<AnomalyType, List<HealingMemoryEntry>> indexedByAnomaly = new ConcurrentHashMap<>();
    private final Map<ActionType, List<HealingMemoryEntry>> indexedByAction = new ConcurrentHashMap<>();

    // Maximum memory size (LRU eviction)
    private static final int MAX_MEMORY_SIZE = 1000;

    /**
     * Store a healing experience in memory.
     */
    public HealingMemoryEntry storeExperience(FeedbackEntry feedback, String difficulty) {
        HealingMemoryEntry entry = new HealingMemoryEntry();

        entry.setDifficulty(difficulty);
        for (AnomalyType anomaly : feedback.getDetectedAnomalies()) {
            entry.addAnomaly(anomaly);
        }
        entry.setSystemHealthBefore(feedback.getBeforeSystemHealth());
        entry.setSystemHealthAfter(feedback.getAfterSystemHealth());
        entry.setSloScoreBefore(feedback.getBeforeSloScore());
        entry.setSloScoreAfter(feedback.getAfterSloScore());
        entry.setActionTaken(feedback.getTakenAction());
        entry.setDecisionSource(feedback.getDecisionSource());
        entry.setActionEffective(feedback.isEffective());
        entry.setEffectivenessScore(feedback.getEffectivenessScore());
        entry.setReward(feedback.getReward());

        // Generate lesson
        entry.generateLesson();

        // Store and index
        memoryStore.add(entry);

        // Index by anomaly
        for (AnomalyType anomaly : entry.getAnomalies()) {
            indexedByAnomaly.computeIfAbsent(anomaly, k -> new ArrayList<>()).add(entry);
        }

        // Index by action
        indexedByAction.computeIfAbsent(entry.getActionTaken(), k -> new ArrayList<>()).add(entry);

        // Evict oldest if over limit
        if (memoryStore.size() > MAX_MEMORY_SIZE) {
            HealingMemoryEntry oldest = memoryStore.remove(0);
            removeFromIndexes(oldest);
            log.debug("Evicted oldest memory entry: {}", oldest.getEpisodeId());
        }

        log.info("Stored healing memory: {} (effective={}, score={:.2f})",
                entry.getEpisodeId(), entry.isActionEffective(), entry.getEffectivenessScore());

        return entry;
    }

    /**
     * Find similar past experiences for the current anomaly.
     * Returns entries sorted by effectiveness score (best first).
     */
    public List<HealingMemoryEntry> findSimilarExperiences(AnomalyType currentAnomaly, int limit) {
        List<HealingMemoryEntry> similar = indexedByAnomaly.getOrDefault(currentAnomaly, new ArrayList<>());

        return similar.stream()
                .sorted(Comparator.comparingDouble(HealingMemoryEntry::getEffectivenessScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get the most effective action for a given anomaly based on historical data.
     */
    public ActionType getBestHistoricalAction(AnomalyType anomaly) {
        List<HealingMemoryEntry> experiences = findSimilarExperiences(anomaly, 50);

        if (experiences.isEmpty()) {
            return null;
        }

        // Group by action and compute average effectiveness
        Map<ActionType, List<HealingMemoryEntry>> byAction = experiences.stream()
                .collect(Collectors.groupingBy(HealingMemoryEntry::getActionTaken));

        return byAction.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(),
                        entry.getValue().stream()
                                .mapToDouble(HealingMemoryEntry::getEffectivenessScore)
                                .average().orElse(0.0)))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Get successful actions for an anomaly (effectiveness > threshold).
     */
    public List<ActionType> getSuccessfulActions(AnomalyType anomaly, double minEffectiveness) {
        List<HealingMemoryEntry> experiences = findSimilarExperiences(anomaly, 100);

        return experiences.stream()
                .filter(e -> e.isActionEffective() && e.getEffectivenessScore() >= minEffectiveness)
                .map(HealingMemoryEntry::getActionTaken)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Get lessons learned for a specific anomaly type.
     */
    public List<String> getLessonsForAnomaly(AnomalyType anomaly) {
        return findSimilarExperiences(anomaly, 10).stream()
                .map(HealingMemoryEntry::getLesson)
                .collect(Collectors.toList());
    }

    /**
     * Get overall memory statistics.
     */
    public MemoryStatistics getStatistics() {
        MemoryStatistics stats = new MemoryStatistics();
        stats.totalMemories = memoryStore.size();
        stats.uniqueAnomalies = indexedByAnomaly.size();
        stats.uniqueActions = indexedByAction.size();

        if (!memoryStore.isEmpty()) {
            stats.averageEffectiveness = memoryStore.stream()
                    .mapToDouble(HealingMemoryEntry::getEffectivenessScore)
                    .average().orElse(0.0);

            stats.successRate = (double) memoryStore.stream()
                    .filter(HealingMemoryEntry::isActionEffective)
                    .count() / memoryStore.size();
        }

        return stats;
    }

    /**
     * Get recent memories (for analysis).
     */
    public List<HealingMemoryEntry> getRecentMemories(int limit) {
        int size = memoryStore.size();
        return memoryStore.subList(Math.max(0, size - limit), size);
    }

    /**
     * Clear all memories (for testing).
     */
    public void clear() {
        memoryStore.clear();
        indexedByAnomaly.clear();
        indexedByAction.clear();
        log.info("Healing memory cleared");
    }

    private void removeFromIndexes(HealingMemoryEntry entry) {
        for (AnomalyType anomaly : entry.getAnomalies()) {
            List<HealingMemoryEntry> list = indexedByAnomaly.get(anomaly);
            if (list != null) {
                list.remove(entry);
            }
        }
        List<HealingMemoryEntry> list = indexedByAction.get(entry.getActionTaken());
        if (list != null) {
            list.remove(entry);
        }
    }

    /**
     * Memory statistics.
     */
    public static class MemoryStatistics {
        public int totalMemories = 0;
        public int uniqueAnomalies = 0;
        public int uniqueActions = 0;
        public double averageEffectiveness = 0.0;
        public double successRate = 0.0;
    }
}

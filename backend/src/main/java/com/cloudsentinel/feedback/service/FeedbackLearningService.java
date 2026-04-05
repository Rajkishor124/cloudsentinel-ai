package com.cloudsentinel.feedback.service;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.decision.model.Decision;
import com.cloudsentinel.detector.model.AnomalyAlert;
import com.cloudsentinel.detector.model.AnomalyAlert.AnomalyType;
import com.cloudsentinel.executor.model.ActionExecutionRecord;
import com.cloudsentinel.feedback.model.FeedbackEntry;
import com.cloudsentinel.simulator.model.InfrastructureState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages feedback learning: tracks before/after states, computes effectiveness,
 * and builds action effectiveness statistics for continuous learning.
 */
@Slf4j
@Service
public class FeedbackLearningService {

    // Persistent feedback history (in-memory for now, would be database in production)
    private final List<FeedbackEntry> feedbackHistory = new ArrayList<>();

    // Action effectiveness statistics
    private final Map<ActionType, ActionEffectivenessStats> actionStats = new ConcurrentHashMap<>();

    /**
     * Create a feedback entry from a decision and execution.
     */
    public FeedbackEntry recordFeedback(
            Decision decision,
            List<AnomalyAlert> alerts,
            ActionExecutionRecord execution,
            InfrastructureState stateAfter,
            double reward) {

        FeedbackEntry entry = new FeedbackEntry();

        // Capture anomalies
        for (AnomalyAlert alert : alerts) {
            entry.addDetectedAnomaly(alert.getAnomalyType());
        }

        // Before state (from execution record)
        entry.setBeforeSystemHealth(execution.getBeforeHealth());
        entry.setBeforeFailureCount(countFailuresFromAlerts(alerts));

        // Decision info
        entry.setTakenAction(decision.getRecommendedAction());
        entry.setDecisionSource(decision.getSource().name());
        entry.setDecisionConfidence(decision.getConfidence());

        // After state
        entry.setAfterSystemHealth(stateAfter.getAverageHealth());
        entry.setAfterSloScore(stateAfter.getSloScore());
        entry.setAfterFailureCount(countActiveFailures(stateAfter));
        entry.setActionCost(execution.getCostIncurred());
        entry.setReward(reward);

        // Compute effectiveness
        entry.computeEffectiveness();

        // Update action statistics
        updateActionStats(entry);

        // Store in history
        feedbackHistory.add(entry);

        log.info("Feedback recorded: {} (effective={}, score={:.2f})",
                entry.getTakenAction(), entry.isEffective(), entry.getEffectivenessScore());

        return entry;
    }

    /**
     * Get effectiveness statistics for an action type.
     */
    public ActionEffectivenessStats getActionStats(ActionType action) {
        return actionStats.computeIfAbsent(action, k -> new ActionEffectivenessStats(action));
    }

    /**
     * Get the most effective action for a given anomaly type based on historical data.
     */
    public ActionType getBestActionForAnomaly(AnomalyType anomalyType) {
        return actionStats.values().stream()
                .filter(stats -> stats.totalExecutions > 0)
                .filter(stats -> stats.anomalyEffectiveness.containsKey(anomalyType))
                .max((a, b) -> Double.compare(
                        a.anomalyEffectiveness.getOrDefault(anomalyType, 0.0),
                        b.anomalyEffectiveness.getOrDefault(anomalyType, 0.0)))
                .map(stats -> stats.action)
                .orElse(null);
    }

    /**
     * Get all action statistics for dashboard display.
     */
    public List<ActionEffectivenessStats> getAllActionStats() {
        return new ArrayList<>(actionStats.values());
    }

    /**
     * Get recent feedback entries (for analysis).
     */
    public List<FeedbackEntry> getRecentFeedback(int limit) {
        int size = feedbackHistory.size();
        return feedbackHistory.subList(Math.max(0, size - limit), size);
    }

    /**
     * Get overall learning metrics.
     */
    public LearningMetrics getLearningMetrics() {
        LearningMetrics metrics = new LearningMetrics();
        metrics.totalEpisodes = feedbackHistory.size();

        if (metrics.totalEpisodes == 0) {
            return metrics;
        }

        long effectiveCount = feedbackHistory.stream().filter(FeedbackEntry::isEffective).count();
        metrics.overallEffectivenessRate = (double) effectiveCount / metrics.totalEpisodes;
        metrics.averageEffectivenessScore = feedbackHistory.stream()
                .mapToDouble(FeedbackEntry::getEffectivenessScore)
                .average().orElse(0.0);
        metrics.averageReward = feedbackHistory.stream()
                .mapToDouble(FeedbackEntry::getReward)
                .average().orElse(0.0);

        return metrics;
    }

    /**
     * Update action statistics with new feedback.
     */
    private void updateActionStats(FeedbackEntry entry) {
        ActionEffectivenessStats stats = actionStats.computeIfAbsent(
                entry.getTakenAction(), k -> new ActionEffectivenessStats(entry.getTakenAction()));

        stats.totalExecutions++;
        if (entry.isEffective()) {
            stats.effectiveExecutions++;
        }
        stats.totalCost += entry.getActionCost();
        stats.totalHealthDelta += entry.getHealthDelta();

        // Update per-anomaly effectiveness
        for (AnomalyType anomaly : entry.getDetectedAnomalies()) {
            double currentScore = stats.anomalyEffectiveness.getOrDefault(anomaly, 0.0);
            int currentCount = stats.anomalyCount.getOrDefault(anomaly, 0);
            stats.anomalyEffectiveness.put(anomaly,
                    (currentScore * currentCount + entry.getEffectivenessScore()) / (currentCount + 1));
            stats.anomalyCount.put(anomaly, currentCount + 1);
        }

        // Update recent trend (last 10)
        stats.recentEffectiveness.add(entry.getEffectivenessScore());
        if (stats.recentEffectiveness.size() > 10) {
            stats.recentEffectiveness.remove(0);
        }
    }

    private int countFailuresFromAlerts(List<AnomalyAlert> alerts) {
        return (int) alerts.stream()
                .map(AnomalyAlert::getServiceId)
                .distinct()
                .count();
    }

    private int countActiveFailures(InfrastructureState state) {
        if (state.getTopology() == null) return 0;
        return (int) state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != com.cloudsentinel.simulator.model.FailureMode.HEALTHY)
                .count();
    }

    /**
     * Statistics for a single action type.
     */
    public static class ActionEffectivenessStats {
        public final ActionType action;
        public int totalExecutions = 0;
        public int effectiveExecutions = 0;
        public double totalCost = 0.0;
        public double totalHealthDelta = 0.0;
        public final Map<AnomalyType, Double> anomalyEffectiveness = new ConcurrentHashMap<>();
        public final Map<AnomalyType, Integer> anomalyCount = new ConcurrentHashMap<>();
        public final List<Double> recentEffectiveness = new ArrayList<>();

        public ActionEffectivenessStats(ActionType action) {
            this.action = action;
        }

        public double getEffectivenessRate() {
            return totalExecutions > 0 ? (double) effectiveExecutions / totalExecutions : 0.0;
        }

        public double getAverageCost() {
            return totalExecutions > 0 ? totalCost / totalExecutions : 0.0;
        }

        public double getAverageHealthDelta() {
            return totalExecutions > 0 ? totalHealthDelta / totalExecutions : 0.0;
        }

        public double getRecentTrend() {
            if (recentEffectiveness.isEmpty()) return 0.0;
            return recentEffectiveness.stream().mapToDouble(d -> d).average().orElse(0.0);
        }
    }

    /**
     * Overall learning metrics.
     */
    public static class LearningMetrics {
        public int totalEpisodes = 0;
        public double overallEffectivenessRate = 0.0;
        public double averageEffectivenessScore = 0.0;
        public double averageReward = 0.0;
    }
}

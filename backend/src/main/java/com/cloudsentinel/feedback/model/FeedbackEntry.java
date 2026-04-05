package com.cloudsentinel.feedback.model;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.detector.model.AnomalyAlert.AnomalyType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks a single learning episode: before state, action taken, after state, and effectiveness.
 */
public class FeedbackEntry {
    private String episodeId;
    private Instant timestamp;
    private int tick;

    // Before state
    private List<AnomalyType> detectedAnomalies = new ArrayList<>();
    private double beforeSystemHealth;
    private double beforeSloScore;
    private int beforeFailureCount;

    // Decision
    private ActionType takenAction;
    private String decisionSource;  // RL, RULE, HYBRID
    private double decisionConfidence;

    // After state
    private double afterSystemHealth;
    private double afterSloScore;
    private int afterFailureCount;
    private double actionCost;

    // Effectiveness
    private double healthDelta;
    private double sloDelta;
    private int failuresResolved;
    private int failuresIntroduced;
    private boolean effective;
    private String effectivenessReason;

    // Learning
    private double reward;
    private double effectivenessScore;  // 0.0 - 1.0
    private boolean shouldRepeatAction;  // Would we do this again?

    public FeedbackEntry() {
        this.timestamp = Instant.now();
        this.episodeId = "FB-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    // --- Getters and Setters ---

    public String getEpisodeId() { return episodeId; }
    public void setEpisodeId(String episodeId) { this.episodeId = episodeId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public int getTick() { return tick; }
    public void setTick(int tick) { this.tick = tick; }

    public List<AnomalyType> getDetectedAnomalies() { return detectedAnomalies; }
    public void setDetectedAnomalies(List<AnomalyType> detectedAnomalies) { this.detectedAnomalies = detectedAnomalies; }

    public void addDetectedAnomaly(AnomalyType anomaly) { this.detectedAnomalies.add(anomaly); }

    public double getBeforeSystemHealth() { return beforeSystemHealth; }
    public void setBeforeSystemHealth(double beforeSystemHealth) { this.beforeSystemHealth = beforeSystemHealth; }

    public double getBeforeSloScore() { return beforeSloScore; }
    public void setBeforeSloScore(double beforeSloScore) { this.beforeSloScore = beforeSloScore; }

    public int getBeforeFailureCount() { return beforeFailureCount; }
    public void setBeforeFailureCount(int beforeFailureCount) { this.beforeFailureCount = beforeFailureCount; }

    public ActionType getTakenAction() { return takenAction; }
    public void setTakenAction(ActionType takenAction) { this.takenAction = takenAction; }

    public String getDecisionSource() { return decisionSource; }
    public void setDecisionSource(String decisionSource) { this.decisionSource = decisionSource; }

    public double getDecisionConfidence() { return decisionConfidence; }
    public void setDecisionConfidence(double decisionConfidence) { this.decisionConfidence = decisionConfidence; }

    public double getAfterSystemHealth() { return afterSystemHealth; }
    public void setAfterSystemHealth(double afterSystemHealth) { this.afterSystemHealth = afterSystemHealth; }

    public double getAfterSloScore() { return afterSloScore; }
    public void setAfterSloScore(double afterSloScore) { this.afterSloScore = afterSloScore; }

    public int getAfterFailureCount() { return afterFailureCount; }
    public void setAfterFailureCount(int afterFailureCount) { this.afterFailureCount = afterFailureCount; }

    public double getActionCost() { return actionCost; }
    public void setActionCost(double actionCost) { this.actionCost = actionCost; }

    public double getHealthDelta() { return healthDelta; }
    public void setHealthDelta(double healthDelta) { this.healthDelta = healthDelta; }

    public double getSloDelta() { return sloDelta; }
    public void setSloDelta(double sloDelta) { this.sloDelta = sloDelta; }

    public int getFailuresResolved() { return failuresResolved; }
    public void setFailuresResolved(int failuresResolved) { this.failuresResolved = failuresResolved; }

    public int getFailuresIntroduced() { return failuresIntroduced; }
    public void setFailuresIntroduced(int failuresIntroduced) { this.failuresIntroduced = failuresIntroduced; }

    public boolean isEffective() { return effective; }
    public void setEffective(boolean effective) { this.effective = effective; }

    public String getEffectivenessReason() { return effectivenessReason; }
    public void setEffectivenessReason(String effectivenessReason) { this.effectivenessReason = effectivenessReason; }

    public double getReward() { return reward; }
    public void setReward(double reward) { this.reward = reward; }

    public double getEffectivenessScore() { return effectivenessScore; }
    public void setEffectivenessScore(double effectivenessScore) { this.effectivenessScore = effectivenessScore; }

    public boolean isShouldRepeatAction() { return shouldRepeatAction; }
    public void setShouldRepeatAction(boolean shouldRepeatAction) { this.shouldRepeatAction = shouldRepeatAction; }

    /**
     * Compute effectiveness metrics after action completion.
     */
    public void computeEffectiveness() {
        this.healthDelta = afterSystemHealth - beforeSystemHealth;
        this.sloDelta = afterSloScore - beforeSloScore;
        this.failuresResolved = Math.max(0, beforeFailureCount - afterFailureCount);
        this.failuresIntroduced = Math.max(0, afterFailureCount - beforeFailureCount);

        // Effective if health improved OR failures reduced
        this.effective = healthDelta > 0.01 || failuresResolved > failuresIntroduced;

        // Effectiveness score: weighted combination
        double healthComponent = Math.max(0, Math.min(1, (healthDelta + 1) / 2));  // Normalize to [0,1]
        double sloComponent = Math.max(0, Math.min(1, sloDelta + 0.5));  // Shift to [0,1]
        double failureComponent = beforeFailureCount > 0 ?
                (double) failuresResolved / beforeFailureCount : 0.5;

        this.effectivenessScore = healthComponent * 0.4 + sloComponent * 0.2 + failureComponent * 0.4;
        this.effectivenessScore = Math.max(0, Math.min(1, this.effectivenessScore));

        // Should repeat if effective and not too costly
        this.shouldRepeatAction = effective && actionCost < 0.5;

        // Reason
        if (healthDelta > 0.05) {
            this.effectivenessReason = String.format("Health improved by %.1f%%, %d failures resolved",
                    healthDelta * 100, failuresResolved);
        } else if (failuresResolved > 0) {
            this.effectivenessReason = String.format("Resolved %d failures (health delta: %.2f)",
                    failuresResolved, healthDelta);
        } else if (healthDelta < -0.05) {
            this.effectivenessReason = String.format("Health degraded by %.1f%%, action may be harmful",
                    -healthDelta * 100);
        } else {
            this.effectivenessReason = "No significant improvement detected";
        }
    }

    @Override
    public String toString() {
        return String.format("Feedback[%s: action=%s, effective=%s, health: %.3f->%.3f, score=%.2f]",
                episodeId, takenAction, effective, beforeSystemHealth, afterSystemHealth, effectivenessScore);
    }
}

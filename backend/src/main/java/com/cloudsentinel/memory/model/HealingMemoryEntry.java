package com.cloudsentinel.memory.model;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.detector.model.AnomalyAlert.AnomalyType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a stored healing episode in persistent memory.
 * Used for learning from past experiences.
 */
public class HealingMemoryEntry {
    private String episodeId;
    private Instant timestamp;

    // Context
    private String difficulty;
    private List<String> activeFailures = new ArrayList<>();
    private List<AnomalyType> anomalies = new ArrayList<>();

    // State snapshots
    private double systemHealthBefore;
    private double systemHealthAfter;
    private double sloScoreBefore;
    private double sloScoreAfter;

    // Decision and action
    private ActionType actionTaken;
    private String decisionSource;  // RL, RULE, HYBRID
    private boolean actionEffective;
    private double effectivenessScore;
    private double reward;

    // Lessons learned
    private String lesson;
    private boolean shouldRepeatInSimilarContext;

    public HealingMemoryEntry() {
        this.episodeId = "HEAL-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
        this.timestamp = Instant.now();
    }

    // --- Getters and Setters ---

    public String getEpisodeId() { return episodeId; }
    public void setEpisodeId(String episodeId) { this.episodeId = episodeId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public List<String> getActiveFailures() { return activeFailures; }
    public void setActiveFailures(List<String> activeFailures) { this.activeFailures = activeFailures; }

    public void addActiveFailure(String failure) { this.activeFailures.add(failure); }

    public List<AnomalyType> getAnomalies() { return anomalies; }
    public void setAnomalies(List<AnomalyType> anomalies) { this.anomalies = anomalies; }

    public void addAnomaly(AnomalyType anomaly) { this.anomalies.add(anomaly); }

    public double getSystemHealthBefore() { return systemHealthBefore; }
    public void setSystemHealthBefore(double systemHealthBefore) { this.systemHealthBefore = systemHealthBefore; }

    public double getSystemHealthAfter() { return systemHealthAfter; }
    public void setSystemHealthAfter(double systemHealthAfter) { this.systemHealthAfter = systemHealthAfter; }

    public double getSloScoreBefore() { return sloScoreBefore; }
    public void setSloScoreBefore(double sloScoreBefore) { this.sloScoreBefore = sloScoreBefore; }

    public double getSloScoreAfter() { return sloScoreAfter; }
    public void setSloScoreAfter(double sloScoreAfter) { this.sloScoreAfter = sloScoreAfter; }

    public ActionType getActionTaken() { return actionTaken; }
    public void setActionTaken(ActionType actionTaken) { this.actionTaken = actionTaken; }

    public String getDecisionSource() { return decisionSource; }
    public void setDecisionSource(String decisionSource) { this.decisionSource = decisionSource; }

    public boolean isActionEffective() { return actionEffective; }
    public void setActionEffective(boolean actionEffective) { this.actionEffective = actionEffective; }

    public double getEffectivenessScore() { return effectivenessScore; }
    public void setEffectivenessScore(double effectivenessScore) { this.effectivenessScore = effectivenessScore; }

    public double getReward() { return reward; }
    public void setReward(double reward) { this.reward = reward; }

    public String getLesson() { return lesson; }
    public void setLesson(String lesson) { this.lesson = lesson; }

    public boolean isShouldRepeatInSimilarContext() { return shouldRepeatInSimilarContext; }
    public void setShouldRepeatInSimilarContext(boolean shouldRepeatInSimilarContext) {
        this.shouldRepeatInSimilarContext = shouldRepeatInSimilarContext;
    }

    /**
     * Generate a human-readable lesson from this experience.
     */
    public void generateLesson() {
        StringBuilder lesson = new StringBuilder();

        if (actionEffective) {
            lesson.append("EFFECTIVE: ");
            lesson.append(actionTaken).append(" successfully addressed ");
            if (!anomalies.isEmpty()) {
                lesson.append(anomalies.get(0));
            }
            lesson.append(" (health: ").append(String.format("%.2f", systemHealthBefore))
                    .append(" -> ").append(String.format("%.2f", systemHealthAfter)).append(")");
        } else {
            lesson.append("INEFFECTIVE: ");
            lesson.append(actionTaken).append(" did not resolve ");
            if (!anomalies.isEmpty()) {
                lesson.append(anomalies.get(0));
            }
            if (systemHealthAfter < systemHealthBefore) {
                lesson.append(" and worsened system health");
            }
        }

        this.lesson = lesson.toString();
        this.shouldRepeatInSimilarContext = actionEffective && effectivenessScore > 0.5;
    }

    @Override
    public String toString() {
        return String.format("HealingMemory[%s: %s -> %s, effective=%s, lesson=%s]",
                episodeId, actionTaken, decisionSource, actionEffective, lesson);
    }
}

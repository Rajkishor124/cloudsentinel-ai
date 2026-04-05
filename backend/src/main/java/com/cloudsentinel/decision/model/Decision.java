package com.cloudsentinel.decision.model;

import com.cloudsentinel.action.model.ActionType;

/**
 * Represents a decision made by the hybrid decision engine.
 */
public class Decision {
    private ActionType recommendedAction;
    private DecisionSource source;
    private double confidence;
    private String reasoning;
    private boolean overridden;
    private ActionType originalAction;

    public enum DecisionSource {
        RULE_ENGINE("Rule-based emergency response"),
        RL_AGENT("Reinforcement Learning agent"),
        HYBRID("Combined rule + RL recommendation");

        private final String description;

        DecisionSource(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public Decision() {}

    public Decision(ActionType action, DecisionSource source, double confidence, String reasoning) {
        this.recommendedAction = action;
        this.source = source;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.overridden = false;
    }

    // --- Getters and Setters ---

    public ActionType getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(ActionType recommendedAction) { this.recommendedAction = recommendedAction; }

    public DecisionSource getSource() { return source; }
    public void setSource(DecisionSource source) { this.source = source; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public boolean isOverridden() { return overridden; }
    public void setOverridden(boolean overridden) { this.overridden = overridden; }

    public ActionType getOriginalAction() { return originalAction; }
    public void setOriginalAction(ActionType originalAction) { this.originalAction = originalAction; }

    /**
     * Record that this decision was overridden by a rule.
     */
    public void overrideWith(ActionType ruleAction, String reason) {
        this.overridden = true;
        this.originalAction = this.recommendedAction;
        this.recommendedAction = ruleAction;
        this.source = DecisionSource.RULE_ENGINE;
        this.reasoning = "RULE OVERRIDE: " + reason;
        this.confidence = 1.0;  // Rules have absolute confidence
    }

    @Override
    public String toString() {
        return String.format("Decision[action=%s, source=%s, confidence=%.2f, reasoning=%s]",
                recommendedAction, source, confidence, reasoning);
    }
}

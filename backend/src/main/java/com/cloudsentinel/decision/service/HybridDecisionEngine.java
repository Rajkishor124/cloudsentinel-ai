package com.cloudsentinel.decision.service;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.decision.model.Decision;
import com.cloudsentinel.decision.model.Decision.DecisionSource;
import com.cloudsentinel.detector.model.AnomalyAlert;
import com.cloudsentinel.simulator.model.InfrastructureState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hybrid decision engine that combines:
 * 1. Rule-based emergency response (critical cases)
 * 2. RL agent recommendations (optimization cases)
 *
 * Decision flow:
 * - Detect anomalies
 * - Check emergency rules (overrides everything)
 * - Query RL agent for non-emergency decisions
 * - Apply safety overrides to RL decisions
 * - Return final decision with provenance
 */
@Slf4j
@Service
public class HybridDecisionEngine {

    private final RuleEngine ruleEngine;

    public HybridDecisionEngine(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    /**
     * Make a decision combining RL agent recommendation with rule-based safety.
     *
     * @param rlAction The action recommended by the RL agent (code)
     * @param state Current infrastructure state
     * @param alerts Detected anomalies
     * @return Final decision with source and reasoning
     */
    public Decision makeDecision(int rlAction, InfrastructureState state, List<AnomalyAlert> alerts) {
        // Step 1: Check emergency rules first
        Decision emergencyDecision = ruleEngine.checkEmergencyRules(state, alerts);

        if (emergencyDecision != null) {
            log.info("[Decision] EMERGENCY RULE: {} (reason: {})",
                    emergencyDecision.getRecommendedAction(), emergencyDecision.getReasoning());
            return emergencyDecision;
        }

        // Step 2: Create RL-based decision
        ActionType rlActionType = ActionType.fromCode(rlAction);
        Decision rlDecision = new Decision(rlActionType, DecisionSource.RL_AGENT, 0.7,
                "RL agent recommendation for action: " + rlActionType);

        // Step 3: Apply safety overrides
        Decision finalDecision = ruleEngine.maybeOverride(rlDecision, state, alerts);

        if (finalDecision.isOverridden()) {
            log.info("[Decision] RULE OVERRIDE: {} -> {} (reason: {})",
                    finalDecision.getOriginalAction(),
                    finalDecision.getRecommendedAction(),
                    finalDecision.getReasoning());
        } else {
            log.debug("[Decision] RL ACTION: {} (source: {})",
                    finalDecision.getRecommendedAction(), finalDecision.getSource());
        }

        return finalDecision;
    }

    /**
     * Make a decision when RL agent is not available (rules only).
     */
    public Decision makeRuleBasedDecision(InfrastructureState state, List<AnomalyAlert> alerts) {
        // Check emergency rules
        Decision emergencyDecision = ruleEngine.checkEmergencyRules(state, alerts);
        if (emergencyDecision != null) {
            return emergencyDecision;
        }

        // No emergency - return DO_NOTHING with context
        if (alerts.isEmpty()) {
            return new Decision(ActionType.DO_NOTHING, DecisionSource.RULE_ENGINE, 0.8,
                    "No anomalies detected, system healthy");
        }

        // Use most recommended action from most severe alert
        AnomalyAlert mostSevere = alerts.stream()
                .max((a, b) -> Integer.compare(a.getSeverity().getLevel(), b.getSeverity().getLevel()))
                .orElse(null);

        if (mostSevere != null && !mostSevere.getRecommendedActions().isEmpty()) {
            ActionType action = parseAction(mostSevere.getRecommendedActions().get(0));
            return new Decision(action, DecisionSource.RULE_ENGINE, 0.6,
                    "Rule-based response to " + mostSevere.getAnomalyType() + " on " + mostSevere.getServiceName());
        }

        return new Decision(ActionType.DO_NOTHING, DecisionSource.RULE_ENGINE, 0.5,
                "No clear rule-based action for detected anomalies");
    }

    /**
     * Get all recommended actions for current state (for dashboard display).
     */
    public List<String> getAllRecommendations(InfrastructureState state, List<AnomalyAlert> alerts) {
        Decision decision = makeRuleBasedDecision(state, alerts);
        return List.of(decision.getRecommendedAction().name());
    }

    private ActionType parseAction(String actionName) {
        try {
            return ActionType.valueOf(actionName);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ActionType.DO_NOTHING;
        }
    }
}

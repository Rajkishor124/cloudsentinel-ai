package com.cloudsentinel.decision.service;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.decision.model.Decision;
import com.cloudsentinel.decision.model.Decision.DecisionSource;
import com.cloudsentinel.detector.model.AnomalyAlert;
import com.cloudsentinel.detector.model.AnomalyAlert.AnomalyType;
import com.cloudsentinel.detector.model.SeverityLevel;
import com.cloudsentinel.simulator.model.FailureMode;
import com.cloudsentinel.simulator.model.InfrastructureState;
import com.cloudsentinel.simulator.model.ServiceNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rule-based decision engine for critical/emergency situations.
 * Overrides RL agent when safety or system stability is at risk.
 */
@Slf4j
@Service
public class RuleEngine {

    /**
     * Check if any emergency rules apply and return an overriding decision.
     * Returns null if no rules apply (let RL agent decide).
     */
    public Decision checkEmergencyRules(InfrastructureState state, List<AnomalyAlert> alerts) {
        if (alerts.isEmpty()) {
            return null;
        }

        // Rule 1: Multiple critical failures - trigger circuit breaker immediately
        long criticalCount = alerts.stream()
                .filter(a -> a.getSeverity() == SeverityLevel.CRITICAL)
                .count();

        if (criticalCount >= 2) {
            return new Decision(ActionType.TRIGGER_CIRCUIT_BREAKER, DecisionSource.RULE_ENGINE, 1.0,
                    "EMERGENCY: " + criticalCount + " critical anomalies detected, circuit breaker required");
        }

        // Rule 2: Service crash with high error rate - restart immediately
        for (ServiceNode node : state.getTopology().getAllNodes()) {
            if (node.getActiveFailure() == FailureMode.SERVICE_CRASH && node.getErrorRate() > 0.5) {
                return new Decision(ActionType.RESTART_SERVICE, DecisionSource.RULE_ENGINE, 1.0,
                        "EMERGENCY: Service " + node.getServiceName() + " crashed with " +
                        String.format("%.0f%%", node.getErrorRate() * 100) + " error rate");
            }
        }

        // Rule 3: Cascading failure spreading - reroute traffic
        long cascadingCount = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() == FailureMode.CASCADING_FAILURE)
                .count();

        if (cascadingCount >= 2) {
            return new Decision(ActionType.REROUTE_TRAFFIC, DecisionSource.RULE_ENGINE, 0.95,
                    "EMERGENCY: " + cascadingCount + " services experiencing cascading failures");
        }

        // Rule 4: Database deadlock - restart database immediately
        for (ServiceNode node : state.getTopology().getAllNodes()) {
            if (node.getActiveFailure() == FailureMode.DATABASE_DEADLOCK && node.getCpu() > 0.8) {
                return new Decision(ActionType.RESTART_DATABASE, DecisionSource.RULE_ENGINE, 0.95,
                        "EMERGENCY: Database deadlock detected with high CPU on " + node.getServiceName());
            }
        }

        // Rule 5: System-wide health below threshold - scale up all
        if (state.getAverageHealth() < 0.3) {
            return new Decision(ActionType.SCALE_UP, DecisionSource.RULE_ENGINE, 0.9,
                    "EMERGENCY: System-wide health critically low at " +
                    String.format("%.1f%%", state.getAverageHealth() * 100));
        }

        // Rule 6: Memory leak approaching OOM - clear cache + prepare restart
        for (ServiceNode node : state.getTopology().getAllNodes()) {
            if (node.getMemory() > 0.95 && node.getActiveFailure() == FailureMode.MEMORY_LEAK) {
                return new Decision(ActionType.CLEAR_CACHE, DecisionSource.RULE_ENGINE, 0.9,
                        "EMERGENCY: Memory leak near OOM on " + node.getServiceName() +
                        " at " + String.format("%.1f%%", node.getMemory() * 100));
            }
        }

        // No emergency rules apply
        return null;
    }

    /**
     * Get recommended actions for a specific anomaly (non-emergency guidance).
     */
    public List<String> getRecommendedActions(AnomalyAlert alert) {
        return alert.getRecommendedActions();
    }

    /**
     * Decide whether to override an RL agent's decision.
     * Returns the final decision (possibly overridden).
     */
    public Decision maybeOverride(Decision rlDecision, InfrastructureState state, List<AnomalyAlert> alerts) {
        if (rlDecision == null) {
            return null;
        }

        // Check emergency rules
        Decision emergencyDecision = checkEmergencyRules(state, alerts);

        if (emergencyDecision != null) {
            // Emergency rule overrides RL decision
            log.warn("Overriding RL decision {} with emergency rule: {}",
                    rlDecision.getRecommendedAction(), emergencyDecision.getRecommendedAction());
            rlDecision.overrideWith(emergencyDecision.getRecommendedAction(),
                    emergencyDecision.getReasoning());
            return rlDecision;
        }

        // Rule 7: If RL suggests DO_NOTHING but there are HIGH severity alerts, suggest intervention
        if (rlDecision.getRecommendedAction() == ActionType.DO_NOTHING) {
            long highSeverityAlerts = alerts.stream()
                    .filter(a -> a.getSeverity() == SeverityLevel.HIGH || a.getSeverity() == SeverityLevel.CRITICAL)
                    .count();

            if (highSeverityAlerts > 0) {
                AnomalyAlert mostSevere = alerts.stream()
                        .max((a, b) -> Integer.compare(a.getSeverity().getLevel(), b.getSeverity().getLevel()))
                        .orElse(null);

                if (mostSevere != null && !mostSevere.getRecommendedActions().isEmpty()) {
                    ActionType correctiveAction = parseAction(mostSevere.getRecommendedActions().get(0));
                    log.info("RL suggests DO_NOTHING but {} HIGH/CRITICAL alerts present, suggesting {} instead",
                            highSeverityAlerts, correctiveAction);
                    rlDecision.setRecommendedAction(correctiveAction);
                    rlDecision.setSource(DecisionSource.HYBRID);
                    rlDecision.setReasoning("RL + Rule: " + highSeverityAlerts + " high severity alerts detected");
                    return rlDecision;
                }
            }
        }

        return rlDecision;
    }

    /**
     * Parse action string to ActionType enum.
     */
    private ActionType parseAction(String actionName) {
        try {
            return ActionType.valueOf(actionName);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ActionType.DO_NOTHING;
        }
    }
}

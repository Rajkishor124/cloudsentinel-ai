package com.cloudsentinel.executor.service;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.executor.model.ActionExecutionRecord;
import com.cloudsentinel.executor.model.ActionExecutionRecord.ExecutionStatus;
import com.cloudsentinel.simulator.engine.CausalStateMachine;
import com.cloudsentinel.simulator.model.FailureMode;
import com.cloudsentinel.simulator.model.InfrastructureState;
import com.cloudsentinel.simulator.model.ServiceNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Executes remediation actions on the infrastructure.
 * Captures before/after state for feedback learning.
 */
@Slf4j
@Service
public class ActionExecutor {

    private final CausalStateMachine stateMachine;

    public ActionExecutor(CausalStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    /**
     * Execute an action on the current state and return execution record with before/after metrics.
     */
    public ActionExecutionRecord execute(ActionType action, InfrastructureState state) {
        // Identify target service
        ServiceNode target = findTargetService(state, action);
        String targetId = target != null ? target.getServiceId() : "N/A";
        String targetName = target != null ? target.getServiceName() : "N/A";

        // Capture before state
        double beforeHealth = target != null ? target.getHealthScore() : state.getAverageHealth();
        double beforeCpu = target != null ? target.getCpu() : 0.0;
        double beforeErrorRate = target != null ? target.getErrorRate() : 0.0;
        double beforeLatency = target != null ? target.getLatency() : 0.0;

        ActionExecutionRecord record = new ActionExecutionRecord(
                action, targetId, targetName,
                beforeHealth, beforeCpu, beforeErrorRate, beforeLatency);

        // Check preconditions
        if (action != ActionType.DO_NOTHING) {
            String blockReason = checkPreconditions(action, state);
            if (blockReason != null) {
                record.block(ExecutionStatus.BLOCKED_BY_PRECONDITION, blockReason);
                log.warn("Action {} blocked: {}", action, blockReason);
                return record;
            }

            // Check cooldowns
            if (target != null && target.getRestartCooldown() > 0 &&
                    (action == ActionType.RESTART_SERVICE || action == ActionType.RESTART_DATABASE)) {
                record.block(ExecutionStatus.BLOCKED_BY_COOLDOWN,
                        "Service " + targetName + " in restart cooldown (" + target.getRestartCooldown() + " ticks)");
                log.warn("Action {} blocked by cooldown on {}", action, targetName);
                return record;
            }
        }

        try {
            // Execute action
            record.setStatus(ExecutionStatus.IN_PROGRESS);
            double cost = stateMachine.applyAction(state, action);
            record.setCostIncurred(cost);

            // Advance simulation
            stateMachine.advanceTick(state);

            // Capture after state
            double afterHealth = target != null ? target.getHealthScore() : state.getAverageHealth();
            double afterCpu = target != null ? target.getCpu() : 0.0;
            double afterErrorRate = target != null ? target.getErrorRate() : 0.0;
            double afterLatency = target != null ? target.getLatency() : 0.0;

            // Determine effectiveness
            boolean effective = isEffective(action, beforeHealth, afterHealth, beforeErrorRate, afterErrorRate);
            String reason = computeEffectivenessReason(action, beforeHealth, afterHealth, beforeErrorRate, afterErrorRate);

            record.complete(afterHealth, afterCpu, afterErrorRate, afterLatency, cost, effective, reason);

            log.info("Action {} executed on {} (cost: {:.4f}, effective: {}, health: {:.3f} -> {:.3f})",
                    action, targetName, cost, effective, beforeHealth, afterHealth);

        } catch (Exception e) {
            record.fail("Execution error: " + e.getMessage());
            log.error("Action {} failed on {}: {}", action, targetName, e.getMessage());
        }

        return record;
    }

    /**
     * Find the target service for an action.
     */
    private ServiceNode findTargetService(InfrastructureState state, ActionType action) {
        if (state.getTopology() == null) return null;

        return switch (action) {
            case DO_NOTHING -> null;
            case RESTART_SERVICE, SCALE_UP -> findMostAffectedNode(state);
            case SCALE_DOWN -> findLeastLoadedNode(state);
            case CLEAR_CACHE, REROUTE_TRAFFIC, TRIGGER_CIRCUIT_BREAKER -> findMostAffectedNode(state);
            case RESTART_DATABASE -> findDatabaseNode(state);
            case ROLLBACK_DEPLOYMENT -> findRecentlyDeployedNode(state);
        };
    }

    /**
     * Check if action preconditions are met.
     * Returns null if OK, or a reason string if blocked.
     */
    private String checkPreconditions(ActionType action, InfrastructureState state) {
        return switch (action) {
            case SCALE_DOWN -> {
                // Only valid if CPU < 40% somewhere
                boolean hasLowCpu = state.getTopology().getAllNodes().stream()
                        .anyMatch(n -> n.getCpu() < 0.4);
                yield hasLowCpu ? null : "No service with CPU < 40%";
            }
            case REROUTE_TRAFFIC -> {
                // Requires at least 2 healthy replicas
                long healthyCount = state.getTopology().getAllNodes().stream()
                        .filter(n -> n.getActiveFailure() == FailureMode.HEALTHY)
                        .count();
                yield healthyCount >= 2 ? null : "Less than 2 healthy replicas";
            }
            case ROLLBACK_DEPLOYMENT -> {
                // Requires recent deploy
                boolean hasRecentDeploy = state.getTopology().getAllNodes().stream()
                        .anyMatch(ServiceNode::isRecentDeploy);
                yield hasRecentDeploy ? null : "No recent deployments";
            }
            default -> null;
        };
    }

    /**
     * Determine if the action was effective.
     */
    private boolean isEffective(ActionType action, double beforeHealth, double afterHealth,
                                 double beforeErrorRate, double afterErrorRate) {
        // Action is effective if health improved or error rate decreased
        return afterHealth > beforeHealth || afterErrorRate < beforeErrorRate;
    }

    /**
     * Compute reason for effectiveness assessment.
     */
    private String computeEffectivenessReason(ActionType action, double beforeHealth, double afterHealth,
                                               double beforeErrorRate, double afterErrorRate) {
        double healthDelta = afterHealth - beforeHealth;
        double errorDelta = afterErrorRate - beforeErrorRate;

        if (healthDelta > 0.05) {
            return String.format("Health improved by %.1f%%", healthDelta * 100);
        } else if (errorDelta < -0.05) {
            return String.format("Error rate reduced by %.1f%%", -errorDelta * 100);
        } else if (healthDelta > 0) {
            return String.format("Minor health improvement (+%.1f%%)", healthDelta * 100);
        } else if (healthDelta < -0.05) {
            return String.format("Health degraded by %.1f%%", -healthDelta * 100);
        } else {
            return "No significant change";
        }
    }

    private ServiceNode findMostAffectedNode(InfrastructureState state) {
        return state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != FailureMode.HEALTHY)
                .max((a, b) -> Double.compare(a.getErrorRate(), b.getErrorRate()))
                .orElse(state.getTopology().getAllNodes().stream()
                        .max((a, b) -> Double.compare(a.getHealthScore(), b.getHealthScore()))
                        .orElse(null));
    }

    private ServiceNode findLeastLoadedNode(InfrastructureState state) {
        return state.getTopology().getAllNodes().stream()
                .min((a, b) -> Double.compare(a.getCpu(), b.getCpu()))
                .orElse(null);
    }

    private ServiceNode findDatabaseNode(InfrastructureState state) {
        return state.getTopology().getAllNodes().stream()
                .filter(n -> n.getServiceId().contains("db"))
                .findFirst()
                .orElse(null);
    }

    private ServiceNode findRecentlyDeployedNode(InfrastructureState state) {
        return state.getTopology().getAllNodes().stream()
                .filter(ServiceNode::isRecentDeploy)
                .findFirst()
                .orElse(null);
    }
}

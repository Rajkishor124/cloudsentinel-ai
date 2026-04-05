package com.cloudsentinel.reward.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.simulator.model.FailureMode;
import com.cloudsentinel.simulator.model.InfrastructureState;
import com.cloudsentinel.simulator.model.ServiceNode;

/**
 * Computes rewards using potential-based reward shaping.
 * Reward = SLO_score + gamma * (phi_next - phi_prev) - cost_penalty - unnecessary_penalty + proactive_bonus
 *
 * ENHANCED: Multi-objective reward design:
 * - System stability (low variance in metrics)
 * - Failure reduction (penalize active failures)
 * - Cost efficiency (penalize costly actions)
 * - Long-term improvement (trend-based bonus)
 */
@Service
public class RewardCalculator {

    @Value("${app.reward.gamma:0.99}")
    private double gamma;

    @Value("${app.reward.cost-weight:1.0}")
    private double costWeight;

    @Value("${app.reward.unnecessary-penalty:0.15}")
    private double unnecessaryPenalty;

    @Value("${app.reward.proactive-bonus:0.3}")
    private double proactiveBonus;

    @Value("${app.reward.stability-weight:0.5}")
    private double stabilityWeight;

    @Value("${app.reward.failure-penalty:0.5}")
    private double failurePenalty;

    @Value("${app.reward.recovery-bonus:1.0}")
    private double recoveryBonus;

    /**
     * Compute the reward for a transition.
     */
    public double computeReward(InfrastructureState prevState, InfrastructureState nextState, ActionType action, double actionCost) {
        double sloReward = computeSloReward(nextState);
        double shaping = computePotentialShaping(prevState, nextState);
        double costPenalty = actionCost * costWeight;
        double unnecessary = computeUnnecessaryPenalty(prevState, action);
        double proactive = computeProactiveBonus(prevState, action);

        // NEW: Multi-objective components
        double stabilityReward = computeStabilityReward(nextState);
        double failurePenaltyVal = computeFailurePenalty(nextState);
        double recoveryBonusVal = computeRecoveryBonus(prevState, nextState, action);

        return sloReward + shaping - costPenalty - unnecessary + proactive
               + stabilityReward - failurePenaltyVal + recoveryBonusVal;
    }

    /**
     * SLO reward: how well the system meets SLO targets.
     * Returns a value in [0, 1].
     */
    private double computeSloReward(InfrastructureState state) {
        if (state.getTopology() == null) return 1.0;

        double availability = state.getSloScore();

        // Check error rate SLO
        double maxErrorRate = state.getTopology().getAllNodes().stream()
                .mapToDouble(ServiceNode::getErrorRate)
                .max().orElse(0.0);
        double errorPenalty = Math.max(0.0, 1.0 - maxErrorRate / 0.001);  // Full penalty if error_rate > 0.001

        // Check CPU SLO
        double maxCpu = state.getTopology().getAllNodes().stream()
                .mapToDouble(ServiceNode::getCpu)
                .max().orElse(0.0);
        double cpuPenalty = maxCpu > 0.8 ? Math.max(0.0, 1.0 - (maxCpu - 0.8) / 0.2) : 1.0;

        // Check latency SLO
        double maxLatency = state.getTopology().getAllNodes().stream()
                .mapToDouble(ServiceNode::getLatency)
                .max().orElse(0.0);
        double latencyPenalty = maxLatency > 0.2 ? Math.max(0.0, 1.0 - (maxLatency - 0.2) / 0.8) : 1.0;

        return (availability * 0.4 + errorPenalty * 0.25 + cpuPenalty * 0.2 + latencyPenalty * 0.15);
    }

    /**
     * Potential-based shaping: gamma * phi(next) - phi(prev)
     * This is theoretically safe and doesn't change the optimal policy.
     */
    private double computePotentialShaping(InfrastructureState prev, InfrastructureState next) {
        double phiPrev = computePotential(prev);
        double phiNext = computePotential(next);
        return gamma * phiNext - phiPrev;
    }

    /**
     * Potential function: weighted sum of system health indicators.
     * Higher potential = better state.
     */
    private double computePotential(InfrastructureState state) {
        if (state.getTopology() == null) return 1.0;

        double avgCpu = state.getTopology().getAllNodes().stream()
                .mapToDouble(ServiceNode::getCpu)
                .average().orElse(0.0);

        double avgMemory = state.getTopology().getAllNodes().stream()
                .mapToDouble(ServiceNode::getMemory)
                .average().orElse(0.0);

        double avgError = state.getTopology().getAllNodes().stream()
                .mapToDouble(ServiceNode::getErrorRate)
                .average().orElse(0.0);

        double avgLatency = state.getTopology().getAllNodes().stream()
                .mapToDouble(ServiceNode::getLatency)
                .average().orElse(0.0);

        return (1.0 - avgCpu) * 0.4
                + (1.0 - avgMemory) * 0.3
                + (1.0 - avgError) * 0.2
                + (1.0 - avgLatency) * 0.1;
    }

    /**
     * Penalize unnecessary actions when the system is healthy.
     */
    private double computeUnnecessaryPenalty(InfrastructureState state, ActionType action) {
        if (action == ActionType.DO_NOTHING) return 0.0;
        if (!state.isSystemHealthy()) return 0.0;
        return unnecessaryPenalty;
    }

    /**
     * Bonus for proactive intervention before full failure manifests.
     */
    private double computeProactiveBonus(InfrastructureState state, ActionType action) {
        if (state.getTopology() == null) return 0.0;

        boolean detectedPrecursor = state.getTopology().getAllNodes().stream()
                .anyMatch(n -> n.getActiveFailure() == FailureMode.HEALTHY &&
                        (n.getCpu() > 0.5 || n.getMemory() > 0.6 || n.getErrorRate() > 0.05));

        if (!detectedPrecursor) return 0.0;

        boolean isPreventive = action == ActionType.RESTART_SERVICE ||
                action == ActionType.SCALE_UP ||
                action == ActionType.CLEAR_CACHE ||
                action == ActionType.TRIGGER_CIRCUIT_BREAKER;

        return isPreventive ? proactiveBonus : 0.0;
    }

    /**
     * NEW: Stability reward - penalizes high variance in metrics across services.
     * Encourages balanced resource utilization and consistent performance.
     */
    private double computeStabilityReward(InfrastructureState state) {
        if (state.getTopology() == null) return 0.0;

        var nodes = state.getTopology().getAllNodes();
        int n = nodes.size();
        if (n <= 1) return 0.5;  // No variance with single node

        double[] cpus = nodes.stream().mapToDouble(ServiceNode::getCpu).toArray();
        double[] errors = nodes.stream().mapToDouble(ServiceNode::getErrorRate).toArray();

        double cpuVar = computeVariance(cpus);
        double errorVar = computeVariance(errors);

        // Lower variance = higher stability reward
        double stability = 1.0 - Math.min(cpuVar + errorVar, 1.0);
        return stability * stabilityWeight;
    }

    /**
     * NEW: Failure penalty - penalizes each active failure proportionally to severity.
     * Encourages rapid failure resolution and prevention.
     */
    private double computeFailurePenalty(InfrastructureState state) {
        if (state.getTopology() == null) return 0.0;

        double totalPenalty = 0.0;
        for (ServiceNode node : state.getTopology().getAllNodes()) {
            if (node.getActiveFailure() != FailureMode.HEALTHY) {
                // Base penalty per failure
                double severity = (node.getCpu() + node.getMemory() + node.getErrorRate()) / 3.0;
                totalPenalty += severity;

                // Extra penalty for cascading failures
                if (node.getActiveFailure() == FailureMode.CASCADING_FAILURE) {
                    totalPenalty += 0.5;
                }
                // Extra penalty for service crashes
                if (node.getActiveFailure() == FailureMode.SERVICE_CRASH) {
                    totalPenalty += 0.3;
                }
            }
        }

        return Math.min(totalPenalty * failurePenalty, 2.0);  // Cap penalty
    }

    /**
     * NEW: Recovery bonus - rewards transitioning from failure to healthy state.
     * Encourages effective remediation actions.
     */
    private double computeRecoveryBonus(InfrastructureState prevState, InfrastructureState nextState, ActionType action) {
        if (prevState.getTopology() == null || nextState.getTopology() == null) return 0.0;

        if (action == ActionType.DO_NOTHING) return 0.0;

        double bonus = 0.0;

        // Count failures before and after
        long prevFailureCount = prevState.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != FailureMode.HEALTHY)
                .count();

        long nextFailureCount = nextState.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != FailureMode.HEALTHY)
                .count();

        // Bonus for reducing failure count
        if (nextFailureCount < prevFailureCount) {
            bonus += recoveryBonus * (prevFailureCount - nextFailureCount);
        }

        // Bonus for reducing error rate on previously failed nodes
        for (ServiceNode prevNode : prevState.getTopology().getAllNodes()) {
            if (prevNode.getActiveFailure() != FailureMode.HEALTHY) {
                ServiceNode nextNode = nextState.getTopology().getNode(prevNode.getServiceId());
                if (nextNode != null && nextNode.getErrorRate() < prevNode.getErrorRate()) {
                    double improvement = prevNode.getErrorRate() - nextNode.getErrorRate();
                    bonus += recoveryBonus * 0.5 * improvement;
                }
            }
        }

        return Math.min(bonus, 1.0);  // Cap bonus
    }

    /**
     * Compute variance of an array.
     */
    private double computeVariance(double[] values) {
        if (values.length == 0) return 0.0;
        double mean = 0.0;
        for (double v : values) mean += v;
        mean /= values.length;

        double var = 0.0;
        for (double v : values) var += (v - mean) * (v - mean);
        return var / values.length;
    }
}

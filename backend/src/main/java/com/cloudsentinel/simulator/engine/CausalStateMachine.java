package com.cloudsentinel.simulator.engine;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.simulator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Core simulation engine that advances the state machine each tick.
 * Handles failure propagation through the dependency graph, metric evolution,
 * and action effects on service health.
 */
@Slf4j
@Service
public class CausalStateMachine {

    private static final double HEALTHY_CPU = 0.2;
    private static final double HEALTHY_MEMORY = 0.3;
    private static final double HEALTHY_ERROR_RATE = 0.0;
    private static final double HEALTHY_LATENCY = 0.1;

    private final FailureInjector failureInjector;

    public CausalStateMachine(FailureInjector failureInjector) {
        this.failureInjector = failureInjector;
    }

    /**
     * Create a new initial state for the given difficulty level.
     */
    public InfrastructureState createInitialState(DifficultyLevel difficulty, int maxTicks) {
        TopologyGraph topology = switch (difficulty) {
            case SIMPLE -> TopologyGraph.simpleTopology();
            case MEDIUM -> TopologyGraph.mediumTopology();
            case COMPLEX, ADVERSARIAL -> TopologyGraph.complexTopology();
        };
        return new InfrastructureState(topology, maxTicks);
    }

    /**
     * Inject a failure into the topology based on difficulty level.
     * Returns the failure mode that was injected.
     */
    public FailureMode injectFailure(InfrastructureState state, DifficultyLevel difficulty) {
        return failureInjector.injectFailure(state, difficulty);
    }

    /**
     * Advance the simulation by one tick. Applies natural metric evolution,
     * failure propagation, and updates SLO scores.
     */
    public void advanceTick(InfrastructureState state) {
        state.setTick(state.getTick() + 1);

        for (ServiceNode node : state.getTopology().getAllNodes()) {
            // Decay cooldowns
            if (node.getRestartCooldown() > 0) {
                node.setRestartCooldown(node.getRestartCooldown() - 1);
                if (node.getRestartCooldown() == 0) {
                    node.setRecentlyRestarted(false);
                }
            }

            // Evolve failure metrics
            if (node.getActiveFailure() != FailureMode.HEALTHY) {
                evolveFailureMetrics(node);
            } else {
                // Natural drift toward healthy baseline
                node.setCpu(lerp(node.getCpu(), HEALTHY_CPU, 0.1));
                node.setMemory(lerp(node.getMemory(), HEALTHY_MEMORY, 0.08));
                node.setErrorRate(lerp(node.getErrorRate(), HEALTHY_ERROR_RATE, 0.15));
                node.setLatency(lerp(node.getLatency(), HEALTHY_LATENCY, 0.12));
            }
        }

        // Propagate cascading failures
        propagateCascadingFailures(state);

        // Recompute SLO
        state.computeSloScore();

        // Check terminal conditions
        if (state.getTick() >= state.getMaxTicks()) {
            state.setTerminal(true);
            state.setTerminalReason("max_ticks_reached");
        }
    }

    /**
     * Apply an action to the current state and return the cost incurred.
     */
    public double applyAction(InfrastructureState state, ActionType action) {
        if (action == ActionType.DO_NOTHING) {
            return 0.0;
        }

        double cost = action.getCostMultiplier();

        switch (action) {
            case RESTART_SERVICE -> {
                // Find the most affected node and restart it
                ServiceNode target = findMostAffectedNode(state);
                if (target != null && target.getRestartCooldown() == 0) {
                    target.setActiveFailure(FailureMode.HEALTHY);
                    target.setFailureTick(0);
                    target.setCpu(0.3);  // Restart spike
                    target.setMemory(0.35);
                    target.setErrorRate(0.05);  // Brief error spike
                    target.setLatency(0.15);
                    target.setRestartCooldown(10);
                    target.setRecentlyRestarted(true);
                }
            }
            case SCALE_UP -> {
                ServiceNode target = findMostAffectedNode(state);
                if (target != null) {
                    target.setCpu(target.getCpu() * 0.6);
                    target.setLatency(target.getLatency() * 0.7);
                    target.setErrorRate(target.getErrorRate() * 0.5);
                }
            }
            case SCALE_DOWN -> {
                ServiceNode target = findLeastLoadedNode(state);
                if (target != null && target.getCpu() < 0.4) {
                    target.setCpu(target.getCpu() * 0.8);
                }
            }
            case CLEAR_CACHE -> {
                // Reduce memory pressure on services with redis dependency
                for (ServiceNode node : state.getTopology().getAllNodes()) {
                    if (node.getMemory() > 0.5) {
                        node.setMemory(node.getMemory() * 0.7);
                    }
                }
            }
            case RESTART_DATABASE -> {
                for (ServiceNode node : state.getTopology().getAllNodes()) {
                    if (node.getServiceId().contains("db")) {
                        node.setActiveFailure(FailureMode.HEALTHY);
                        node.setFailureTick(0);
                        node.setCpu(0.1);
                        node.setMemory(0.2);
                        node.setErrorRate(0.0);
                        node.setLatency(0.05);
                        node.setRestartCooldown(15);
                        node.setRecentlyRestarted(true);
                    }
                }
                cost += 0.2;  // Extra cost for DB restart
            }
            case REROUTE_TRAFFIC -> {
                // Reduce error rates on affected nodes by shifting load
                for (ServiceNode node : state.getTopology().getAllNodes()) {
                    if (node.getActiveFailure() != FailureMode.HEALTHY) {
                        node.setErrorRate(node.getErrorRate() * 0.4);
                        node.setLatency(node.getLatency() * 0.8);
                    }
                }
            }
            case ROLLBACK_DEPLOYMENT -> {
                for (ServiceNode node : state.getTopology().getAllNodes()) {
                    if (node.isRecentDeploy()) {
                        node.setRecentDeploy(false);
                        if (node.getActiveFailure() != FailureMode.HEALTHY) {
                            node.setActiveFailure(FailureMode.HEALTHY);
                            node.setFailureTick(0);
                            node.setCpu(0.25);
                            node.setMemory(0.3);
                            node.setErrorRate(0.02);
                            node.setLatency(0.12);
                        }
                    }
                }
            }
            case TRIGGER_CIRCUIT_BREAKER -> {
                // Isolate failing services to prevent cascade
                for (ServiceNode node : state.getTopology().getAllNodes()) {
                    if (node.getErrorRate() > 0.3) {
                        node.setErrorRate(0.1);  // Cap error rate
                        node.setLatency(Math.min(node.getLatency() + 0.1, 1.0));  // Added latency from breaker
                    }
                }
            }
            default -> {}
        }

        return cost;
    }

    /**
     * Evolve metrics for a node with an active failure.
     * Each failure mode has a different metric signature.
     */
    private void evolveFailureMetrics(ServiceNode node) {
        node.setFailureTick(node.getFailureTick() + 1);

        switch (node.getActiveFailure()) {
            case CPU_SPIKE -> {
                node.setCpu(Math.min(node.getCpu() + 0.08, 1.0));
                node.setMemory(lerp(node.getMemory(), 0.5, 0.05));
                node.setLatency(lerp(node.getLatency(), 0.5, 0.06));
                if (node.getCpu() > 0.9) {
                    node.setErrorRate(Math.min(node.getErrorRate() + 0.05, 1.0));
                }
                // After sustained CPU spike, may trigger memory pressure
                if (node.getFailureTick() > 10) {
                    node.setMemory(Math.min(node.getMemory() + 0.04, 1.0));
                }
            }
            case MEMORY_LEAK -> {
                node.setMemory(Math.min(node.getMemory() + 0.06, 1.0));
                node.setCpu(lerp(node.getCpu(), 0.6, 0.03));
                node.setLatency(lerp(node.getLatency(), 0.6, 0.04));
                if (node.getMemory() > 0.9) {
                    node.setErrorRate(Math.min(node.getErrorRate() + 0.08, 1.0));
                }
                // After sustained leak, may trigger OOM crash
                if (node.getMemory() >= 0.98 && node.getFailureTick() > 8) {
                    node.setActiveFailure(FailureMode.SERVICE_CRASH);
                    node.setFailureTick(0);
                }
            }
            case SERVICE_CRASH -> {
                node.setErrorRate(Math.min(node.getErrorRate() + 0.15, 1.0));
                node.setLatency(Math.min(node.getLatency() + 0.1, 1.0));
                node.setCpu(lerp(node.getCpu(), 0.1, 0.1));  // Dead service uses little CPU
                node.setMemory(lerp(node.getMemory(), 0.2, 0.05));
            }
            case NETWORK_PARTITION -> {
                node.setErrorRate(Math.min(node.getErrorRate() + 0.1, 1.0));
                node.setLatency(Math.min(node.getLatency() + 0.15, 1.0));
                node.setCpu(lerp(node.getCpu(), 0.5, 0.05));  // Retry storms
            }
            case DATABASE_DEADLOCK -> {
                node.setCpu(Math.min(node.getCpu() + 0.1, 1.0));
                node.setLatency(Math.min(node.getLatency() + 0.12, 1.0));
                node.setErrorRate(Math.min(node.getErrorRate() + 0.06, 1.0));
            }
            case CASCADING_FAILURE -> {
                node.setCpu(Math.min(node.getCpu() + 0.05, 1.0));
                node.setMemory(Math.min(node.getMemory() + 0.04, 1.0));
                node.setErrorRate(Math.min(node.getErrorRate() + 0.08, 1.0));
                node.setLatency(Math.min(node.getLatency() + 0.08, 1.0));
            }
            default -> {}
        }
    }

    /**
     * Propagate failures through the dependency graph.
     * When a service fails, its downstream dependents degrade after propagation delay.
     */
    private void propagateCascadingFailures(InfrastructureState state) {
        List<ServiceNode> failingNodes = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != FailureMode.HEALTHY)
                .toList();

        for (ServiceNode failingNode : failingNodes) {
            List<String> dependents = state.getTopology().getDownstreamDependents(failingNode.getServiceId());

            for (String dependentId : dependents) {
                ServiceNode dependent = state.getTopology().getNode(dependentId);
                if (dependent == null || dependent.getActiveFailure() != FailureMode.HEALTHY) {
                    continue;
                }

                // Downstream degradation: increase error rate and latency
                double severity = failingNode.getErrorRate() * 0.4;
                dependent.setErrorRate(dependent.getErrorRate() + severity);
                dependent.setLatency(dependent.getLatency() + severity * 0.5);

                // If upstream failure is severe enough, trigger cascading failure
                if (failingNode.getErrorRate() > 0.5 && failingNode.getFailureTick() > 3) {
                    if (dependent.getErrorRate() > 0.3) {
                        dependent.setActiveFailure(FailureMode.CASCADING_FAILURE);
                        dependent.setFailureTick(0);
                    }
                }
            }
        }
    }

    private ServiceNode findMostAffectedNode(InfrastructureState state) {
        return state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != FailureMode.HEALTHY)
                .max(Comparator.comparingDouble(ServiceNode::getErrorRate))
                .orElse(null);
    }

    private ServiceNode findLeastLoadedNode(InfrastructureState state) {
        return state.getTopology().getAllNodes().stream()
                .min(Comparator.comparingDouble(ServiceNode::getCpu))
                .orElse(null);
    }

    private double lerp(double current, double target, double rate) {
        return current + (target - current) * rate;
    }
}

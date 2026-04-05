package com.cloudsentinel.simulator.application;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.api.dto.ActionRequestDTO;
import com.cloudsentinel.api.dto.ObservationDTO;
import com.cloudsentinel.api.dto.StepResponseDTO;
import com.cloudsentinel.reward.engine.RewardCalculator;
import com.cloudsentinel.simulator.engine.CausalStateMachine;
import com.cloudsentinel.simulator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main simulation service that orchestrates state management,
 * action execution, failure injection, and reward computation.
 *
 * Thread-safe for concurrent access (multiple training sessions).
 */
@Slf4j
@Service
public class SimulationService {

    @Value("${simulator.max-episode-ticks:200}")
    private int maxEpisodeTicks;

    private final CausalStateMachine stateMachine;
    private final RewardCalculator rewardCalculator;

    // Current episode state (in-memory, per-session)
    private final AtomicReference<InfrastructureState> currentState = new AtomicReference<>();
    private final AtomicReference<DifficultyLevel> currentDifficulty = new AtomicReference<>(DifficultyLevel.SIMPLE);
    private volatile boolean episodeActive = false;

    // Track previous tick's average metrics for delta computation
    private volatile double[] previousMetrics = new double[4]; // cpu, memory, errorRate, latency

    public SimulationService(CausalStateMachine stateMachine, RewardCalculator rewardCalculator) {
        this.stateMachine = stateMachine;
        this.rewardCalculator = rewardCalculator;
    }

    /**
     * Reset the environment to a fresh initial state.
     */
    public ObservationDTO reset(DifficultyLevel difficulty) {
        currentDifficulty.set(difficulty);
        InfrastructureState state = stateMachine.createInitialState(difficulty, maxEpisodeTicks);
        currentState.set(state);
        episodeActive = true;

        // For non-simple difficulties, inject initial failure immediately
        if (difficulty != DifficultyLevel.SIMPLE) {
            stateMachine.injectFailure(state, difficulty);
        }

        // Initialize previous metrics from initial state
        previousMetrics = computeAverageMetrics(state);

        log.info("Environment reset: difficulty={}, services={}",
                difficulty, state.getTopology().getNodeCount());

        return buildObservation(state);
    }

    /**
     * Apply an action, advance the simulation, and return (observation, reward, done).
     */
    public StepResponseDTO step(int actionCode) {
        if (!episodeActive) {
            throw new IllegalStateException("No active episode. Call reset() first.");
        }

        InfrastructureState prevState = currentState.get();
        ActionType action = ActionType.fromCode(actionCode);

        // Apply action
        double actionCost = stateMachine.applyAction(prevState, action);

        // Save previous metrics before advancing
        previousMetrics = computeAverageMetrics(prevState);

        // Advance simulation tick
        stateMachine.advanceTick(prevState);

        // Compute reward
        double reward = rewardCalculator.computeReward(prevState, prevState, action, actionCost);

        InfrastructureState nextState = currentState.get();
        nextState.setCumulativeCost(nextState.getCumulativeCost() + actionCost);
        nextState.setCumulativeReward(nextState.getCumulativeReward() + reward);
        nextState.recordAction(actionCode, action.name(), reward);

        // Build response
        ObservationDTO obs = buildObservation(nextState);
        StepResponseDTO response = new StepResponseDTO();
        response.setObservation(obs.flatten());
        response.setReward(reward);
        response.setDone(nextState.isTerminal());
        response.setDoneReason(nextState.getTerminalReason());
        response.setSloScore(nextState.getSloScore());
        response.setCumulativeCost(nextState.getCumulativeCost());
        response.setCumulativeReward(nextState.getCumulativeReward());
        response.setTick(nextState.getTick());
        response.setActionName(action.name());

        // Report the most severe active failure
        FailureMode worstFailure = findWorstFailure(nextState);
        response.setFailureMode(worstFailure != null ? worstFailure.name() : FailureMode.HEALTHY.name());

        if (nextState.isTerminal()) {
            episodeActive = false;
            log.info("Episode terminated: reason={}, reward={}, slo={}, ticks={}",
                    nextState.getTerminalReason(),
                    nextState.getCumulativeReward(),
                    nextState.getSloScore(),
                    nextState.getTick());
        }

        return response;
    }

    /**
     * Get the current state snapshot without advancing.
     */
    public ObservationDTO getCurrentState() {
        InfrastructureState state = currentState.get();
        if (state == null) {
            throw new IllegalStateException("No active episode. Call reset() first.");
        }
        return buildObservation(state);
    }

    /**
     * Get the current InfrastructureState (for internal use).
     */
    public InfrastructureState getInternalState() {
        return currentState.get();
    }

    public boolean isEpisodeActive() { return episodeActive; }

    public DifficultyLevel getCurrentDifficulty() { return currentDifficulty.get(); }

    /**
     * Build the observation vector from the current infrastructure state.
     */
    private ObservationDTO buildObservation(InfrastructureState state) {
        ObservationDTO obs = new ObservationDTO();
        List<ServiceNode> nodes = state.getTopology().getAllNodes().stream().toList();
        int n = nodes.size();

        double[][] metrics = new double[n][4];
        String worstFailure = FailureMode.HEALTHY.name();
        int worstFailureIdx = -1;
        double worstErrorRate = 0.0;

        for (int i = 0; i < n; i++) {
            ServiceNode node = nodes.get(i);
            metrics[i][0] = node.getCpu();
            metrics[i][1] = node.getMemory();
            metrics[i][2] = node.getErrorRate();
            metrics[i][3] = node.getLatency();

            if (node.getErrorRate() > worstErrorRate) {
                worstErrorRate = node.getErrorRate();
                worstFailure = node.getActiveFailure().name();
                worstFailureIdx = i;
            }
        }

        obs.setNodeMetrics(metrics);
        obs.setTickRatio((double) state.getTick() / state.getMaxTicks());
        obs.setActionMask(computeActionMask(state));
        obs.setActiveFailure(worstFailure);
        obs.setActiveFailureServiceIndex(worstFailureIdx);

        // Compute max cascade depth
        double maxDepth = 0.0;
        for (ServiceNode node : nodes) {
            if (node.getActiveFailure() != FailureMode.HEALTHY) {
                var cascade = state.getTopology().getCascadeTargets(node.getServiceId());
                for (int depth : cascade.values()) {
                    maxDepth = Math.max(maxDepth, depth);
                }
            }
        }
        obs.setMaxCascadeDepth(maxDepth / Math.max(n, 1));  // Normalize

        // Set previous metrics for delta computation
        obs.setPreviousMetrics(previousMetrics.clone());

        return obs;
    }

    /**
     * Compute average metrics across all nodes in the topology.
     */
    private double[] computeAverageMetrics(InfrastructureState state) {
        List<ServiceNode> nodes = state.getTopology().getAllNodes().stream().toList();
        int n = nodes.size();
        if (n == 0) return new double[4];

        double sumCpu = 0, sumMem = 0, sumError = 0, sumLatency = 0;
        for (ServiceNode node : nodes) {
            sumCpu += node.getCpu();
            sumMem += node.getMemory();
            sumError += node.getErrorRate();
            sumLatency += node.getLatency();
        }

        return new double[]{
            sumCpu / n, sumMem / n, sumError / n, sumLatency / n
        };
    }

    /**
     * Compute action validity mask for the current state.
     */
    private boolean[] computeActionMask(InfrastructureState state) {
        boolean[] mask = new boolean[ActionType.size()];
        for (int i = 0; i < ActionType.size(); i++) {
            mask[i] = true;  // All actions valid by default
        }

        // DO_NOTHING is always valid
        mask[ActionType.DO_NOTHING.getCode()] = true;

        for (ServiceNode node : state.getTopology().getAllNodes()) {
            // SCALE_DOWN only valid if CPU < 40%
            if (node.getCpu() >= 0.4) {
                mask[ActionType.SCALE_DOWN.getCode()] = false;
            }

            // RESTART_DATABASE only valid if no DB was recently restarted
            if (node.isRecentlyRestarted() && node.getServiceId().contains("db")) {
                mask[ActionType.RESTART_DATABASE.getCode()] = false;
            }

            // ROLLBACK only valid if there was a recent deploy
            if (!node.isRecentDeploy()) {
                mask[ActionType.ROLLBACK_DEPLOYMENT.getCode()] = false;
            }
        }

        // REROUTE_TRAFFIC requires at least 2 healthy replicas
        long healthyCount = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() == FailureMode.HEALTHY)
                .count();
        if (healthyCount < 2) {
            mask[ActionType.REROUTE_TRAFFIC.getCode()] = false;
        }

        return mask;
    }

    private FailureMode findWorstFailure(InfrastructureState state) {
        return state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != FailureMode.HEALTHY)
                .max((a, b) -> Double.compare(a.getErrorRate(), b.getErrorRate()))
                .map(ServiceNode::getActiveFailure)
                .orElse(null);
    }
}

package com.cloudsentinel.simulator.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the complete state of the simulation at a given tick.
 * This is the observation vector source for the RL agent.
 */
public class InfrastructureState {
    private TopologyGraph topology;
    private int tick;
    private int maxTicks;
    private double cumulativeCost;
    private double cumulativeReward;
    private double sloScore;
    private boolean isTerminal;
    private String terminalReason;
    private Instant startedAt;
    private List<ActionRecord> actionHistory = new ArrayList<>();

    public InfrastructureState() {
        this.startedAt = Instant.now();
    }

    public InfrastructureState(TopologyGraph topology, int maxTicks) {
        this.topology = topology;
        this.maxTicks = maxTicks;
        this.tick = 0;
        this.cumulativeCost = 0.0;
        this.cumulativeReward = 0.0;
        this.sloScore = 1.0;
        this.isTerminal = false;
        this.startedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public TopologyGraph getTopology() { return topology; }
    public void setTopology(TopologyGraph topology) { this.topology = topology; }

    public int getTick() { return tick; }
    public void setTick(int tick) { this.tick = tick; }

    public int getMaxTicks() { return maxTicks; }
    public void setMaxTicks(int maxTicks) { this.maxTicks = maxTicks; }

    public double getCumulativeCost() { return cumulativeCost; }
    public void setCumulativeCost(double cumulativeCost) { this.cumulativeCost = cumulativeCost; }

    public double getCumulativeReward() { return cumulativeReward; }
    public void setCumulativeReward(double cumulativeReward) { this.cumulativeReward = cumulativeReward; }

    public double getSloScore() { return sloScore; }
    public void setSloScore(double sloScore) { this.sloScore = sloScore; }

    public boolean isTerminal() { return isTerminal; }
    public void setTerminal(boolean terminal) { isTerminal = terminal; }

    public String getTerminalReason() { return terminalReason; }
    public void setTerminalReason(String terminalReason) { this.terminalReason = terminalReason; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public List<ActionRecord> getActionHistory() { return actionHistory; }
    public void setActionHistory(List<ActionRecord> actionHistory) { this.actionHistory = actionHistory; }

    public void recordAction(int action, String actionName, double reward) {
        actionHistory.add(new ActionRecord(tick, action, actionName, reward));
    }

    /**
     * Check if the system is broadly healthy (all nodes below failure thresholds).
     */
    public boolean isSystemHealthy() {
        if (topology == null) return true;
        for (ServiceNode node : topology.getAllNodes()) {
            if (node.getActiveFailure() != FailureMode.HEALTHY) return false;
            if (node.getCpu() > 0.8 || node.getErrorRate() > 0.1) return false;
        }
        return true;
    }

    /**
     * Compute the average health score across all services.
     */
    public double getAverageHealth() {
        if (topology == null) return 1.0;
        return topology.getAllNodes().stream()
                .mapToDouble(ServiceNode::getHealthScore)
                .average()
                .orElse(1.0);
    }

    /**
     * Compute SLO score based on availability and error rate targets.
     */
    public void computeSloScore() {
        if (topology == null) {
            sloScore = 1.0;
            return;
        }
        double totalServices = topology.getNodeCount();
        double healthyServices = topology.getAllNodes().stream()
                .filter(n -> n.getActiveFailure() == FailureMode.HEALTHY && n.getErrorRate() < 0.001)
                .count();
        sloScore = healthyServices / totalServices;
    }

    public record ActionRecord(int tick, int action, String actionName, double reward) {}
}

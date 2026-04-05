package com.cloudsentinel.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response from a simulation step containing observation, reward, and episode status.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepResponseDTO {
    private double[] observation;         // Flattened observation vector for RL agent
    private double reward;
    private boolean done;
    private String doneReason;
    private double sloScore;
    private double cumulativeCost;
    private double cumulativeReward;
    private int tick;
    private String actionName;
    private String failureMode;

    public double[] getObservation() { return observation; }
    public void setObservation(double[] observation) { this.observation = observation; }

    public double getReward() { return reward; }
    public void setReward(double reward) { this.reward = reward; }

    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }

    public String getDoneReason() { return doneReason; }
    public void setDoneReason(String doneReason) { this.doneReason = doneReason; }

    public double getSloScore() { return sloScore; }
    public void setSloScore(double sloScore) { this.sloScore = sloScore; }

    public double getCumulativeCost() { return cumulativeCost; }
    public void setCumulativeCost(double cumulativeCost) { this.cumulativeCost = cumulativeCost; }

    public double getCumulativeReward() { return cumulativeReward; }
    public void setCumulativeReward(double cumulativeReward) { this.cumulativeReward = cumulativeReward; }

    public int getTick() { return tick; }
    public void setTick(int tick) { this.tick = tick; }

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public String getFailureMode() { return failureMode; }
    public void setFailureMode(String failureMode) { this.failureMode = failureMode; }
}

package com.cloudsentinel.executor.model;

import com.cloudsentinel.action.model.ActionType;

import java.time.Duration;
import java.time.Instant;

/**
 * Tracks the execution of an action and its outcome.
 */
public class ActionExecutionRecord {
    private String executionId;
    private Instant startedAt;
    private Instant completedAt;
    private ActionType action;
    private String targetServiceId;
    private String targetServiceName;
    private ExecutionStatus status;
    private String failureReason;
    private double costIncurred;

    // Before/after state snapshots
    private double beforeHealth;
    private double afterHealth;
    private double beforeCpu;
    private double afterCpu;
    private double beforeErrorRate;
    private double afterErrorRate;
    private double beforeLatency;
    private double afterLatency;

    // Effectiveness metrics
    private double healthDelta;
    private boolean effective;
    private String effectivenessReason;

    public enum ExecutionStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        BLOCKED_BY_COOLDOWN,
        BLOCKED_BY_PRECONDITION
    }

    public ActionExecutionRecord() {
        this.startedAt = Instant.now();
        this.status = ExecutionStatus.PENDING;
        this.executionId = "EXEC-" + System.currentTimeMillis();
    }

    public ActionExecutionRecord(ActionType action, String targetServiceId, String targetServiceName,
                                  double beforeHealth, double beforeCpu, double beforeErrorRate, double beforeLatency) {
        this();
        this.action = action;
        this.targetServiceId = targetServiceId;
        this.targetServiceName = targetServiceName;
        this.beforeHealth = beforeHealth;
        this.beforeCpu = beforeCpu;
        this.beforeErrorRate = beforeErrorRate;
        this.beforeLatency = beforeLatency;
    }

    // --- Getters and Setters ---

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public ActionType getAction() { return action; }
    public void setAction(ActionType action) { this.action = action; }

    public String getTargetServiceId() { return targetServiceId; }
    public void setTargetServiceId(String targetServiceId) { this.targetServiceId = targetServiceId; }

    public String getTargetServiceName() { return targetServiceName; }
    public void setTargetServiceName(String targetServiceName) { this.targetServiceName = targetServiceName; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public double getCostIncurred() { return costIncurred; }
    public void setCostIncurred(double costIncurred) { this.costIncurred = costIncurred; }

    public double getBeforeHealth() { return beforeHealth; }
    public void setBeforeHealth(double beforeHealth) { this.beforeHealth = beforeHealth; }

    public double getAfterHealth() { return afterHealth; }
    public void setAfterHealth(double afterHealth) { this.afterHealth = afterHealth; }

    public double getBeforeCpu() { return beforeCpu; }
    public void setBeforeCpu(double beforeCpu) { this.beforeCpu = beforeCpu; }

    public double getAfterCpu() { return afterCpu; }
    public void setAfterCpu(double afterCpu) { this.afterCpu = afterCpu; }

    public double getBeforeErrorRate() { return beforeErrorRate; }
    public void setBeforeErrorRate(double beforeErrorRate) { this.beforeErrorRate = beforeErrorRate; }

    public double getAfterErrorRate() { return afterErrorRate; }
    public void setAfterErrorRate(double afterErrorRate) { this.afterErrorRate = afterErrorRate; }

    public double getBeforeLatency() { return beforeLatency; }
    public void setBeforeLatency(double beforeLatency) { this.beforeLatency = beforeLatency; }

    public double getAfterLatency() { return afterLatency; }
    public void setAfterLatency(double afterLatency) { this.afterLatency = afterLatency; }

    public double getHealthDelta() { return healthDelta; }
    public void setHealthDelta(double healthDelta) { this.healthDelta = healthDelta; }

    public boolean isEffective() { return effective; }
    public void setEffective(boolean effective) { this.effective = effective; }

    public String getEffectivenessReason() { return effectivenessReason; }
    public void setEffectivenessReason(String effectivenessReason) { this.effectivenessReason = effectivenessReason; }

    /**
     * Complete the execution record with after-state and compute effectiveness.
     */
    public void complete(double afterHealth, double afterCpu, double afterErrorRate, double afterLatency,
                         double cost, boolean effective, String reason) {
        this.completedAt = Instant.now();
        this.afterHealth = afterHealth;
        this.afterCpu = afterCpu;
        this.afterErrorRate = afterErrorRate;
        this.afterLatency = afterLatency;
        this.costIncurred = cost;
        this.healthDelta = afterHealth - beforeHealth;
        this.effective = effective;
        this.effectivenessReason = reason;
        this.status = ExecutionStatus.COMPLETED;
    }

    /**
     * Mark execution as failed.
     */
    public void fail(String reason) {
        this.completedAt = Instant.now();
        this.status = ExecutionStatus.FAILED;
        this.failureReason = reason;
        this.effective = false;
        this.effectivenessReason = "Execution failed: " + reason;
    }

    /**
     * Mark execution as blocked.
     */
    public void block(ExecutionStatus blockStatus, String reason) {
        this.completedAt = Instant.now();
        this.status = blockStatus;
        this.failureReason = reason;
        this.effective = false;
        this.effectivenessReason = "Blocked: " + reason;
    }

    /**
     * Get execution duration in milliseconds.
     */
    public long getDurationMs() {
        if (completedAt == null) return -1;
        return Duration.between(startedAt, completedAt).toMillis();
    }

    @Override
    public String toString() {
        return String.format("ActionExecution[%s: %s on %s, status=%s, effective=%s, health_delta=%.3f]",
                executionId, action, targetServiceName, status, effective, healthDelta);
    }
}

package com.cloudsentinel.simulator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single service in the cloud topology.
 * All metrics are normalized to [0, 1] range for RL stability.
 */
public class ServiceNode {
    private String serviceId;
    private String serviceName;
    private List<String> upstreamDependencies = new ArrayList<>();
    private double cpu;           // 0.0 - 1.0
    private double memory;        // 0.0 - 1.0
    private double errorRate;     // 0.0 - 1.0
    private double latency;       // 0.0 - 1.0 (normalized to MAX_LATENCY)
    private FailureMode activeFailure = FailureMode.HEALTHY;
    private int failureTick = 0;
    private int ticksUntilCascade = 0;
    private boolean recentDeploy = false;
    private boolean recentlyRestarted = false;
    private int restartCooldown = 0;
    private boolean isHealthy = true;

    public ServiceNode() {}

    public ServiceNode(String serviceId, String serviceName) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
    }

    public ServiceNode(String serviceId, String serviceName, List<String> upstreamDependencies) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.upstreamDependencies = upstreamDependencies;
    }

    // --- Getters and Setters ---

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public List<String> getUpstreamDependencies() { return upstreamDependencies; }
    public void setUpstreamDependencies(List<String> upstreamDependencies) { this.upstreamDependencies = upstreamDependencies; }

    public double getCpu() { return cpu; }
    public void setCpu(double cpu) { this.cpu = Math.clamp(cpu, 0.0, 1.0); }

    public double getMemory() { return memory; }
    public void setMemory(double memory) { this.memory = Math.clamp(memory, 0.0, 1.0); }

    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = Math.clamp(errorRate, 0.0, 1.0); }

    public double getLatency() { return latency; }
    public void setLatency(double latency) { this.latency = Math.clamp(latency, 0.0, 1.0); }

    public FailureMode getActiveFailure() { return activeFailure; }
    public void setActiveFailure(FailureMode activeFailure) { this.activeFailure = activeFailure; }

    public int getFailureTick() { return failureTick; }
    public void setFailureTick(int failureTick) { this.failureTick = failureTick; }

    public int getTicksUntilCascade() { return ticksUntilCascade; }
    public void setTicksUntilCascade(int ticksUntilCascade) { this.ticksUntilCascade = ticksUntilCascade; }

    public boolean isRecentDeploy() { return recentDeploy; }
    public void setRecentDeploy(boolean recentDeploy) { this.recentDeploy = recentDeploy; }

    public boolean isRecentlyRestarted() { return recentlyRestarted; }
    public void setRecentlyRestarted(boolean recentlyRestarted) { this.recentlyRestarted = recentlyRestarted; }

    public int getRestartCooldown() { return restartCooldown; }
    public void setRestartCooldown(int restartCooldown) { this.restartCooldown = restartCooldown; }

    public boolean isHealthy() { return isHealthy; }
    public void setHealthy(boolean healthy) { isHealthy = healthy; }

    /**
     * Compute health score: 1.0 = perfectly healthy, 0.0 = completely failed.
     */
    public double getHealthScore() {
        return 1.0 - (cpu * 0.3 + memory * 0.25 + errorRate * 0.3 + latency * 0.15);
    }

    @Override
    public String toString() {
        return String.format("ServiceNode[%s: cpu=%.2f mem=%.2f err=%.2f lat=%.2f failure=%s]",
                serviceName, cpu, memory, errorRate, latency, activeFailure);
    }
}

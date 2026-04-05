package com.cloudsentinel.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Observation vector returned to the RL agent after each step.
 * All values are normalized to [0, 1].
 *
 * ENHANCED: Includes derived features for trend detection:
 * - Per-service: cpu, memory, errorRate, latency (raw metrics)
 * - Derived: avg_cpu_delta, avg_memory_delta, avg_error_delta (trends)
 * - Context: tickRatio, maxCascadeDepth, hasFailure, severityIndex
 */
public class ObservationDTO {
    private double[][] nodeMetrics;       // [nServices][4]: cpu, memory, errorRate, latency
    private double[] previousMetrics;     // [4]: avg cpu/mem/error/latency from previous tick
    private double tickRatio;             // tick / maxTicks
    private double maxCascadeDepth;
    private boolean[] actionMask;         // Valid actions for current state
    private String activeFailure;         // Failure mode name of most affected node
    private int activeFailureServiceIndex;

    public ObservationDTO() {}

    public double[][] getNodeMetrics() { return nodeMetrics; }
    public void setNodeMetrics(double[][] nodeMetrics) { this.nodeMetrics = nodeMetrics; }

    public double[] getPreviousMetrics() { return previousMetrics; }
    public void setPreviousMetrics(double[] previousMetrics) { this.previousMetrics = previousMetrics; }

    public double getTickRatio() { return tickRatio; }
    public void setTickRatio(double tickRatio) { this.tickRatio = tickRatio; }

    public double getMaxCascadeDepth() { return maxCascadeDepth; }
    public void setMaxCascadeDepth(double maxCascadeDepth) { this.maxCascadeDepth = maxCascadeDepth; }

    public boolean[] getActionMask() { return actionMask; }
    public void setActionMask(boolean[] actionMask) { this.actionMask = actionMask; }

    public String getActiveFailure() { return activeFailure; }
    public void setActiveFailure(String activeFailure) { this.activeFailure = activeFailure; }

    public int getActiveFailureServiceIndex() { return activeFailureServiceIndex; }
    public void setActiveFailureServiceIndex(int activeFailureServiceIndex) { this.activeFailureServiceIndex = activeFailureServiceIndex; }

    /**
     * Fixed-size observation vector: always 31 elements regardless of topology.
     * Layout: [7 services x 4 metrics] = 28
     *         + avgCpuDelta + avgMemoryDelta + avgErrorDelta = 3 (trend features)
     *         = 31
     *
     * Services beyond the actual topology count are zero-padded.
     * Delta features: (current_avg - previous_avg) normalized to [0,1] via (delta + 1)/2
     */
    public static final int MAX_SERVICES = 7;
    public static final int METRICS_PER_SERVICE = 4;
    public static final int OBS_SIZE = MAX_SERVICES * METRICS_PER_SERVICE + 3;  // 31

    public double[] flatten() {
        double[] flat = new double[OBS_SIZE];
        int idx = 0;

        // Raw metrics: [7 services x 4 metrics]
        int n = Math.min(nodeMetrics.length, MAX_SERVICES);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) {
                flat[idx++] = nodeMetrics[i][j];
            }
        }
        // Zero-pad remaining service slots (already zero from initialization)
        idx = MAX_SERVICES * METRICS_PER_SERVICE;  // idx = 28

        // Derived trend features: deltas from previous tick
        if (previousMetrics != null && previousMetrics.length == 4) {
            double currentAvgCpu = 0.0, currentAvgMem = 0.0, currentAvgError = 0.0;
            for (int i = 0; i < n; i++) {
                currentAvgCpu += nodeMetrics[i][0];
                currentAvgMem += nodeMetrics[i][1];
                currentAvgError += nodeMetrics[i][2];
            }
            if (n > 0) {
                currentAvgCpu /= n;
                currentAvgMem /= n;
                currentAvgError /= n;
            }

            // Normalize deltas to [0,1]: (delta + 1.0) / 2.0, clamped
            double cpuDelta = (currentAvgCpu - previousMetrics[0] + 1.0) / 2.0;
            double memDelta = (currentAvgMem - previousMetrics[1] + 1.0) / 2.0;
            double errorDelta = (currentAvgError - previousMetrics[2] + 1.0) / 2.0;

            flat[idx++] = Math.max(0.0, Math.min(1.0, cpuDelta));
            flat[idx++] = Math.max(0.0, Math.min(1.0, memDelta));
            flat[idx++] = Math.max(0.0, Math.min(1.0, errorDelta));
        } else {
            // No previous data (first tick): zero deltas
            flat[idx++] = 0.5;  // Neutral (no change)
            flat[idx++] = 0.5;
            flat[idx++] = 0.5;
        }

        return flat;
    }
}

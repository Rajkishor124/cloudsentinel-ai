package com.cloudsentinel.detector.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a detected anomaly with metadata.
 */
public class AnomalyAlert {

    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    private String alertId;
    private Instant timestamp;
    private String serviceId;
    private String serviceName;
    private AnomalyType anomalyType;
    private SeverityLevel severity;
    private double detectedValue;
    private double threshold;
    private String description;
    private List<String> recommendedActions = new ArrayList<>();
    private boolean acknowledged = false;
    private Instant resolvedAt;

    public enum AnomalyType {
        CPU_SPIKE("CPU usage exceeded threshold"),
        MEMORY_PRESSURE("Memory usage approaching limit"),
        HIGH_ERROR_RATE("Error rate above acceptable threshold"),
        LATENCY_DEGRADATION("Response time exceeding SLO"),
        CASCADE_RISK("Service at risk of cascading failure"),
        SERVICE_DEGRADATION("Overall service health declining"),
        MULTIPLE_FAILURES("Multiple simultaneous failures detected");

        private final String description;

        AnomalyType(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public AnomalyAlert() {
        this.timestamp = Instant.now();
        this.alertId = generateAlertId();
    }

    // --- Getters and Setters ---

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public AnomalyType getAnomalyType() { return anomalyType; }
    public void setAnomalyType(AnomalyType anomalyType) { this.anomalyType = anomalyType; }

    public SeverityLevel getSeverity() { return severity; }
    public void setSeverity(SeverityLevel severity) { this.severity = severity; }

    public double getDetectedValue() { return detectedValue; }
    public void setDetectedValue(double detectedValue) { this.detectedValue = detectedValue; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(List<String> recommendedActions) { this.recommendedActions = recommendedActions; }

    public void addRecommendedAction(String action) { this.recommendedActions.add(action); }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    private String generateAlertId() {
        return "ALERT-" + System.currentTimeMillis() + "-" + ID_COUNTER.incrementAndGet();
    }

    @Override
    public String toString() {
        return String.format("[%s] %s on %s: %.2f (threshold: %.2f) - %s",
                severity, anomalyType, serviceName, detectedValue, threshold, description);
    }
}

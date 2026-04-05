package com.cloudsentinel.detector.service;

import com.cloudsentinel.detector.model.AnomalyAlert;
import com.cloudsentinel.detector.model.AnomalyAlert.AnomalyType;
import com.cloudsentinel.detector.model.SeverityLevel;
import com.cloudsentinel.simulator.model.FailureMode;
import com.cloudsentinel.simulator.model.InfrastructureState;
import com.cloudsentinel.simulator.model.ServiceNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Monitors cloud metrics and detects anomalies across all services.
 * Detects CPU spikes, memory pressure, high error rates, latency degradation,
 * and cascading failure risks.
 */
@Slf4j
@Service
public class FailureDetector {

    // Detection thresholds
    private static final double CPU_WARNING_THRESHOLD = 0.6;
    private static final double CPU_CRITICAL_THRESHOLD = 0.85;
    private static final double MEMORY_WARNING_THRESHOLD = 0.7;
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.9;
    private static final double ERROR_RATE_WARNING_THRESHOLD = 0.05;
    private static final double ERROR_RATE_CRITICAL_THRESHOLD = 0.2;
    private static final double LATENCY_WARNING_THRESHOLD = 0.4;
    private static final double LATENCY_CRITICAL_THRESHOLD = 0.7;
    private static final double CASCADE_RISK_THRESHOLD = 0.3;

    /**
     * Scan all services and return list of detected anomalies.
     */
    public List<AnomalyAlert> detectAnomalies(InfrastructureState state) {
        if (state.getTopology() == null) {
            return List.of();
        }

        List<AnomalyAlert> alerts = new ArrayList<>();

        for (ServiceNode node : state.getTopology().getAllNodes()) {
            alerts.addAll(detectServiceAnomalies(node));
        }

        // Check for systemic issues
        alerts.addAll(detectSystemicAnomalies(state));

        // Log detected anomalies
        if (!alerts.isEmpty()) {
            log.info("Detected {} anomalies across services", alerts.size());
            for (AnomalyAlert alert : alerts) {
                log.debug("  {}", alert);
            }
        }

        return alerts;
    }

    /**
     * Detect anomalies for a single service.
     */
    private List<AnomalyAlert> detectServiceAnomalies(ServiceNode node) {
        List<AnomalyAlert> alerts = new ArrayList<>();

        // CPU detection
        if (node.getCpu() > CPU_CRITICAL_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.CPU_SPIKE, SeverityLevel.CRITICAL,
                    node.getCpu(), CPU_CRITICAL_THRESHOLD,
                    "CPU critically high at " + formatPercent(node.getCpu())));
        } else if (node.getCpu() > CPU_WARNING_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.CPU_SPIKE, SeverityLevel.HIGH,
                    node.getCpu(), CPU_WARNING_THRESHOLD,
                    "CPU elevated at " + formatPercent(node.getCpu())));
        }

        // Memory detection
        if (node.getMemory() > MEMORY_CRITICAL_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.MEMORY_PRESSURE, SeverityLevel.CRITICAL,
                    node.getMemory(), MEMORY_CRITICAL_THRESHOLD,
                    "Memory critically high at " + formatPercent(node.getMemory())));
        } else if (node.getMemory() > MEMORY_WARNING_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.MEMORY_PRESSURE, SeverityLevel.MEDIUM,
                    node.getMemory(), MEMORY_WARNING_THRESHOLD,
                    "Memory elevated at " + formatPercent(node.getMemory())));
        }

        // Error rate detection
        if (node.getErrorRate() > ERROR_RATE_CRITICAL_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.HIGH_ERROR_RATE, SeverityLevel.CRITICAL,
                    node.getErrorRate(), ERROR_RATE_CRITICAL_THRESHOLD,
                    "Error rate critically high at " + formatPercent(node.getErrorRate())));
        } else if (node.getErrorRate() > ERROR_RATE_WARNING_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.HIGH_ERROR_RATE, SeverityLevel.HIGH,
                    node.getErrorRate(), ERROR_RATE_WARNING_THRESHOLD,
                    "Error rate elevated at " + formatPercent(node.getErrorRate())));
        }

        // Latency detection
        if (node.getLatency() > LATENCY_CRITICAL_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.LATENCY_DEGRADATION, SeverityLevel.CRITICAL,
                    node.getLatency(), LATENCY_CRITICAL_THRESHOLD,
                    "Latency critically high at " + formatPercent(node.getLatency())));
        } else if (node.getLatency() > LATENCY_WARNING_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.LATENCY_DEGRADATION, SeverityLevel.MEDIUM,
                    node.getLatency(), LATENCY_WARNING_THRESHOLD,
                    "Latency elevated at " + formatPercent(node.getLatency())));
        }

        // Cascade risk detection
        if (node.getActiveFailure() != FailureMode.HEALTHY && node.getErrorRate() > CASCADE_RISK_THRESHOLD) {
            alerts.add(createAlert(node, AnomalyType.CASCADE_RISK, SeverityLevel.HIGH,
                    node.getErrorRate(), CASCADE_RISK_THRESHOLD,
                    "Service has active " + node.getActiveFailure() + " failure, cascade risk high"));
        }

        return alerts;
    }

    /**
     * Detect systemic anomalies (multiple failures, overall degradation).
     */
    private List<AnomalyAlert> detectSystemicAnomalies(InfrastructureState state) {
        List<AnomalyAlert> alerts = new ArrayList<>();

        long failingCount = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() != FailureMode.HEALTHY)
                .count();

        long totalServices = state.getTopology().getNodeCount();

        // Multiple simultaneous failures
        if (failingCount >= 3) {
            AnomalyAlert alert = new AnomalyAlert();
            alert.setAnomalyType(AnomalyType.MULTIPLE_FAILURES);
            alert.setSeverity(SeverityLevel.CRITICAL);
            alert.setDetectedValue(failingCount);
            alert.setThreshold(2.0);
            alert.setDescription(failingCount + " out of " + totalServices + " services failing simultaneously");
            alert.addRecommendedAction("TRIGGER_CIRCUIT_BREAKER");
            alert.addRecommendedAction("REROUTE_TRAFFIC");
            alert.addRecommendedAction("RESTART_SERVICE");
            alerts.add(alert);
        } else if (failingCount >= 2) {
            AnomalyAlert alert = new AnomalyAlert();
            alert.setAnomalyType(AnomalyType.MULTIPLE_FAILURES);
            alert.setSeverity(SeverityLevel.HIGH);
            alert.setDetectedValue(failingCount);
            alert.setThreshold(2.0);
            alert.setDescription(failingCount + " services failing, monitor for cascade");
            alert.addRecommendedAction("REROUTE_TRAFFIC");
            alert.addRecommendedAction("SCALE_UP");
            alerts.add(alert);
        }

        // Overall service degradation
        double avgHealth = state.getAverageHealth();
        if (avgHealth < 0.5) {
            AnomalyAlert alert = new AnomalyAlert();
            alert.setAnomalyType(AnomalyType.SERVICE_DEGRADATION);
            alert.setSeverity(SeverityLevel.HIGH);
            alert.setDetectedValue(avgHealth);
            alert.setThreshold(0.5);
            alert.setDescription("Average service health critically low at " + formatPercent(avgHealth));
            alert.addRecommendedAction("SCALE_UP");
            alert.addRecommendedAction("RESTART_SERVICE");
            alerts.add(alert);
        }

        return alerts;
    }

    /**
     * Create an anomaly alert with recommended actions.
     */
    private AnomalyAlert createAlert(ServiceNode node, AnomalyType type, SeverityLevel severity,
                                      double value, double threshold, String description) {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setServiceId(node.getServiceId());
        alert.setServiceName(node.getServiceName());
        alert.setAnomalyType(type);
        alert.setSeverity(severity);
        alert.setDetectedValue(value);
        alert.setThreshold(threshold);
        alert.setDescription(description);

        // Map anomaly to recommended actions
        mapActionsToAlert(alert, node, type);

        return alert;
    }

    /**
     * Map anomaly type to recommended remediation actions.
     */
    private void mapActionsToAlert(AnomalyAlert alert, ServiceNode node, AnomalyType type) {
        switch (type) {
            case CPU_SPIKE:
                alert.addRecommendedAction("SCALE_UP");
                alert.addRecommendedAction("RESTART_SERVICE");
                if (node.getMemory() < 0.5) {
                    alert.addRecommendedAction("CLEAR_CACHE");
                }
                break;

            case MEMORY_PRESSURE:
                alert.addRecommendedAction("CLEAR_CACHE");
                alert.addRecommendedAction("RESTART_SERVICE");
                break;

            case HIGH_ERROR_RATE:
                alert.addRecommendedAction("REROUTE_TRAFFIC");
                alert.addRecommendedAction("RESTART_SERVICE");
                alert.addRecommendedAction("TRIGGER_CIRCUIT_BREAKER");
                break;

            case LATENCY_DEGRADATION:
                alert.addRecommendedAction("SCALE_UP");
                alert.addRecommendedAction("REROUTE_TRAFFIC");
                break;

            case CASCADE_RISK:
                alert.addRecommendedAction("TRIGGER_CIRCUIT_BREAKER");
                alert.addRecommendedAction("REROUTE_TRAFFIC");
                alert.addRecommendedAction("RESTART_SERVICE");
                break;

            default:
                alert.addRecommendedAction("RESTART_SERVICE");
        }
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    /**
     * Get the most severe alert from a list.
     */
    public AnomalyAlert getMostSevereAlert(List<AnomalyAlert> alerts) {
        if (alerts.isEmpty()) return null;
        return alerts.stream()
                .max((a, b) -> Integer.compare(a.getSeverity().getLevel(), b.getSeverity().getLevel()))
                .orElse(null);
    }

    /**
     * Count alerts by severity level.
     */
    public int countBySeverity(List<AnomalyAlert> alerts, SeverityLevel severity) {
        return (int) alerts.stream()
                .filter(a -> a.getSeverity() == severity)
                .count();
    }

    /**
     * Get alerts for a specific service.
     */
    public List<AnomalyAlert> getAlertsForService(List<AnomalyAlert> alerts, String serviceId) {
        return alerts.stream()
                .filter(a -> serviceId.equals(a.getServiceId()))
                .collect(Collectors.toList());
    }
}

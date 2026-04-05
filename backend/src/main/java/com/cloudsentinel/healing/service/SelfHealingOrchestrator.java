package com.cloudsentinel.healing.service;

import com.cloudsentinel.action.model.ActionType;
import com.cloudsentinel.decision.model.Decision;
import com.cloudsentinel.decision.service.HybridDecisionEngine;
import com.cloudsentinel.detector.model.AnomalyAlert;
import com.cloudsentinel.detector.service.FailureDetector;
import com.cloudsentinel.executor.model.ActionExecutionRecord;
import com.cloudsentinel.executor.service.ActionExecutor;
import com.cloudsentinel.feedback.model.FeedbackEntry;
import com.cloudsentinel.feedback.service.FeedbackLearningService;
import com.cloudsentinel.memory.model.HealingMemoryEntry;
import com.cloudsentinel.memory.service.HealingMemoryService;
import com.cloudsentinel.simulator.model.InfrastructureState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main orchestrator for the self-healing AI system.
 * Coordinates monitoring, decision-making, action execution, and learning.
 *
 * Healing cycle:
 * 1. Monitor: Detect anomalies
 * 2. Decide: Hybrid decision (RL + rules)
 * 3. Execute: Apply remediation action
 * 4. Learn: Record feedback and store in memory
 * 5. Adapt: Use historical data to improve future decisions
 */
@Slf4j
@Service
public class SelfHealingOrchestrator {

    private final FailureDetector failureDetector;
    private final HybridDecisionEngine decisionEngine;
    private final ActionExecutor actionExecutor;
    private final FeedbackLearningService feedbackService;
    private final HealingMemoryService memoryService;

    // Healing cycle state
    private final AtomicBoolean healingActive = new AtomicBoolean(false);
    private final AtomicInteger totalHealingCycles = new AtomicInteger(0);
    private final AtomicInteger successfulHealingCycles = new AtomicInteger(0);
    private final List<HealingCycleReport> recentCycles = Collections.synchronizedList(new ArrayList<>());

    public SelfHealingOrchestrator(
            FailureDetector failureDetector,
            HybridDecisionEngine decisionEngine,
            ActionExecutor actionExecutor,
            FeedbackLearningService feedbackService,
            HealingMemoryService memoryService) {
        this.failureDetector = failureDetector;
        this.decisionEngine = decisionEngine;
        this.actionExecutor = actionExecutor;
        this.feedbackService = feedbackService;
        this.memoryService = memoryService;

        log.info("Self-Healing Orchestrator initialized");
    }

    /**
     * Execute one complete healing cycle.
     * Called by the scheduled task or manually via API.
     */
    public HealingCycleReport executeHealingCycle(InfrastructureState state, int rlAction) {
        if (healingActive.compareAndSet(false, true)) {
            try {
                return doHealingCycle(state, rlAction);
            } finally {
                healingActive.set(false);
            }
        } else {
            log.warn("Healing cycle already in progress, skipping");
            return HealingCycleReport.skipped("Cycle already in progress");
        }
    }

    private HealingCycleReport doHealingCycle(InfrastructureState state, int rlAction) {
        long startTime = System.currentTimeMillis();
        totalHealingCycles.incrementAndGet();

        log.info("=== Starting Healing Cycle #{} ===", totalHealingCycles.get());

        // Step 1: Monitor - Detect anomalies
        log.info("[1/4] Monitoring: Scanning for anomalies...");
        List<AnomalyAlert> alerts = failureDetector.detectAnomalies(state);
        log.info("  Detected {} anomalies", alerts.size());

        if (alerts.isEmpty()) {
            log.info("  System healthy, no action needed");
            HealingCycleReport report = HealingCycleReport.completed(
                    totalHealingCycles.get(), alerts, null, null, null,
                    System.currentTimeMillis() - startTime, "System healthy");
            recentCycles.add(report);
            return report;
        }

        // Step 2: Decide - Hybrid decision (RL + rules)
        log.info("[2/4] Deciding: Evaluating actions...");
        Decision decision = decisionEngine.makeDecision(rlAction, state, alerts);
        log.info("  Decision: {} (source: {}, confidence: {:.2f})",
                decision.getRecommendedAction(), decision.getSource(), decision.getConfidence());

        // Check memory for similar experiences
        if (!alerts.isEmpty()) {
            ActionType bestHistorical = memoryService.getBestHistoricalAction(alerts.get(0).getAnomalyType());
            if (bestHistorical != null) {
                log.info("  Memory suggests: {} (based on past experience)", bestHistorical);
            }
        }

        // Step 3: Execute - Apply action
        log.info("[3/4] Executing: Applying {}...", decision.getRecommendedAction());
        ActionExecutionRecord execution = actionExecutor.execute(decision.getRecommendedAction(), state);
        log.info("  Execution: {} (effective: {})", execution.getStatus(), execution.isEffective());

        // Step 4: Learn - Record feedback
        log.info("[4/4] Learning: Recording feedback...");
        double reward = state.getCumulativeReward() - (state.getCumulativeReward() - execution.getCostIncurred());
        FeedbackEntry feedback = feedbackService.recordFeedback(
                decision, alerts, execution, state, reward);

        // Store in memory
        HealingMemoryEntry memory = memoryService.storeExperience(feedback, "SIMPLE");
        log.info("  Memory stored: {} (lesson: {})", memory.getEpisodeId(), memory.getLesson());

        // Build report
        long duration = System.currentTimeMillis() - startTime;
        boolean success = execution.isEffective() || alerts.isEmpty();
        if (success) successfulHealingCycles.incrementAndGet();

        HealingCycleReport report = HealingCycleReport.completed(
                totalHealingCycles.get(), alerts, decision, execution, feedback,
                duration, success ? "Healing successful" : "Healing ineffective");
        recentCycles.add(report);

        log.info("=== Healing Cycle #{} Complete (duration: {}ms, effective: {}) ===",
                totalHealingCycles.get(), duration, success);

        return report;
    }

    /**
     * Get orchestrator status for dashboard.
     */
    public OrchestratorStatus getStatus() {
        OrchestratorStatus status = new OrchestratorStatus();
        status.healingActive = healingActive.get();
        status.totalCycles = totalHealingCycles.get();
        status.successfulCycles = successfulHealingCycles.get();
        status.successRate = totalHealingCycles.get() > 0 ?
                (double) successfulHealingCycles.get() / totalHealingCycles.get() : 0.0;
        status.memoryStatistics = memoryService.getStatistics();
        status.learningMetrics = feedbackService.getLearningMetrics();
        status.recentCycles = recentCycles.size() > 10 ?
                recentCycles.subList(recentCycles.size() - 10, recentCycles.size()) :
                new ArrayList<>(recentCycles);
        return status;
    }

    /**
     * Get recent healing cycle reports.
     */
    public List<HealingCycleReport> getRecentCycles(int limit) {
        int size = recentCycles.size();
        return recentCycles.subList(Math.max(0, size - limit), size);
    }

    /**
     * Healing cycle report for API/dashboard.
     */
    public static class HealingCycleReport {
        public int cycleNumber;
        public long timestamp;
        public long durationMs;
        public int anomalyCount;
        public List<AnomalyAlert> detectedAnomalies;
        public Decision decision;
        public ActionExecutionRecord execution;
        public FeedbackEntry feedback;
        public String outcome;
        public boolean successful;

        public static HealingCycleReport completed(int cycleNumber, List<AnomalyAlert> alerts,
                                                     Decision decision, ActionExecutionRecord execution,
                                                     FeedbackEntry feedback, long durationMs, String outcome) {
            HealingCycleReport report = new HealingCycleReport();
            report.cycleNumber = cycleNumber;
            report.timestamp = System.currentTimeMillis();
            report.durationMs = durationMs;
            report.anomalyCount = alerts.size();
            report.detectedAnomalies = alerts;
            report.decision = decision;
            report.execution = execution;
            report.feedback = feedback;
            report.outcome = outcome;
            report.successful = outcome.contains("successful");
            return report;
        }

        public static HealingCycleReport skipped(String reason) {
            HealingCycleReport report = new HealingCycleReport();
            report.timestamp = System.currentTimeMillis();
            report.outcome = "Skipped: " + reason;
            report.successful = false;
            return report;
        }
    }

    /**
     * Orchestrator status for dashboard.
     */
    public static class OrchestratorStatus {
        public boolean healingActive;
        public int totalCycles;
        public int successfulCycles;
        public double successRate;
        public com.cloudsentinel.memory.service.HealingMemoryService.MemoryStatistics memoryStatistics;
        public com.cloudsentinel.feedback.service.FeedbackLearningService.LearningMetrics learningMetrics;
        public List<HealingCycleReport> recentCycles;
    }
}

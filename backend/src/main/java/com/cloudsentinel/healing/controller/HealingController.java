package com.cloudsentinel.healing.controller;

import com.cloudsentinel.detector.model.AnomalyAlert;
import com.cloudsentinel.detector.service.FailureDetector;
import com.cloudsentinel.feedback.service.FeedbackLearningService;
import com.cloudsentinel.healing.service.SelfHealingOrchestrator;
import com.cloudsentinel.memory.service.HealingMemoryService;
import com.cloudsentinel.simulator.application.SimulationService;
import com.cloudsentinel.simulator.model.InfrastructureState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the self-healing AI system.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/healing")
@RequiredArgsConstructor
public class HealingController {

    private final SelfHealingOrchestrator orchestrator;
    private final FailureDetector failureDetector;
    private final SimulationService simulationService;
    private final HealingMemoryService memoryService;
    private final FeedbackLearningService feedbackService;

    /**
     * Execute one self-healing cycle.
     * POST /api/v1/healing/cycle?rlAction=0
     */
    @PostMapping("/cycle")
    public ResponseEntity<SelfHealingOrchestrator.HealingCycleReport> executeCycle(
            @RequestParam(defaultValue = "0") int rlAction) {
        InfrastructureState state = simulationService.getInternalState();
        if (state == null) {
            return ResponseEntity.badRequest().build();
        }

        SelfHealingOrchestrator.HealingCycleReport report = orchestrator.executeHealingCycle(state, rlAction);
        return ResponseEntity.ok(report);
    }

    /**
     * Get orchestrator status and statistics.
     * GET /api/v1/healing/status
     */
    @GetMapping("/status")
    public ResponseEntity<SelfHealingOrchestrator.OrchestratorStatus> getStatus() {
        return ResponseEntity.ok(orchestrator.getStatus());
    }

    /**
     * Get current anomaly alerts.
     * GET /api/v1/healing/alerts
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<AnomalyAlert>> getAlerts() {
        InfrastructureState state = simulationService.getInternalState();
        if (state == null) {
            return ResponseEntity.badRequest().build();
        }

        List<AnomalyAlert> alerts = failureDetector.detectAnomalies(state);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get healing memory statistics.
     * GET /api/v1/healing/memory/stats
     */
    @GetMapping("/memory/stats")
    public ResponseEntity<HealingMemoryService.MemoryStatistics> getMemoryStats() {
        return ResponseEntity.ok(memoryService.getStatistics());
    }

    /**
     * Get feedback learning metrics.
     * GET /api/v1/healing/feedback/metrics
     */
    @GetMapping("/feedback/metrics")
    public ResponseEntity<FeedbackLearningService.LearningMetrics> getFeedbackMetrics() {
        return ResponseEntity.ok(feedbackService.getLearningMetrics());
    }

    /**
     * Get recent healing cycles.
     * GET /api/v1/healing/recent?limit=10
     */
    @GetMapping("/recent")
    public ResponseEntity<List<SelfHealingOrchestrator.HealingCycleReport>> getRecentCycles(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(orchestrator.getRecentCycles(limit));
    }
}

package com.cloudsentinel.simulator.controller;

import com.cloudsentinel.api.dto.ActionRequestDTO;
import com.cloudsentinel.api.dto.ObservationDTO;
import com.cloudsentinel.api.dto.StepResponseDTO;
import com.cloudsentinel.simulator.application.SimulationService;
import com.cloudsentinel.simulator.model.DifficultyLevel;
import com.cloudsentinel.simulator.model.InfrastructureState;
import com.cloudsentinel.simulator.model.ServiceNode;
import com.cloudsentinel.simulator.model.TopologyGraph;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for the simulation environment.
 * These endpoints are used by the Python RL agent (via HTTP) and the dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulationService simulationService;

    /**
     * Reset the environment. Returns initial observation.
     * POST /api/v1/simulation/reset?difficulty=SIMPLE
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(
            @RequestParam(defaultValue = "SIMPLE") DifficultyLevel difficulty) {
        ObservationDTO obs = simulationService.reset(difficulty);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("observation", obs.flatten());
        response.put("observation_shape", obs.flatten().length);
        response.put("action_mask", obs.getActionMask());
        response.put("num_services", obs.getNodeMetrics().length);
        response.put("difficulty", difficulty.name());

        return ResponseEntity.ok(response);
    }

    /**
     * Apply an action and advance the simulation.
     * POST /api/v1/simulation/step
     */
    @PostMapping("/step")
    public ResponseEntity<StepResponseDTO> step(@Valid @RequestBody ActionRequestDTO request) {
        StepResponseDTO response = simulationService.step(request.getAction());
        return ResponseEntity.ok(response);
    }

    /**
     * Get the current state snapshot.
     * GET /api/v1/simulation/state
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getState() {
        ObservationDTO obs = simulationService.getCurrentState();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("observation", obs.flatten());
        response.put("tick_ratio", obs.getTickRatio());
        response.put("active_failure", obs.getActiveFailure());
        response.put("cascade_depth", obs.getMaxCascadeDepth());
        response.put("action_mask", obs.getActionMask());

        return ResponseEntity.ok(response);
    }

    /**
     * Get the service dependency topology.
     * GET /api/v1/simulation/topology
     */
    @GetMapping("/topology")
    public ResponseEntity<Map<String, Object>> getTopology() {
        InfrastructureState state = simulationService.getInternalState();
        if (state == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No active episode"));
        }

        TopologyGraph topology = state.getTopology();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (ServiceNode node : topology.getAllNodes()) {
            Map<String, Object> nodeData = new LinkedHashMap<>();
            nodeData.put("id", node.getServiceId());
            nodeData.put("name", node.getServiceName());
            nodeData.put("cpu", node.getCpu());
            nodeData.put("memory", node.getMemory());
            nodeData.put("errorRate", node.getErrorRate());
            nodeData.put("latency", node.getLatency());
            nodeData.put("failureMode", node.getActiveFailure().name());
            nodeData.put("healthScore", node.getHealthScore());
            nodes.add(nodeData);

            for (String dep : node.getUpstreamDependencies()) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", node.getServiceId());
                edge.put("target", dep);
                edges.add(edge);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nodes", nodes);
        response.put("edges", edges);
        response.put("total_services", nodes.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Manual failure injection for testing.
     * If no healthy nodes are available, recovers the least-affected node first.
     * POST /api/v1/simulation/inject-failure?mode=CPU_SPIKE
     */
    @PostMapping("/inject-failure")
    public ResponseEntity<Map<String, Object>> injectFailure(
            @RequestParam(defaultValue = "CPU_SPIKE") String mode) {
        InfrastructureState state = simulationService.getInternalState();
        if (state == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No active episode"));
        }

        try {
            com.cloudsentinel.simulator.model.FailureMode failureMode =
                    com.cloudsentinel.simulator.model.FailureMode.valueOf(mode);

            // Find a healthy node to inject into
            Optional<ServiceNode> target = state.getTopology().getAllNodes().stream()
                    .filter(n -> n.getActiveFailure() == com.cloudsentinel.simulator.model.FailureMode.HEALTHY)
                    .findFirst();

            // If no healthy nodes, recover the least-affected node first
            if (target.isEmpty()) {
                Optional<ServiceNode> leastAffected = state.getTopology().getAllNodes().stream()
                        .filter(n -> !n.getActiveFailure().name().contains("CRASH"))
                        .filter(n -> !n.getActiveFailure().name().contains("DEADLOCK"))
                        .min(Comparator.comparingDouble(ServiceNode::getErrorRate));

                if (leastAffected.isPresent()) {
                    ServiceNode node = leastAffected.get();
                    // Recover the node first
                    node.setActiveFailure(com.cloudsentinel.simulator.model.FailureMode.HEALTHY);
                    node.setFailureTick(0);
                    node.setCpu(0.2);
                    node.setMemory(0.3);
                    node.setErrorRate(0.0);
                    node.setLatency(0.1);
                    node.setRestartCooldown(0);

                    log.info("Recovered {} from {} to inject new failure", 
                            node.getServiceName(), node.getActiveFailure());

                    // Now inject the requested failure
                    node.setActiveFailure(failureMode);
                    node.setFailureTick(0);
                    applyFailureSignatureToNode(node, failureMode);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("injected", true);
                    response.put("mode", failureMode.name());
                    response.put("target", node.getServiceId());
                    response.put("recoveredFirst", true);

                    return ResponseEntity.ok(response);
                }

                return ResponseEntity.badRequest().body(Map.of("error", "No healthy nodes available and none could be recovered"));
            }

            ServiceNode node = target.get();
            node.setActiveFailure(failureMode);
            node.setFailureTick(0);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("injected", true);
            response.put("mode", failureMode.name());
            response.put("target", node.getServiceId());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid failure mode: " + mode));
        }
    }

    /**
     * Apply failure signature metrics to a node.
     */
    private void applyFailureSignatureToNode(ServiceNode node, com.cloudsentinel.simulator.model.FailureMode failure) {
        switch (failure) {
            case CPU_SPIKE -> {
                node.setCpu(0.5);
                node.setMemory(0.35);
                node.setErrorRate(0.02);
                node.setLatency(0.2);
            }
            case MEMORY_LEAK -> {
                node.setCpu(0.3);
                node.setMemory(0.55);
                node.setErrorRate(0.01);
                node.setLatency(0.15);
            }
            case SERVICE_CRASH -> {
                node.setCpu(0.1);
                node.setMemory(0.2);
                node.setErrorRate(0.5);
                node.setLatency(0.8);
            }
            case NETWORK_PARTITION -> {
                node.setCpu(0.3);
                node.setMemory(0.35);
                node.setErrorRate(0.3);
                node.setLatency(0.6);
            }
            case DATABASE_DEADLOCK -> {
                node.setCpu(0.6);
                node.setMemory(0.5);
                node.setErrorRate(0.15);
                node.setLatency(0.5);
            }
            case CASCADING_FAILURE -> {
                node.setCpu(0.4);
                node.setMemory(0.45);
                node.setErrorRate(0.25);
                node.setLatency(0.4);
            }
            default -> {}
        }
    }

    /**
     * Get environment metadata (observation size, action count, etc.)
     * GET /api/v1/simulation/metadata
     */
    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("action_count", com.cloudsentinel.action.model.ActionType.size());
        meta.put("action_names", Arrays.stream(com.cloudsentinel.action.model.ActionType.values())
                .map(Enum::name)
                .toList());
        meta.put("difficulty_levels", Arrays.stream(DifficultyLevel.values())
                .map(Enum::name)
                .toList());

        // Estimate observation size (will be accurate after reset)
        meta.put("observation_size", "depends_on_topology_after_reset");

        return ResponseEntity.ok(meta);
    }
}

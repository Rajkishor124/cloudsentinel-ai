package com.cloudsentinel.simulator.engine;

import com.cloudsentinel.simulator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Injects failures into the simulation topology based on difficulty level.
 * Uses deterministic selection for reproducibility (seeded randomness).
 */
@Slf4j
@Service
public class FailureInjector {

    private static final Random RANDOM = new Random(42);

    /**
     * Inject failure(s) based on difficulty level.
     */
    public FailureMode injectFailure(InfrastructureState state, DifficultyLevel difficulty) {
        return switch (difficulty) {
            case SIMPLE -> injectSimpleFailure(state);
            case MEDIUM -> injectMediumFailure(state);
            case COMPLEX -> injectComplexFailure(state);
            case ADVERSARIAL -> injectAdversarialFailure(state);
        };
    }

    /**
     * Level 1: Single failure on a single service.
     */
    private FailureMode injectSimpleFailure(InfrastructureState state) {
        List<ServiceNode> healthyNodes = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() == FailureMode.HEALTHY)
                .toList();

        if (healthyNodes.isEmpty()) return FailureMode.HEALTHY;

        ServiceNode target = healthyNodes.get(RANDOM.nextInt(healthyNodes.size()));
        FailureMode failure = randomSimpleFailure();

        target.setActiveFailure(failure);
        target.setFailureTick(0);

        // Set initial failure metrics
        applyFailureSignature(target, failure);

        log.debug("Injected {} into {}", failure, target.getServiceName());
        return failure;
    }

    /**
     * Level 2: Multiple simultaneous failures on different services.
     */
    private FailureMode injectMediumFailure(InfrastructureState state) {
        List<ServiceNode> healthyNodes = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() == FailureMode.HEALTHY)
                .toList();

        if (healthyNodes.size() < 2) return injectSimpleFailure(state);

        // Pick 2-3 random failures (create mutable copy for shuffle)
        int numFailures = Math.min(2 + RANDOM.nextInt(2), healthyNodes.size());
        List<ServiceNode> mutableNodes = new ArrayList<>(healthyNodes);
        Collections.shuffle(mutableNodes);

        FailureMode primaryFailure = null;
        for (int i = 0; i < numFailures; i++) {
            ServiceNode node = mutableNodes.get(i);
            FailureMode failure = randomMediumFailure();
            node.setActiveFailure(failure);
            node.setFailureTick(0);
            applyFailureSignature(node, failure);

            if (i == 0) primaryFailure = failure;
            log.debug("Injected {} into {}", failure, node.getServiceName());
        }

        return primaryFailure != null ? primaryFailure : FailureMode.CPU_SPIKE;
    }

    /**
     * Level 3: Cascading failure starting from a critical service.
     */
    private FailureMode injectComplexFailure(InfrastructureState state) {
        // Start with a database or core service failure
        Optional<ServiceNode> dbNode = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getServiceId().contains("db") && n.getActiveFailure() == FailureMode.HEALTHY)
                .findFirst();

        if (dbNode.isPresent()) {
            ServiceNode db = dbNode.get();
            db.setActiveFailure(FailureMode.DATABASE_DEADLOCK);
            db.setFailureTick(0);
            applyFailureSignature(db, FailureMode.DATABASE_DEADLOCK);
            log.debug("Injected DATABASE_DEADLOCK into {}", db.getServiceName());

            // Also inject a network partition on a dependent service
            Map<String, Integer> cascadeTargets = state.getTopology().getCascadeTargets(db.getServiceId());
            if (!cascadeTargets.isEmpty()) {
                String firstDependent = cascadeTargets.keySet().iterator().next();
                ServiceNode dependent = state.getTopology().getNode(firstDependent);
                if (dependent != null && dependent.getActiveFailure() == FailureMode.HEALTHY) {
                    dependent.setActiveFailure(FailureMode.NETWORK_PARTITION);
                    dependent.setFailureTick(0);
                    applyFailureSignature(dependent, FailureMode.NETWORK_PARTITION);
                    log.debug("Injected NETWORK_PARTITION into {}", dependent.getServiceName());
                }
            }

            return FailureMode.DATABASE_DEADLOCK;
        }

        // Fallback: inject CPU spike on the gateway
        return injectSimpleFailure(state);
    }

    /**
     * Level 4: Adversarial - targets the service the agent is least equipped to handle.
     * For now, uses the most complex failure pattern.
     */
    private FailureMode injectAdversarialFailure(InfrastructureState state) {
        // Inject failures on multiple critical path services simultaneously
        List<ServiceNode> criticalNodes = state.getTopology().getAllNodes().stream()
                .filter(n -> n.getActiveFailure() == FailureMode.HEALTHY)
                .filter(n -> n.getServiceId().contains("db") ||
                             n.getServiceId().contains("gateway") ||
                             n.getServiceId().contains("auth"))
                .toList();

        if (criticalNodes.isEmpty()) {
            return injectComplexFailure(state);
        }

        // Fail the most critical node with the hardest-to-recover failure
        ServiceNode target = criticalNodes.get(0);
        target.setActiveFailure(FailureMode.SERVICE_CRASH);
        target.setFailureTick(0);
        applyFailureSignature(target, FailureMode.SERVICE_CRASH);

        // Also inject a secondary failure
        if (criticalNodes.size() > 1) {
            ServiceNode secondary = criticalNodes.get(1);
            secondary.setActiveFailure(FailureMode.MEMORY_LEAK);
            secondary.setFailureTick(0);
            applyFailureSignature(secondary, FailureMode.MEMORY_LEAK);
        }

        log.debug("Adversarial: crashed {} and memory leak on {}",
                target.getServiceName(),
                criticalNodes.size() > 1 ? criticalNodes.get(1).getServiceName() : "N/A");

        return FailureMode.SERVICE_CRASH;
    }

    private FailureMode randomSimpleFailure() {
        FailureMode[] modes = {FailureMode.CPU_SPIKE, FailureMode.MEMORY_LEAK, FailureMode.SERVICE_CRASH};
        return modes[RANDOM.nextInt(modes.length)];
    }

    private FailureMode randomMediumFailure() {
        FailureMode[] modes = {FailureMode.CPU_SPIKE, FailureMode.MEMORY_LEAK,
                FailureMode.SERVICE_CRASH, FailureMode.NETWORK_PARTITION};
        return modes[RANDOM.nextInt(modes.length)];
    }

    private void applyFailureSignature(ServiceNode node, FailureMode failure) {
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
}

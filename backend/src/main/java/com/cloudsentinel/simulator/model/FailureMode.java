package com.cloudsentinel.simulator.model;

/**
 * Represents the failure modes that can affect a service node.
 * Failures propagate through the dependency graph with configurable delays.
 */
public enum FailureMode {
    HEALTHY,
    CPU_SPIKE,
    MEMORY_LEAK,
    SERVICE_CRASH,
    NETWORK_PARTITION,
    DATABASE_DEADLOCK,
    CASCADING_FAILURE
}

package com.cloudsentinel.simulator.model;

/**
 * Difficulty levels for curriculum learning.
 * Each level controls failure complexity, topology size, and adversarial behavior.
 */
public enum DifficultyLevel {
    SIMPLE,       // Single failure, single service
    MEDIUM,       // Multiple simultaneous failures, 3-4 services
    COMPLEX,      // Cascading failures, 7-service distributed topology
    ADVERSARIAL   // Failure injector targets agent's weakest failure modes
}

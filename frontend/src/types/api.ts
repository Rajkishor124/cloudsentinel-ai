/**
 * TypeScript type definitions for the CloudSentinel AI API.
 * Mirrors backend DTOs and response structures.
 */

// ============ Simulator Types ============

export interface ServiceNode {
    id: string;
    name: string;
    cpu: number;
    memory: number;
    errorRate: number;
    latency: number;
    failureMode: FailureMode;
    healthScore: number;
}

export type FailureMode =
    | 'HEALTHY'
    | 'CPU_SPIKE'
    | 'MEMORY_LEAK'
    | 'SERVICE_CRASH'
    | 'NETWORK_PARTITION'
    | 'DATABASE_DEADLOCK'
    | 'CASCADING_FAILURE';

export interface TopologyResponse {
    nodes: ServiceNode[];
    edges: Array<{ source: string; target: string }>;
    total_services: number;
}

export interface SimulationState {
    observation: number[];
    tick_ratio: number;
    active_failure: string;
    cascade_depth: number;
    action_mask: boolean[];
}

export interface StepResponse {
    observation: number[];
    reward: number;
    done: boolean;
    doneReason?: string;
    sloScore: number;
    cumulativeCost: number;
    cumulativeReward: number;
    tick: number;
    actionName: string;
    failureMode: string;
}

export type DifficultyLevel = 'SIMPLE' | 'MEDIUM' | 'COMPLEX' | 'ADVERSARIAL';

export type ActionType =
    | 'DO_NOTHING'
    | 'RESTART_SERVICE'
    | 'SCALE_UP'
    | 'SCALE_DOWN'
    | 'CLEAR_CACHE'
    | 'RESTART_DATABASE'
    | 'REROUTE_TRAFFIC'
    | 'ROLLBACK_DEPLOYMENT'
    | 'TRIGGER_CIRCUIT_BREAKER';

// ============ Self-Healing Types ============

export type SeverityLevel = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type AnomalyType =
    | 'CPU_SPIKE'
    | 'MEMORY_PRESSURE'
    | 'HIGH_ERROR_RATE'
    | 'LATENCY_DEGRADATION'
    | 'CASCADE_RISK'
    | 'SERVICE_DEGRADATION'
    | 'MULTIPLE_FAILURES';

export interface AnomalyAlert {
    alertId: string;
    timestamp: string;
    serviceId: string;
    serviceName: string;
    anomalyType: AnomalyType;
    severity: SeverityLevel;
    detectedValue: number;
    threshold: number;
    description: string;
    recommendedActions: string[];
    acknowledged: boolean;
    resolvedAt?: string;
}

export interface Decision {
    recommendedAction: ActionType;
    source: 'RULE_ENGINE' | 'RL_AGENT' | 'HYBRID';
    confidence: number;
    reasoning: string;
    overridden: boolean;
    originalAction?: ActionType;
}

export interface ActionExecutionRecord {
    executionId: string;
    startedAt: string;
    completedAt?: string;
    action: ActionType;
    targetServiceId: string;
    targetServiceName: string;
    status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'BLOCKED_BY_COOLDOWN' | 'BLOCKED_BY_PRECONDITION';
    failureReason?: string;
    costIncurred: number;
    beforeHealth: number;
    afterHealth: number;
    beforeCpu: number;
    afterCpu: number;
    beforeErrorRate: number;
    afterErrorRate: number;
    beforeLatency: number;
    afterLatency: number;
    healthDelta: number;
    effective: boolean;
    effectivenessReason: string;
}

export interface FeedbackEntry {
    episodeId: string;
    timestamp: string;
    tick: number;
    detectedAnomalies: AnomalyType[];
    beforeSystemHealth: number;
    beforeSloScore: number;
    beforeFailureCount: number;
    takenAction: ActionType;
    decisionSource: string;
    decisionConfidence: number;
    afterSystemHealth: number;
    afterSloScore: number;
    afterFailureCount: number;
    actionCost: number;
    healthDelta: number;
    sloDelta: number;
    failuresResolved: number;
    failuresIntroduced: number;
    effective: boolean;
    effectivenessReason: string;
    reward: number;
    effectivenessScore: number;
    shouldRepeatAction: boolean;
}

export interface HealingCycleReport {
    cycleNumber: number;
    timestamp: number;
    durationMs: number;
    anomalyCount: number;
    detectedAnomalies: AnomalyAlert[];
    decision: Decision;
    execution: ActionExecutionRecord;
    feedback: FeedbackEntry;
    outcome: string;
    successful: boolean;
}

export interface MemoryStatistics {
    totalMemories: number;
    uniqueAnomalies: number;
    uniqueActions: number;
    averageEffectiveness: number;
    successRate: number;
}

export interface LearningMetrics {
    totalEpisodes: number;
    overallEffectivenessRate: number;
    averageEffectivenessScore: number;
    averageReward: number;
}

export interface OrchestratorStatus {
    healingActive: boolean;
    totalCycles: number;
    successfulCycles: number;
    successRate: number;
    memoryStatistics: MemoryStatistics;
    learningMetrics: LearningMetrics;
    recentCycles: HealingCycleReport[];
}

// ============ Training Types ============

export interface TrainingConfig {
    difficulty: DifficultyLevel;
    totalTimesteps: number;
    learningRate: number;
    bufferSize: number;
    batchSize: number;
    gamma: number;
}

export interface TrainingMetrics {
    currentStep: number;
    currentEpisode: number;
    episodeReward: number;
    meanReward: number;
    sloScore: number;
    failureMode: string;
}

export interface ModelCheckpoint {
    name: string;
    path: string;
    createdAt: string;
    meanReward: number;
}

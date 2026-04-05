/**
 * API client for CloudSentinel AI backend.
 * Uses Axios with interceptors for error handling and type safety.
 */

import axios, { AxiosInstance, AxiosError } from 'axios';
import type {
    AnomalyAlert,
    DifficultyLevel,
    HealingCycleReport,
    LearningMetrics,
    MemoryStatistics,
    OrchestratorStatus,
    SimulationState,
    StepResponse,
    TopologyResponse,
} from '../types/api';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';

class ApiClient {
    private client: AxiosInstance;

    constructor() {
        this.client = axios.create({
            baseURL: API_BASE_URL,
            timeout: 10000,
            headers: {
                'Content-Type': 'application/json',
            },
        });

        this.setupInterceptors();
    }

    private setupInterceptors() {
        // Request interceptor
        this.client.interceptors.request.use(
            (config) => {
                console.debug(`[API] ${config.method?.toUpperCase()} ${config.url}`);
                return config;
            },
            (error) => Promise.reject(error)
        );

        // Response interceptor
        this.client.interceptors.response.use(
            (response) => response,
            (error: AxiosError) => {
                const message = error.response?.data
                    ? JSON.stringify(error.response.data)
                    : error.message;
                console.error(`[API ERROR] ${error.status}: ${message}`);
                return Promise.reject(error);
            }
        );
    }

    // ============ Simulation Endpoints ============

    async resetSimulation(difficulty: DifficultyLevel = 'SIMPLE') {
        const response = await this.client.post('/simulation/reset', null, {
            params: { difficulty },
        });
        return response.data;
    }

    async stepSimulation(action: number) {
        const response = await this.client.post<StepResponse>('/simulation/step', {
            action,
        });
        return response.data;
    }

    async getSimulationState() {
        const response = await this.client.get<SimulationState>('/simulation/state');
        return response.data;
    }

    async getTopology() {
        const response = await this.client.get<TopologyResponse>('/simulation/topology');
        return response.data;
    }

    async injectFailure(mode: string) {
        const response = await this.client.post('/simulation/inject-failure', null, {
            params: { mode },
        });
        return response.data;
    }

    async getMetadata() {
        const response = await this.client.get('/simulation/metadata');
        return response.data;
    }

    // ============ Self-Healing Endpoints ============

    async executeHealingCycle(rlAction: number = 0) {
        const response = await this.client.post<HealingCycleReport>('/healing/cycle', null, {
            params: { rlAction },
        });
        return response.data;
    }

    async getHealingStatus() {
        const response = await this.client.get<OrchestratorStatus>('/healing/status');
        return response.data;
    }

    async getAlerts() {
        const response = await this.client.get<AnomalyAlert[]>('/healing/alerts');
        return response.data;
    }

    async getMemoryStats() {
        const response = await this.client.get<MemoryStatistics>('/healing/memory/stats');
        return response.data;
    }

    async getFeedbackMetrics() {
        const response = await this.client.get<LearningMetrics>('/healing/feedback/metrics');
        return response.data;
    }

    async getRecentCycles(limit: number = 10) {
        const response = await this.client.get<HealingCycleReport[]>('/healing/recent', {
            params: { limit },
        });
        return response.data;
    }
}

// Singleton instance
export const api = new ApiClient();
export default api;

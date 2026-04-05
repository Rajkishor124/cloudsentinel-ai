/**
 * Zustand store for simulation state management.
 */

import { create } from 'zustand';
import { api } from '../lib/api';
import type {
    DifficultyLevel,
    ServiceNode,
    SimulationState,
    StepResponse,
    TopologyResponse,
} from '../types/api';

interface SimulationStore {
    // State
    state: SimulationState | null;
    topology: TopologyResponse | null;
    isLoading: boolean;
    error: string | null;
    difficulty: DifficultyLevel;

    // Actions
    resetSimulation: (difficulty?: DifficultyLevel) => Promise<void>;
    stepSimulation: (action: number) => Promise<StepResponse | null>;
    fetchState: () => Promise<void>;
    fetchTopology: () => Promise<void>;
    injectFailure: (mode: string) => Promise<void>;
    setDifficulty: (difficulty: DifficultyLevel) => void;

    // Selectors
    getSystemHealth: () => number;
    getActiveFailures: () => ServiceNode[];
    isHealthy: () => boolean;
}

export const useSimulationStore = create<SimulationStore>((set, get) => ({
    // Initial state
    state: null,
    topology: null,
    isLoading: false,
    error: null,
    difficulty: 'SIMPLE',

    // Actions
    resetSimulation: async (difficulty = 'SIMPLE') => {
        set({ isLoading: true, error: null, difficulty });
        try {
            const data = await api.resetSimulation(difficulty);
            set({
                state: {
                    observation: data.observation,
                    tick_ratio: 0,
                    active_failure: 'HEALTHY',
                    cascade_depth: 0,
                    action_mask: data.action_mask,
                },
                isLoading: false,
            });
            // Fetch topology after reset
            await get().fetchTopology();
        } catch (err) {
            set({ error: (err as Error).message, isLoading: false });
        }
    },

    stepSimulation: async (action: number) => {
        try {
            const response = await api.stepSimulation(action);
            // Update state with new observation
            set((state) => ({
                state: state.state
                    ? {
                        ...state.state,
                        observation: response.observation,
                        tick_ratio: response.tick / 200, // Assuming max 200 ticks
                        active_failure: response.failureMode,
                    }
                    : null,
            }));
            return response;
        } catch (err) {
            set({ error: (err as Error).message });
            return null;
        }
    },

    fetchState: async () => {
        try {
            const data = await api.getSimulationState();
            set({ state: data });
        } catch (err) {
            set({ error: (err as Error).message });
        }
    },

    fetchTopology: async () => {
        try {
            const data = await api.getTopology();
            set({ topology: data });
        } catch (err) {
            set({ error: (err as Error).message });
        }
    },

    injectFailure: async (mode: string) => {
        try {
            await api.injectFailure(mode);
            await get().fetchState();
        } catch (err) {
            set({ error: (err as Error).message });
        }
    },

    setDifficulty: (difficulty: DifficultyLevel) => set({ difficulty }),

    // Selectors
    getSystemHealth: () => {
        const { topology } = get();
        if (!topology || topology.nodes.length === 0) return 1.0;
        const avgHealth =
            topology.nodes.reduce((sum, n) => sum + n.healthScore, 0) /
            topology.nodes.length;
        return Math.round(avgHealth * 100) / 100;
    },

    getActiveFailures: () => {
        const { topology } = get();
        if (!topology) return [];
        return topology.nodes.filter((n) => n.failureMode !== 'HEALTHY');
    },

    isHealthy: () => {
        const { topology } = get();
        if (!topology) return true;
        return topology.nodes.every((n) => n.failureMode === 'HEALTHY');
    },
}));

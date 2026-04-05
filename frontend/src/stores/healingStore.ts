/**
 * Zustand store for self-healing system state management.
 */

import { create } from 'zustand';
import { api } from '../lib/api';
import type {
    AnomalyAlert,
    HealingCycleReport,
    LearningMetrics,
    MemoryStatistics,
    OrchestratorStatus,
} from '../types/api';

interface HealingStore {
    // State
    status: OrchestratorStatus | null;
    alerts: AnomalyAlert[];
    recentCycles: HealingCycleReport[];
    memoryStats: MemoryStatistics | null;
    feedbackMetrics: LearningMetrics | null;
    isLoading: boolean;
    error: string | null;
    lastUpdated: number | null;

    // Actions
    fetchStatus: () => Promise<void>;
    fetchAlerts: () => Promise<void>;
    executeCycle: (rlAction?: number) => Promise<HealingCycleReport | null>;
    fetchRecentCycles: (limit?: number) => Promise<void>;
    refreshAll: () => Promise<void>;

    // Selectors
    getAlertCount: () => number;
    getCriticalAlerts: () => AnomalyAlert[];
    getSuccessRate: () => number;
}

export const useHealingStore = create<HealingStore>((set, get) => ({
    // Initial state
    status: null,
    alerts: [],
    recentCycles: [],
    memoryStats: null,
    feedbackMetrics: null,
    isLoading: false,
    error: null,
    lastUpdated: null,

    // Actions
    fetchStatus: async () => {
        try {
            const data = await api.getHealingStatus();
            set({ status: data, lastUpdated: Date.now() });
        } catch (err) {
            set({ error: (err as Error).message });
        }
    },

    fetchAlerts: async () => {
        try {
            const data = await api.getAlerts();
            set({ alerts: data });
        } catch (err) {
            set({ error: (err as Error).message });
        }
    },

    executeCycle: async (rlAction = 0) => {
        set({ isLoading: true });
        try {
            const report = await api.executeHealingCycle(rlAction);
            set((state) => ({
                recentCycles: [...state.recentCycles.slice(-19), report],
                isLoading: false,
                lastUpdated: Date.now(),
            }));
            // Refresh status after cycle
            await get().fetchStatus();
            await get().fetchAlerts();
            return report;
        } catch (err) {
            set({ error: (err as Error).message, isLoading: false });
            return null;
        }
    },

    fetchRecentCycles: async (limit = 10) => {
        try {
            const data = await api.getRecentCycles(limit);
            set({ recentCycles: data });
        } catch (err) {
            set({ error: (err as Error).message });
        }
    },

    refreshAll: async () => {
        set({ isLoading: true });
        try {
            await Promise.all([
                get().fetchStatus(),
                get().fetchAlerts(),
                get().fetchRecentCycles(10),
            ]);
            set({ isLoading: false, lastUpdated: Date.now() });
        } catch (err) {
            set({ error: (err as Error).message, isLoading: false });
        }
    },

    // Selectors
    getAlertCount: () => {
        const { alerts } = get();
        return alerts.length;
    },

    getCriticalAlerts: () => {
        const { alerts } = get();
        return alerts.filter(
            (a) => a.severity === 'CRITICAL' || a.severity === 'HIGH'
        );
    },

    getSuccessRate: () => {
        const { status } = get();
        if (!status) return 0;
        return Math.round(status.successRate * 100);
    },
}));

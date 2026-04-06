import { useEffect, useState } from 'react';
import { useHealingStore }    from '../stores/healingStore';
import { useSimulationStore } from '../stores/simulationStore';
import Layout from '../components/layout/Layout';
import {
  MetricCard, SectionCard, StatusBadge, PrimaryButton,
  GhostButton, InjectButton, EmptyState, Spinner,
} from '../components/ui/primitives';
import type { ActionType } from '../types/api';

const ACTION_CODES: Record<ActionType, number> = {
  DO_NOTHING: 0, RESTART_SERVICE: 1, SCALE_UP: 2, SCALE_DOWN: 3,
  CLEAR_CACHE: 4, RESTART_DATABASE: 5, REROUTE_TRAFFIC: 6,
  ROLLBACK_DEPLOYMENT: 7, TRIGGER_CIRCUIT_BREAKER: 8,
};

const SEVERITY_MAP: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger',
  HIGH:     'danger',
  MEDIUM:   'warning',
  LOW:      'info',
  INFO:     'neutral',
};

export default function HealingPage() {
  const {
    status, alerts, recentCycles, memoryStats, feedbackMetrics,
    isLoading, refreshAll, fetchAlerts, fetchStatus,
    executeCycle, getAlertCount, getCriticalAlerts, getSuccessRate,
  } = useHealingStore();

  const { injectFailure } = useSimulationStore();
  const [isExecuting, setIsExecuting]         = useState(false);
  const [selectedAction, setSelectedAction]   = useState<ActionType | null>(null);

  useEffect(() => { refreshAll(); }, []);

  useEffect(() => {
    const id = setInterval(() => { fetchAlerts(); fetchStatus(); }, 3000);
    return () => clearInterval(id);
  }, []);

  const handleExecute = async () => {
    setIsExecuting(true);
    await executeCycle(selectedAction ? ACTION_CODES[selectedAction] : 0);
    setIsExecuting(false);
  };

  const handleInject = async (mode: string) => {
    await injectFailure(mode);
    await refreshAll();
  };

  const alertCount     = getAlertCount();
  const criticalAlerts = getCriticalAlerts();
  const successRate    = getSuccessRate();

  const fmt = (ms: number) => ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;

  const headerActions = (
    <>
      <GhostButton icon="refresh" onClick={refreshAll}>Refresh</GhostButton>
      <PrimaryButton onClick={handleExecute} loading={isExecuting} icon="play_arrow">
        Execute Healing Cycle
      </PrimaryButton>
    </>
  );

  return (
    <Layout title="Self-Healing AI" subtitle="Autonomous cloud incident response" actions={headerActions}>
      <div className="space-y-8 animate-fade-in">

        {/* ── Controls ────────────────────────────────────────────────── */}
        <SectionCard title="Controls" headerRight={
          <span className="text-xs text-on-surface-variant">Manual actions and failure injection</span>
        }>
          <div className="flex flex-col md:flex-row items-start md:items-center gap-6 justify-between">
            {/* Action selector */}
            <div className="flex flex-col gap-2">
              <label className="text-[10px] uppercase tracking-widest text-on-surface-variant font-bold">
                System Strategy
              </label>
              <div className="relative">
                <select
                  value={selectedAction ?? ''}
                  onChange={e => setSelectedAction(e.target.value as ActionType || null)}
                  className="appearance-none bg-surface-lowest border border-outline-variant/20 rounded-xl
                             px-4 py-3 pr-10 text-sm font-medium text-on-surface w-64
                             focus:ring-2 focus:ring-primary/50 focus:outline-none"
                >
                  <option value="">Auto-detect (Recommended)</option>
                  {Object.keys(ACTION_CODES).map(a => (
                    <option key={a} value={a}>{a.replace(/_/g, ' ')}</option>
                  ))}
                </select>
                <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2
                                  pointer-events-none text-outline">expand_more</span>
              </div>
            </div>

            {/* Inject buttons */}
            <div className="flex flex-wrap gap-3">
              <InjectButton label="Inject CPU Spike"     icon="bolt"      variant="amber"  onClick={() => handleInject('CPU_SPIKE')} />
              <InjectButton label="Inject Memory Leak"   icon="database"  variant="orange" onClick={() => handleInject('MEMORY_LEAK')} />
              <InjectButton label="Inject Service Crash" icon="dangerous" variant="red"    onClick={() => handleInject('SERVICE_CRASH')} />
            </div>
          </div>
        </SectionCard>

        {/* ── Stats Row ───────────────────────────────────────────────── */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <div className="bg-surface-container rounded-2xl p-5 border-l-4 border-error ghost-border">
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant font-bold mb-1">Active Alerts</p>
            <p className="text-4xl font-headline font-bold text-error">{alertCount}</p>
            <p className="mt-3 text-[10px] text-error/80 font-bold uppercase tracking-tight flex items-center gap-1">
              <span className="material-symbols-outlined text-xs">trending_up</span>
              {criticalAlerts.length} critical
            </p>
          </div>
          <div className="bg-surface-container rounded-2xl p-5 border-l-4 border-secondary-container ghost-border">
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant font-bold mb-1">Healing Cycles</p>
            <p className="text-4xl font-headline font-bold text-secondary-container">{status?.totalCycles ?? 0}</p>
            <p className="mt-3 text-xs text-on-surface-variant">
              <span className="text-primary font-bold">{successRate}%</span> success rate
            </p>
          </div>
          <div className="bg-surface-container rounded-2xl p-5 border-l-4 border-primary ghost-border">
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant font-bold mb-1">Memory Entries</p>
            <p className="text-4xl font-headline font-bold text-primary">{memoryStats?.totalMemories ?? 0}</p>
            <p className="mt-3 text-xs text-on-surface-variant">
              <span className="text-primary font-bold">
                {((memoryStats?.averageEffectiveness ?? 0) * 100).toFixed(0)}%
              </span> effective
            </p>
          </div>
          <div className="bg-surface-container rounded-2xl p-5 border-l-4 border-tertiary ghost-border">
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant font-bold mb-1">Learning Episodes</p>
            <p className="text-4xl font-headline font-bold text-tertiary">{feedbackMetrics?.totalEpisodes ?? 0}</p>
            <p className="mt-3 text-xs text-on-surface-variant">
              Avg reward: <span className="text-tertiary font-bold">
                {(feedbackMetrics?.averageReward ?? 0).toFixed(2)}
              </span>
            </p>
          </div>
        </div>

        {/* ── Alerts + Orchestrator ────────────────────────────────────── */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Alerts panel */}
          <div className="lg:col-span-8">
            <SectionCard
              title="Active Alerts"
              headerRight={
                <div className="flex gap-2">
                  <span className="px-2 py-1 bg-surface-high rounded text-[10px] font-bold
                                    text-on-surface-variant border border-outline-variant/10">
                    ALL SERVICES
                  </span>
                  <span className="px-2 py-1 bg-primary/10 rounded text-[10px] font-bold
                                    text-primary border border-primary/20">
                    REAL-TIME
                  </span>
                </div>
              }
            >
              {alerts.length === 0 ? (
                <EmptyState icon="verified_user" title="No active alerts" sub="System is healthy" />
              ) : (
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {alerts.map(alert => (
                    <div key={alert.alertId}
                         className="flex items-center justify-between p-4 bg-surface-low
                                    rounded-xl border border-outline-variant/5 hover:bg-surface-high
                                    transition-colors">
                      <div className="flex items-center gap-4">
                        <StatusBadge variant={SEVERITY_MAP[alert.severity] ?? 'neutral'}>
                          {alert.severity}
                        </StatusBadge>
                        <div>
                          <p className="text-sm font-bold">{alert.serviceName}</p>
                          <p className="text-xs text-on-surface-variant">
                            {alert.anomalyType.replace(/_/g, ' ')}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-4">
                        <span className="text-[11px] font-mono text-on-surface-variant/60">
                          {new Date(alert.timestamp).toLocaleTimeString()}
                        </span>
                        <button className="px-3 py-1.5 text-[11px] font-bold bg-surface-highest
                                            hover:bg-primary hover:text-on-primary-container
                                            rounded-lg transition-all">
                          Resolve
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </SectionCard>
          </div>

          {/* Orchestrator status */}
          <div className="lg:col-span-4 bg-surface-container rounded-2xl border border-outline-variant/10 p-6
                          flex flex-col items-center justify-center text-center">
            <p className="text-[10px] uppercase tracking-widest text-on-surface-variant font-bold mb-6 self-start">
              Orchestrator Status
            </p>

            <div className="relative mb-6">
              <div className="w-32 h-32 rounded-full border-4 border-surface-highest flex items-center justify-center">
                <div className="w-24 h-24 rounded-full bg-primary/10 flex items-center justify-center relative">
                  {isLoading ? (
                    <Spinner size="lg" />
                  ) : (
                    <span className="material-symbols-outlined icon-filled text-primary text-5xl">check_circle</span>
                  )}
                  <div className="absolute inset-0 border-2 border-primary border-t-transparent
                                   rounded-full animate-spin-slow opacity-20" />
                </div>
              </div>
            </div>

            <h3 className="text-2xl font-headline font-bold mb-1">
              {status?.healingActive ? 'Active' : 'Ready / Idle'}
            </h3>
            <p className="text-xs text-on-surface-variant mb-4">
              {status?.healingActive ? 'Healing in progress...' : 'Awaiting next telemetry cycle...'}
            </p>

            <div className="w-full h-px bg-outline-variant/15 mb-4" />
            <div className="flex items-center justify-center gap-2 text-[10px] font-mono text-on-surface-variant/70">
              <span className="material-symbols-outlined text-xs">schedule</span>
              Last check: {new Date().toLocaleTimeString()} UTC
            </div>

            {/* Quick stats */}
            <div className="w-full mt-4 grid grid-cols-2 gap-3 text-left">
              {[
                { label: 'Total Cycles',     value: status?.totalCycles ?? 0 },
                { label: 'Success Rate',     value: `${successRate}%` },
                { label: 'Memory Size',      value: memoryStats?.totalMemories ?? 0 },
                { label: 'Avg Effectiveness',value: `${((memoryStats?.averageEffectiveness ?? 0) * 100).toFixed(0)}%` },
              ].map(({ label, value }) => (
                <div key={label} className="bg-surface-low rounded-xl p-3 border border-outline-variant/10">
                  <p className="text-[10px] text-on-surface-variant uppercase tracking-wide">{label}</p>
                  <p className="text-sm font-bold font-mono text-on-surface mt-1">{value}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ── Healing History ──────────────────────────────────────────── */}
        <SectionCard
          title="Healing Cycle History"
          noPad
          headerRight={
            <button className="text-xs font-bold text-primary flex items-center gap-1 hover:underline">
              View Full Audit Log
              <span className="material-symbols-outlined text-xs">arrow_forward</span>
            </button>
          }
        >
          {recentCycles.length === 0 ? (
            <div className="p-6">
              <EmptyState icon="history" title="No healing cycles executed yet"
                          sub='Click "Execute Healing Cycle" to start' />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="bg-surface-low/50">
                    {['Cycle ID', 'Timestamp', 'Action Taken', 'Result', 'Duration', 'Reward'].map(h => (
                      <th key={h} className="px-6 py-3 text-[10px] uppercase tracking-widest
                                              font-bold text-on-surface-variant">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/5">
                  {[...recentCycles].reverse().slice(0, 10).map(cycle => (
                    <tr key={cycle.cycleNumber} className="hover:bg-surface-high/50 transition-colors">
                      <td className="px-6 py-4 font-mono text-[11px] text-primary">
                        #CYC-{String(cycle.cycleNumber).padStart(4, '0')}
                      </td>
                      <td className="px-6 py-4 font-mono text-[11px] text-on-surface-variant">
                        {new Date(cycle.timestamp).toLocaleString()}
                      </td>
                      <td className="px-6 py-4 text-sm font-medium">
                        {cycle.decision?.recommendedAction?.replace(/_/g, ' ') ?? 'Auto-detect'}
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge variant={cycle.successful ? 'success' : 'danger'}>
                          {cycle.successful ? 'Success' : 'Failed'}
                        </StatusBadge>
                      </td>
                      <td className="px-6 py-4 text-sm text-on-surface-variant">
                        {fmt(cycle.durationMs)}
                      </td>
                      <td className={`px-6 py-4 text-sm font-bold font-mono
                                       ${(cycle.feedback?.reward ?? 0) >= 0 ? 'text-primary' : 'text-error'}`}>
                        {(cycle.feedback?.reward ?? 0) >= 0 ? '+' : ''}
                        {(cycle.feedback?.reward ?? 0).toFixed(2)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>

      </div>
    </Layout>
  );
}

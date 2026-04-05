/**
 * Self-Healing page with alerts, controls, and execution history.
 */

import { useEffect, useState } from 'react';
import { useHealingStore } from '@stores/healingStore';
import { useSimulationStore } from '@stores/simulationStore';
import Card from '@components/ui/Card';
import StatBox from '@components/ui/StatBox';
import Badge from '@components/ui/Badge';
import Button from '@components/ui/Button';
import AlertItem from '@components/ui/AlertItem';
import LoadingSpinner from '@components/ui/LoadingSpinner';
import type { ActionType } from '@types/api';

const ACTION_CODES: Record<ActionType, number> = {
  DO_NOTHING: 0,
  RESTART_SERVICE: 1,
  SCALE_UP: 2,
  SCALE_DOWN: 3,
  CLEAR_CACHE: 4,
  RESTART_DATABASE: 5,
  REROUTE_TRAFFIC: 6,
  ROLLBACK_DEPLOYMENT: 7,
  TRIGGER_CIRCUIT_BREAKER: 8,
};

export default function HealingPage() {
  const {
    status,
    alerts,
    recentCycles,
    memoryStats,
    feedbackMetrics,
    isLoading,
    fetchStatus,
    fetchAlerts,
    executeCycle,
    fetchRecentCycles,
    refreshAll,
    getAlertCount,
    getCriticalAlerts,
    getSuccessRate,
  } = useHealingStore();

  const { injectFailure, fetchTopology } = useSimulationStore();
  const [isExecuting, setIsExecuting] = useState(false);
  const [selectedAction, setSelectedAction] = useState<ActionType | null>(null);

  // Initial load
  useEffect(() => {
    refreshAll();
  }, []);

  // Poll for alerts
  useEffect(() => {
    const interval = setInterval(() => {
      fetchAlerts();
      fetchStatus();
    }, 3000);

    return () => clearInterval(interval);
  }, []);

  const handleExecuteCycle = async () => {
    setIsExecuting(true);
    const action = selectedAction ? ACTION_CODES[selectedAction] : 0;
    await executeCycle(action);
    setIsExecuting(false);
  };

  const handleInjectFailure = async (mode: string) => {
    await injectFailure(mode);
    await refreshAll();
  };

  const alertCount = getAlertCount();
  const criticalAlerts = getCriticalAlerts();
  const successRate = getSuccessRate();

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-sentinel-text">Self-Healing AI</h1>
          <p className="text-sentinel-muted mt-1">Autonomous cloud incident response</p>
        </div>
        <div className="flex items-center gap-3">
          <Button onClick={refreshAll} variant="secondary" size="sm">
            Refresh
          </Button>
          <Button
            onClick={handleExecuteCycle}
            isLoading={isExecuting}
            variant="primary"
            size="md"
          >
            Execute Healing Cycle
          </Button>
        </div>
      </div>

      {/* Controls */}
      <Card title="Controls" subtitle="Manual actions and failure injection" className="mb-6">
        <div className="flex flex-wrap gap-3">
          <select
            value={selectedAction || ''}
            onChange={(e) => setSelectedAction(e.target.value as ActionType)}
            className="bg-sentinel-dark border border-sentinel-border text-sentinel-text rounded-md px-3 py-2 text-sm"
          >
            <option value="">Auto-detect action</option>
            {Object.keys(ACTION_CODES).map((action) => (
              <option key={action} value={action}>
                {action.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
          <div className="flex gap-2">
            <Button onClick={() => handleInjectFailure('CPU_SPIKE')} variant="secondary" size="sm">
              Inject CPU Spike
            </Button>
            <Button onClick={() => handleInjectFailure('MEMORY_LEAK')} variant="secondary" size="sm">
              Inject Memory Leak
            </Button>
            <Button onClick={() => handleInjectFailure('SERVICE_CRASH')} variant="secondary" size="sm">
              Inject Service Crash
            </Button>
          </div>
        </div>
      </Card>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatBox
          label="Active Alerts"
          value={alertCount}
          color={alertCount > 0 ? 'red' : 'green'}
        />
        <StatBox
          label="Healing Cycles"
          value={status?.totalCycles || 0}
          color="blue"
          trend={status ? 'up' : 'stable'}
          trendValue={`${successRate}% success`}
        />
        <StatBox
          label="Memory Entries"
          value={memoryStats?.totalMemories || 0}
          color="green"
          trend={memoryStats ? 'up' : 'stable'}
          trendValue={`${(memoryStats?.averageEffectiveness * 100).toFixed(0)}% effective`}
        />
        <StatBox
          label="Learning Episodes"
          value={feedbackMetrics?.totalEpisodes || 0}
          color="yellow"
          trend={feedbackMetrics ? 'up' : 'stable'}
          trendValue={`Avg reward: ${feedbackMetrics?.averageReward.toFixed(2)}`}
        />
      </div>

      {/* Alerts and Status */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        {/* Active Alerts */}
        <div className="lg:col-span-2">
          <Card title="Active Alerts" subtitle={`${criticalAlerts.length} critical`}>
            {alerts.length === 0 ? (
              <div className="text-center py-8 text-sentinel-muted">
                <p className="text-lg">No active alerts</p>
                <p className="text-sm mt-2">System is healthy</p>
              </div>
            ) : (
              <div className="max-h-96 overflow-y-auto">
                {alerts.map((alert) => (
                  <AlertItem key={alert.alertId} alert={alert} />
                ))}
              </div>
            )}
          </Card>
        </div>

        {/* Orchestrator Status */}
        <Card title="Orchestrator Status">
          {status ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sentinel-muted">Status</span>
                <Badge variant={status.healingActive ? 'warning' : 'success'}>
                  {status.healingActive ? 'Active' : 'Idle'}
                </Badge>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sentinel-muted">Total Cycles</span>
                <span className="text-sentinel-text font-medium">{status.totalCycles}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sentinel-muted">Success Rate</span>
                <span className="text-sentinel-text font-medium">{successRate}%</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sentinel-muted">Memory Size</span>
                <span className="text-sentinel-text">{memoryStats?.totalMemories || 0}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sentinel-muted">Unique Anomalies</span>
                <span className="text-sentinel-text">{memoryStats?.uniqueAnomalies || 0}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sentinel-muted">Avg Effectiveness</span>
                <span className="text-sentinel-text">
                  {((memoryStats?.averageEffectiveness || 0) * 100).toFixed(1)}%
                </span>
              </div>
            </div>
          ) : (
            <LoadingSpinner size="md" />
          )}
        </Card>
      </div>

      {/* Recent Healing Cycles */}
      <Card title="Healing Cycle History" subtitle="Last 10 executions">
        {recentCycles.length === 0 ? (
          <div className="text-center py-8 text-sentinel-muted">
            <p>No healing cycles executed yet</p>
            <p className="text-sm mt-2">Click "Execute Healing Cycle" to start</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-sentinel-border">
                  <th className="text-left py-2 px-3 text-sentinel-muted">Cycle</th>
                  <th className="text-left py-2 px-3 text-sentinel-muted">Action</th>
                  <th className="text-left py-2 px-3 text-sentinel-muted">Alerts</th>
                  <th className="text-left py-2 px-3 text-sentinel-muted">Result</th>
                  <th className="text-left py-2 px-3 text-sentinel-muted">Reward</th>
                  <th className="text-left py-2 px-3 text-sentinel-muted">Duration</th>
                </tr>
              </thead>
              <tbody>
                {recentCycles.slice(-10).reverse().map((cycle) => (
                  <tr key={cycle.cycleNumber} className="border-b border-sentinel-border/50">
                    <td className="py-2 px-3 text-sentinel-text">#{cycle.cycleNumber}</td>
                    <td className="py-2 px-3 text-sentinel-text">
                      <Badge variant="info" size="sm">
                        {cycle.decision?.recommendedAction?.replace(/_/g, ' ') || 'N/A'}
                      </Badge>
                    </td>
                    <td className="py-2 px-3 text-sentinel-text">{cycle.anomalyCount}</td>
                    <td className="py-2 px-3">
                      <Badge variant={cycle.successful ? 'success' : 'danger'} size="sm">
                        {cycle.successful ? 'Effective' : 'Ineffective'}
                      </Badge>
                    </td>
                    <td className="py-2 px-3 text-sentinel-text">
                      {cycle.feedback?.reward?.toFixed(3) || '0.000'}
                    </td>
                    <td className="py-2 px-3 text-sentinel-muted">
                      {formatDuration(cycle.durationMs)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

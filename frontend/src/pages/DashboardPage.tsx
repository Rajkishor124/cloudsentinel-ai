/**
 * Main Dashboard page with real-time metrics and charts.
 */

import { useEffect, useState } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar, Legend } from 'recharts';
import { useSimulationStore } from '@stores/simulationStore';
import { useHealingStore } from '@stores/healingStore';
import Card from '@components/ui/Card';
import StatBox from '@components/ui/StatBox';
import Badge from '@components/ui/Badge';
import Button from '@components/ui/Button';
import LoadingSpinner from '@components/ui/LoadingSpinner';
import type { DifficultyLevel } from '@types/api';

export default function DashboardPage() {
  const {
    topology,
    isLoading: isLoadingSim,
    difficulty,
    resetSimulation,
    fetchTopology,
    getSystemHealth,
    getActiveFailures,
    isHealthy,
  } = useSimulationStore();

  const {
    status,
    alerts,
    recentCycles,
    fetchStatus,
    fetchAlerts,
    fetchRecentCycles,
  } = useHealingStore();

  const [healthHistory, setHealthHistory] = useState<Array<{ time: string; health: number }>>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Initial load
  useEffect(() => {
    resetSimulation(difficulty);
  }, []);

  // Poll for updates
  useEffect(() => {
    const interval = setInterval(() => {
      refreshData();
    }, 5000);

    return () => clearInterval(interval);
  }, []);

  // Track health history
  useEffect(() => {
    if (topology) {
      const health = getSystemHealth();
      setHealthHistory((prev) => {
        const newHistory = [
          ...prev,
          { time: new Date().toLocaleTimeString(), health },
        ];
        return newHistory.slice(-20); // Keep last 20 points
      });
    }
  }, [topology]);

  const refreshData = async () => {
    setIsRefreshing(true);
    await Promise.all([fetchTopology(), fetchStatus(), fetchAlerts()]);
    setIsRefreshing(false);
  };

  const handleDifficultyChange = async (newDifficulty: DifficultyLevel) => {
    await resetSimulation(newDifficulty);
  };

  const systemHealth = getSystemHealth();
  const activeFailures = getActiveFailures();
  const healthy = isHealthy();
  const criticalAlerts = alerts.filter((a) => a.severity === 'CRITICAL' || a.severity === 'HIGH');

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-sentinel-text">Dashboard</h1>
          <p className="text-sentinel-muted mt-1">Real-time system monitoring</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={difficulty}
            onChange={(e) => handleDifficultyChange(e.target.value as DifficultyLevel)}
            className="bg-sentinel-card border border-sentinel-border text-sentinel-text rounded-md px-3 py-2 text-sm"
          >
            <option value="SIMPLE">Simple</option>
            <option value="MEDIUM">Medium</option>
            <option value="COMPLEX">Complex</option>
            <option value="ADVERSARIAL">Adversarial</option>
          </select>
          <Button onClick={refreshData} isLoading={isRefreshing} variant="secondary" size="sm">
            Refresh
          </Button>
        </div>
      </div>

      {isLoadingSim ? (
        <div className="flex items-center justify-center h-64">
          <LoadingSpinner size="lg" />
        </div>
      ) : (
        <>
          {/* Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <StatBox
              label="System Health"
              value={(systemHealth * 100).toFixed(1) + '%'}
              color={systemHealth > 0.8 ? 'green' : systemHealth > 0.5 ? 'yellow' : 'red'}
              trend={systemHealth > 0.8 ? 'up' : systemHealth < 0.5 ? 'down' : 'stable'}
              trendValue={`${(systemHealth * 100).toFixed(0)}%`}
            />
            <StatBox
              label="Active Services"
              value={topology?.nodes.length || 0}
              color="blue"
            />
            <StatBox
              label="Active Failures"
              value={activeFailures.length}
              color={activeFailures.length > 0 ? 'red' : 'green'}
            />
            <StatBox
              label="Healing Cycles"
              value={status?.totalCycles || 0}
              color="green"
              trend={status ? 'up' : 'stable'}
              trendValue={`${(status?.successRate * 100).toFixed(0)}% success`}
            />
          </div>

          {/* Charts Row */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
            {/* Health Trend */}
            <Card title="System Health Trend" subtitle="Last 20 measurements">
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={healthHistory}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                    <XAxis dataKey="time" stroke="#9ca3af" fontSize={12} />
                    <YAxis stroke="#9ca3af" fontSize={12} domain={[0, 1]} tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#111827',
                        border: '1px solid #1f2937',
                        borderRadius: '8px',
                      }}
                      formatter={(value: number) => [(value * 100).toFixed(1) + '%', 'Health']}
                    />
                    <Line
                      type="monotone"
                      dataKey="health"
                      stroke={systemHealth > 0.8 ? '#10b981' : systemHealth > 0.5 ? '#f59e0b' : '#ef4444'}
                      strokeWidth={2}
                      dot={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </Card>

            {/* Service Metrics */}
            <Card title="Service Metrics" subtitle="Current resource utilization">
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={topology?.nodes.map((n) => ({
                      name: n.name.split('-')[0],
                      CPU: n.cpu,
                      Memory: n.memory,
                      'Error Rate': n.errorRate,
                      Latency: n.latency,
                    }))}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                    <XAxis dataKey="name" stroke="#9ca3af" fontSize={12} />
                    <YAxis stroke="#9ca3af" fontSize={12} domain={[0, 1]} tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#111827',
                        border: '1px solid #1f2937',
                        borderRadius: '8px',
                      }}
                      formatter={(value: number) => (value * 100).toFixed(1) + '%'}
                    />
                    <Legend />
                    <Bar dataKey="CPU" fill="#3b82f6" />
                    <Bar dataKey="Memory" fill="#10b981" />
                    <Bar dataKey="Error Rate" fill="#ef4444" />
                    <Bar dataKey="Latency" fill="#f59e0b" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Card>
          </div>

          {/* Status and Alerts Row */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* System Status */}
            <Card title="System Status">
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sentinel-muted">Status</span>
                  <Badge variant={healthy ? 'success' : 'danger'}>
                    {healthy ? 'Healthy' : 'Degraded'}
                  </Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sentinel-muted">Difficulty</span>
                  <span className="text-sentinel-text font-medium">{difficulty}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sentinel-muted">Services</span>
                  <span className="text-sentinel-text">{topology?.nodes.length || 0}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sentinel-muted">SLO Score</span>
                  <span className="text-sentinel-text">
                    {((topology?.nodes.filter((n) => n.failureMode === 'HEALTHY').length || 0) /
                      Math.max(topology?.nodes.length || 1, 1) * 100).toFixed(1)}%
                  </span>
                </div>
              </div>
            </Card>

            {/* Critical Alerts */}
            <Card title="Critical Alerts" subtitle={`${criticalAlerts.length} active`}>
              {criticalAlerts.length === 0 ? (
                <p className="text-sentinel-muted text-sm">No critical alerts</p>
              ) : (
                <div className="space-y-2 max-h-48 overflow-y-auto">
                  {criticalAlerts.slice(0, 5).map((alert) => (
                    <div key={alert.alertId} className="p-2 bg-sentinel-dark rounded border border-sentinel-border">
                      <div className="flex items-center gap-2">
                        <Badge variant={alert.severity === 'CRITICAL' ? 'danger' : 'warning'} size="sm">
                          {alert.severity}
                        </Badge>
                        <span className="text-sm text-sentinel-text truncate">{alert.serviceName}</span>
                      </div>
                      <p className="text-xs text-sentinel-muted mt-1">{alert.anomalyType.replace(/_/g, ' ')}</p>
                    </div>
                  ))}
                </div>
              )}
            </Card>

            {/* Recent Healing Cycles */}
            <Card title="Recent Healing" subtitle="Last 5 cycles">
              {recentCycles.length === 0 ? (
                <p className="text-sentinel-muted text-sm">No healing cycles yet</p>
              ) : (
                <div className="space-y-2 max-h-48 overflow-y-auto">
                  {recentCycles.slice(-5).reverse().map((cycle) => (
                    <div key={cycle.cycleNumber} className="p-2 bg-sentinel-dark rounded border border-sentinel-border">
                      <div className="flex items-center justify-between">
                        <span className="text-sm text-sentinel-text">Cycle #{cycle.cycleNumber}</span>
                        <Badge variant={cycle.successful ? 'success' : 'danger'} size="sm">
                          {cycle.successful ? 'OK' : 'FAIL'}
                        </Badge>
                      </div>
                      <p className="text-xs text-sentinel-muted mt-1">{cycle.outcome}</p>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  );
}

import { useEffect, useState } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, BarChart, Bar, Legend,
} from 'recharts';
import { useSimulationStore } from '../stores/simulationStore';
import { useHealingStore }    from '../stores/healingStore';
import Layout from '../components/layout/Layout';
import {
  MetricCard, SectionCard, StatusBadge, PrimaryButton, GhostButton, EmptyState, Spinner,
} from '../components/ui/primitives';
import type { DifficultyLevel, AnomalyAlert } from '../types/api';

// ─── Custom tooltip shared style ─────────────────────────────────────────────
const TOOLTIP_STYLE = {
  backgroundColor: '#1c2026',
  border: '1px solid rgba(66,71,84,0.3)',
  borderRadius: '12px',
  fontSize: '12px',
  color: '#dfe2eb',
};

export default function DashboardPage() {
  const {
    topology, isLoading, difficulty,
    resetSimulation, fetchTopology,
    getSystemHealth, getActiveFailures, isHealthy,
  } = useSimulationStore();

  const { status, alerts, recentCycles, fetchStatus, fetchAlerts } = useHealingStore();

  const [healthHistory, setHealthHistory] = useState<{ time: string; health: number }[]>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => { resetSimulation(difficulty); }, []);

  useEffect(() => {
    const id = setInterval(refreshData, 5000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (topology) {
      setHealthHistory(prev =>
        [...prev, { time: new Date().toLocaleTimeString(), health: getSystemHealth() }].slice(-20)
      );
    }
  }, [topology]);

  const refreshData = async () => {
    setIsRefreshing(true);
    await Promise.all([fetchTopology(), fetchStatus(), fetchAlerts()]);
    setIsRefreshing(false);
  };

  const systemHealth  = getSystemHealth();
  const activeFailures = getActiveFailures();
  const criticalAlerts = alerts.filter(a => a.severity === 'CRITICAL' || a.severity === 'HIGH');
  const sloScore = topology
    ? (topology.nodes.filter(n => n.failureMode === 'HEALTHY').length / Math.max(topology.nodes.length, 1)) * 100
    : 0;

  const serviceMetrics = topology?.nodes.map(n => ({
    name: n.name.split('-')[0],
    CPU:       +(n.cpu * 100).toFixed(1),
    Memory:    +(n.memory * 100).toFixed(1),
    'Err Rate': +(n.errorRate * 100).toFixed(2),
    Latency:   +(n.latency * 100).toFixed(1),
  })) ?? [];

  const headerActions = (
    <>
      <select
        value={difficulty}
        onChange={e => resetSimulation(e.target.value as DifficultyLevel)}
        className="bg-surface-high border border-outline-variant/20 text-on-surface rounded-xl
                   px-4 py-2 text-xs font-medium font-headline hover:border-primary/50 transition-all"
      >
        {['SIMPLE', 'MEDIUM', 'COMPLEX', 'ADVERSARIAL'].map(d => (
          <option key={d} value={d}>{d.charAt(0) + d.slice(1).toLowerCase()}</option>
        ))}
      </select>
      <PrimaryButton onClick={refreshData} loading={isRefreshing} icon="refresh">
        Refresh
      </PrimaryButton>
    </>
  );

  return (
    <Layout title="Dashboard" subtitle="Real-time system monitoring" actions={headerActions}>
      {isLoading ? (
        <div className="flex items-center justify-center h-96">
          <Spinner size="lg" />
        </div>
      ) : (
        <div className="space-y-8 animate-fade-in">

          {/* ── Stats Row ───────────────────────────────────────────────── */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            <MetricCard
              label="System Health"
              value={`${(systemHealth * 100).toFixed(2)}%`}
              accent="primary"
              pulse
              sub="Above SLA (+0.02%)"
              subIcon="trending_up"
            />
            <MetricCard
              label="Active Services"
              value={topology?.nodes.length ?? 0}
              accent="secondary"
              icon="dns"
              sub={`${activeFailures.length} failures active`}
            />
            <MetricCard
              label="Active Failures"
              value={String(activeFailures.length).padStart(2, '0')}
              accent={activeFailures.length > 0 ? 'error' : 'primary'}
              icon="report_problem"
              sub={activeFailures.length > 0 ? 'High priority alerts' : 'All systems nominal'}
              subIcon={activeFailures.length > 0 ? 'warning' : 'check_circle'}
            />
            <MetricCard
              label="Healing Cycles"
              value={status?.totalCycles ?? 0}
              accent="tertiary"
              icon="bolt"
              sub={`${((status?.successRate ?? 0) * 100).toFixed(1)}% success rate`}
              subIcon="check_circle"
            />
          </div>

          {/* ── Charts Row ──────────────────────────────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Health Trend */}
            <SectionCard
              title="System Health Trend"
              headerRight={
                <div className="flex items-center gap-2 text-[10px] font-bold text-on-surface-variant">
                  <span className="w-2 h-2 rounded-full bg-primary" />
                  Live Telemetry
                </div>
              }
            >
              <div className="h-60 mt-4">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={healthHistory}>
                    <defs>
                      <linearGradient id="healthGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%"   stopColor="#3adfab" stopOpacity={0.3} />
                        <stop offset="100%" stopColor="#3adfab" stopOpacity={0}   />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="4 4" stroke="rgba(66,71,84,0.2)" />
                    <XAxis dataKey="time" stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }} />
                    <YAxis
                      stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }}
                      domain={[0, 1]} tickFormatter={v => `${(v * 100).toFixed(0)}%`}
                    />
                    <Tooltip
                      contentStyle={TOOLTIP_STYLE}
                      formatter={(v: number) => [`${(v * 100).toFixed(1)}%`, 'Health']}
                    />
                    <Line
                      type="monotone" dataKey="health"
                      stroke={systemHealth > 0.8 ? '#3adfab' : systemHealth > 0.5 ? '#ffb95f' : '#ffb4ab'}
                      strokeWidth={2.5} dot={false}
                      style={{ filter: 'drop-shadow(0 0 4px #3adfab)' }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </SectionCard>

            {/* Service Metrics */}
            <SectionCard
              title="Service Metrics"
              headerRight={
                <div className="flex items-center gap-3">
                  {[
                    { label: 'CPU', color: 'bg-secondary' },
                    { label: 'MEM', color: 'bg-primary' },
                    { label: 'ERR', color: 'bg-error' },
                    { label: 'LAT', color: 'bg-tertiary' },
                  ].map(({ label, color }) => (
                    <div key={label} className="flex items-center gap-1 text-[10px] font-bold text-on-surface-variant">
                      <span className={`w-2 h-2 rounded ${color}`} />
                      {label}
                    </div>
                  ))}
                </div>
              }
            >
              {serviceMetrics.length > 0 ? (
                <div className="h-60 mt-4">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={serviceMetrics} barCategoryGap="30%">
                      <CartesianGrid strokeDasharray="4 4" stroke="rgba(66,71,84,0.2)" />
                      <XAxis dataKey="name" stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }} />
                      <YAxis stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }} unit="%" />
                      <Tooltip contentStyle={TOOLTIP_STYLE} formatter={(v: number) => [`${v.toFixed(1)}%`]} />
                      <Bar dataKey="CPU"      fill="#adc6ff" radius={[3, 3, 0, 0]} />
                      <Bar dataKey="Memory"   fill="#3adfab" radius={[3, 3, 0, 0]} />
                      <Bar dataKey="Err Rate" fill="#ffb4ab" radius={[3, 3, 0, 0]} />
                      <Bar dataKey="Latency"  fill="#ffb95f" radius={[3, 3, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="h-60 flex items-center justify-center">
                  <EmptyState icon="bar_chart" title="No service data" sub="Reset the simulation to load services" />
                </div>
              )}
            </SectionCard>
          </div>

          {/* ── Bottom Row ──────────────────────────────────────────────── */}
          <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
            {/* System Status table */}
            <SectionCard
              title="System Status"
              noPad
              headerRight={
                <span className="material-symbols-outlined text-on-surface-variant cursor-pointer
                                  hover:text-primary transition-colors">more_vert</span>
              }
            >
              <table className="w-full text-left">
                <thead>
                  <tr className="bg-surface-low">
                    {['Service', 'SLO', 'Status'].map(h => (
                      <th key={h} className="px-6 py-3 text-[10px] font-black uppercase
                                             tracking-wider text-on-surface-variant
                                             last:text-right">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/5">
                  {(topology?.nodes ?? []).slice(0, 5).map(node => {
                    const ok = node.failureMode === 'HEALTHY';
                    return (
                      <tr key={node.id} className="hover:bg-surface-high transition-colors">
                        <td className="px-6 py-4">
                          <p className="text-xs font-bold">{node.name}</p>
                          <p className="text-[10px] text-on-surface-variant capitalize">
                            {difficulty.toLowerCase()} difficulty
                          </p>
                        </td>
                        <td className="px-6 py-4 text-center">
                          <span className={`text-xs font-headline ${ok ? 'text-primary' : 'text-tertiary'}`}>
                            {(node.healthScore * 100).toFixed(1)}%
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <StatusBadge variant={ok ? 'success' : 'warning'}>
                            {ok ? 'Healthy' : 'Warn'}
                          </StatusBadge>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </SectionCard>

            {/* Critical Alerts */}
            <div className="bg-surface-container rounded-2xl border border-outline-variant/10 p-6
                            flex flex-col items-center justify-center text-center">
              {criticalAlerts.length === 0 ? (
                <>
                  <div className="w-16 h-16 bg-surface-high rounded-full flex items-center justify-center
                                  mb-5 border border-outline-variant/10">
                    <span className="material-symbols-outlined text-3xl text-on-surface-variant">verified_user</span>
                  </div>
                  <h3 className="font-headline font-bold text-lg mb-2">No Critical Alerts</h3>
                  <p className="text-sm text-on-surface-variant max-w-[200px]">
                    All systems operating within defined SLO parameters.
                  </p>
                  <GhostButton className="mt-6">View Alert History</GhostButton>
                </>
              ) : (
                <div className="w-full space-y-3">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-headline font-bold">Critical Alerts</h3>
                    <StatusBadge variant="danger">{criticalAlerts.length} active</StatusBadge>
                  </div>
                  {criticalAlerts.slice(0, 4).map(a => (
                    <div key={a.alertId}
                         className="flex items-start gap-3 p-3 bg-surface-low rounded-xl border border-outline-variant/5 text-left">
                      <StatusBadge variant="danger" size="sm">{a.severity}</StatusBadge>
                      <div className="overflow-hidden">
                        <p className="text-xs font-bold truncate">{a.serviceName}</p>
                        <p className="text-[10px] text-on-surface-variant">
                          {a.anomalyType.replace(/_/g, ' ')}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Recent Healing */}
            <SectionCard
              title="Recent Healing"
              headerRight={
                <span className="text-[10px] font-bold text-primary uppercase">Active Cycle</span>
              }
              noPad
            >
              <div className="p-4 space-y-3">
                {recentCycles.length === 0 ? (
                  <EmptyState icon="history" title="No healing cycles yet" />
                ) : (
                  [...recentCycles].reverse().slice(0, 4).map(cycle => (
                    <div key={cycle.cycleNumber}
                         className="flex items-start gap-3 p-3 bg-surface-low rounded-xl
                                    border border-outline-variant/5 hover:bg-surface-high transition-colors">
                      <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0
                                       ${cycle.successful ? 'bg-primary/10' : 'bg-error/10'}`}>
                        <span className={`material-symbols-outlined text-lg
                                          ${cycle.successful ? 'text-primary' : 'text-error'}`}>
                          {cycle.successful ? 'autorenew' : 'error_outline'}
                        </span>
                      </div>
                      <div className="flex-1 overflow-hidden">
                        <div className="flex justify-between">
                          <p className="text-xs font-bold truncate">Cycle #{cycle.cycleNumber}</p>
                          <StatusBadge variant={cycle.successful ? 'success' : 'danger'} size="sm">
                            {cycle.successful ? 'OK' : 'FAIL'}
                          </StatusBadge>
                        </div>
                        <p className="text-[10px] text-on-surface-variant truncate mt-0.5">
                          {cycle.outcome}
                        </p>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </SectionCard>
          </div>

        </div>
      )}
    </Layout>
  );
}

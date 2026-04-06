import { useEffect, useRef, useState } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useSimulationStore } from '../stores/simulationStore';
import Layout from '../components/layout/Layout';
import { SectionCard, GhostButton, EmptyState } from '../components/ui/primitives';
import type { DifficultyLevel } from '../types/api';

interface TrainingMetric {
  step: number;
  episodeReward: number;
  sloScore: number;
}

interface LogEntry {
  ts: string;
  text: string;
  variant: 'default' | 'reward' | 'warning' | 'system';
}

const LOG_COLORS: Record<LogEntry['variant'], string> = {
  default: 'text-primary/80',
  reward:  'text-tertiary',
  warning: 'text-error',
  system:  'text-on-surface',
};

const TOOLTIP_STYLE = {
  backgroundColor: '#1c2026',
  border: '1px solid rgba(66,71,84,0.3)',
  borderRadius: '12px',
  fontSize: '12px',
  color: '#dfe2eb',
};

export default function TrainingPage() {
  const { resetSimulation, stepSimulation, getSystemHealth } = useSimulationStore();

  const [isTraining, setIsTraining]           = useState(false);
  const [difficulty, setDifficulty]           = useState<DifficultyLevel>('SIMPLE');
  const [currentStep, setCurrentStep]         = useState(0);
  const [currentReward, setCurrentReward]     = useState(0);
  const [episodeReward, setEpisodeReward]     = useState(0);
  const [totalEpisodes, setTotalEpisodes]     = useState(0);
  const [metrics, setMetrics]                 = useState<TrainingMetric[]>([]);
  const [logs, setLogs]                       = useState<LogEntry[]>([
    { ts: new Date().toLocaleTimeString(), text: 'System initialized. Sentinel Core v4.2.0-stable', variant: 'system' },
    { ts: new Date().toLocaleTimeString(), text: 'Awaiting agent start command...', variant: 'reward' },
  ]);

  const logRef     = useRef<HTMLDivElement>(null);
  const abortRef   = useRef(false);

  const pushLog = (text: string, variant: LogEntry['variant'] = 'default') => {
    setLogs(prev => [...prev.slice(-200), { ts: new Date().toLocaleTimeString(), text, variant }]);
  };

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [logs]);

  const handleStart = async () => {
    abortRef.current = false;
    setIsTraining(true);
    await resetSimulation(difficulty);
    setCurrentStep(0);
    setEpisodeReward(0);
    setMetrics([]);
    pushLog(`Training started | Difficulty: ${difficulty}`, 'system');

    let cumReward = 0;
    for (let i = 0; i < 100 && !abortRef.current; i++) {
      const action   = Math.floor(Math.random() * 9);
      const response = await stepSimulation(action);
      if (!response) break;

      cumReward += response.reward;
      setCurrentStep(i + 1);
      setCurrentReward(response.reward);
      setEpisodeReward(cumReward);

      setMetrics(prev => [...prev, {
        step: i + 1,
        episodeReward: +cumReward.toFixed(3),
        sloScore:      +response.sloScore.toFixed(3),
      }]);

      pushLog(
        `Episode ${i + 1} | Reward: ${response.reward.toFixed(4)} | ` +
        `Steps: ${i + 1} | SLO: ${(response.sloScore * 100).toFixed(1)}%`,
        response.reward >= 0 ? 'default' : 'warning'
      );

      await new Promise(r => setTimeout(r, 150));
    }

    setTotalEpisodes(prev => prev + 1);
    pushLog(`Training episode complete | Total reward: ${cumReward.toFixed(3)}`, 'reward');
    setIsTraining(false);
  };

  const handleStop = () => {
    abortRef.current = true;
    pushLog('Training stopped by user.', 'warning');
  };

  const handleReset = async () => {
    abortRef.current = true;
    await resetSimulation(difficulty);
    setCurrentStep(0);
    setEpisodeReward(0);
    setCurrentReward(0);
    setMetrics([]);
    setLogs([{ ts: new Date().toLocaleTimeString(), text: 'Environment reset.', variant: 'system' }]);
  };

  const healthPct  = (getSystemHealth() * 100).toFixed(1);
  const statusText = isTraining ? 'Training' : 'Idle';

  const headerActions = (
    <>
      <select
        value={difficulty}
        onChange={e => setDifficulty(e.target.value as DifficultyLevel)}
        disabled={isTraining}
        className="bg-surface-high border border-outline-variant/20 text-on-surface rounded-xl
                   px-4 py-2 text-xs font-medium font-headline disabled:opacity-50
                   hover:border-primary/50 transition-all"
      >
        {['SIMPLE', 'MEDIUM', 'COMPLEX', 'ADVERSARIAL'].map(d => (
          <option key={d} value={d}>{d.charAt(0) + d.slice(1).toLowerCase()}</option>
        ))}
      </select>
      <GhostButton onClick={handleReset} disabled={isTraining}>Reset</GhostButton>

      {isTraining ? (
        <button
          onClick={handleStop}
          className="flex items-center gap-2 px-6 py-2.5 bg-error-container text-on-error-container
                     rounded-xl font-bold text-xs active:scale-95 transition-transform"
        >
          <span className="material-symbols-outlined text-sm icon-filled">stop</span>
          Stop Training
        </button>
      ) : (
        <button
          onClick={handleStart}
          className="flex items-center gap-2 px-6 py-2.5 bg-[#8B5CF6] hover:bg-[#7c4dff] text-white
                     rounded-xl font-bold text-xs shadow-[0_4px_15px_rgba(139,92,246,0.25)]
                     active:scale-95 transition-all"
        >
          <span className="material-symbols-outlined text-sm icon-filled">play_arrow</span>
          Start Training
        </button>
      )}
    </>
  );

  return (
    <Layout title="Training Console" subtitle="RL agent training and evaluation" actions={headerActions}>
      <div className="space-y-8 animate-fade-in">

        {/* ── Metric Cards ────────────────────────────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[
            { label: 'Current Step',    value: currentStep.toLocaleString(), color: 'text-secondary',  icon: 'analytics',  sub: 'STEP_COUNTER_LIVE' },
            { label: 'Episode Reward',  value: episodeReward.toFixed(3),     color: 'text-tertiary',   icon: 'rewarded_ads', sub: 'RWD_COEF_VAL' },
            { label: 'System Health',   value: `${healthPct}%`,              color: 'text-primary',    icon: 'bolt',         sub: 'SYS_STABILITY_INDEX', pulse: true },
          ].map(({ label, value, color, icon, sub, pulse }) => (
            <div key={label} className="bg-surface-low rounded-2xl p-6 border border-outline-variant/10">
              <div className="flex justify-between items-start mb-4">
                <span className="text-on-surface-variant text-xs font-bold uppercase tracking-widest">{label}</span>
                <span className={`material-symbols-outlined text-lg opacity-50 ${color}`}>{icon}</span>
              </div>
              <div className="flex items-baseline gap-2">
                <span className={`text-4xl font-mono font-bold tracking-tighter ${color}`}>{value}</span>
                {pulse && isTraining && (
                  <div className="w-2 h-2 bg-primary rounded-full animate-pulse shadow-glow-primary" />
                )}
              </div>
              <p className="mt-2 text-[10px] text-on-surface-variant/40 font-mono">{sub}</p>
            </div>
          ))}
        </div>

        {/* ── Charts ──────────────────────────────────────────────────── */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Episode Reward */}
          <SectionCard
            title="Episode Reward"
            headerRight={
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-[#8B5CF6]" />
                <span className="text-xs text-on-surface-variant">Live Training</span>
              </div>
            }
          >
            <div className="h-60 mt-4">
              {metrics.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={metrics}>
                    <defs>
                      <linearGradient id="rewardGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%"   stopColor="#8B5CF6" stopOpacity={0.3} />
                        <stop offset="100%" stopColor="#8B5CF6" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="4 4" stroke="rgba(66,71,84,0.2)" />
                    <XAxis dataKey="step" stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }} />
                    <YAxis stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }} />
                    <Tooltip contentStyle={TOOLTIP_STYLE} />
                    <Line type="monotone" dataKey="episodeReward" name="Reward"
                          stroke="#8B5CF6" strokeWidth={2.5} dot={false}
                          style={{ filter: 'drop-shadow(0 0 4px #8B5CF6)' }} />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex flex-col items-center justify-center
                                border-2 border-dashed border-outline-variant/20 rounded-xl
                                bg-surface-lowest/50">
                  <span className="material-symbols-outlined text-4xl text-on-surface-variant/30 mb-3">monitoring</span>
                  <p className="text-on-surface-variant/60 font-medium text-sm">Start training to see metrics</p>
                  <div className="mt-4 flex gap-1 opacity-20">
                    {[8, 12, 6, 10, 14, 9].map((h, i) => (
                      <div key={i} className="w-1 bg-[#8B5CF6] rounded-full" style={{ height: h * 4 }} />
                    ))}
                  </div>
                </div>
              )}
            </div>
          </SectionCard>

          {/* SLO Score */}
          <SectionCard
            title="SLO Score"
            headerRight={
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-primary" />
                <span className="text-xs font-mono text-on-surface-variant">0–100% SCALE</span>
              </div>
            }
          >
            <div className="h-60 mt-4">
              {metrics.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={metrics}>
                    <CartesianGrid strokeDasharray="4 4" stroke="rgba(66,71,84,0.2)" />
                    <XAxis dataKey="step" stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }} />
                    <YAxis stroke="#424754" fontSize={10} tick={{ fill: '#8c909f' }}
                           domain={[0, 1]} tickFormatter={v => `${(v * 100).toFixed(0)}%`} />
                    <Tooltip contentStyle={TOOLTIP_STYLE}
                             formatter={(v: number) => [`${(v * 100).toFixed(1)}%`, 'SLO']} />
                    <Line type="monotone" dataKey="sloScore" name="SLO Score"
                          stroke="#3adfab" strokeWidth={2.5} dot={false}
                          style={{ filter: 'drop-shadow(0 0 4px #3adfab)' }} />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex flex-col items-center justify-center
                                border-2 border-dashed border-outline-variant/20 rounded-xl
                                bg-surface-lowest/50">
                  <span className="material-symbols-outlined text-4xl text-on-surface-variant/30 mb-3">analytics</span>
                  <p className="text-on-surface-variant/60 font-medium text-sm">Start training to see metrics</p>
                  <div className="mt-4 flex gap-1 opacity-20">
                    {[10, 6, 12, 8, 14, 11].map((h, i) => (
                      <div key={i} className="w-1 bg-primary rounded-full" style={{ height: h * 4 }} />
                    ))}
                  </div>
                </div>
              )}
            </div>
          </SectionCard>
        </div>

        {/* ── Status Row ──────────────────────────────────────────────── */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          {/* Status */}
          <div className="bg-surface-container rounded-2xl p-5 flex flex-col gap-3">
            <span className="text-xs text-on-surface-variant font-bold uppercase tracking-widest">Status</span>
            <div className="flex items-center gap-2">
              {isTraining && (
                <div className="relative w-2.5 h-2.5">
                  <span className="absolute inset-0 bg-primary rounded-full animate-ripple" />
                  <span className="relative block w-2.5 h-2.5 bg-primary rounded-full" />
                </div>
              )}
              <span className={`font-headline font-bold text-lg
                                ${isTraining ? 'text-primary' : 'text-on-surface-variant'}`}>
                {statusText}
              </span>
            </div>
          </div>
          {/* Reward */}
          <div className="bg-surface-container rounded-2xl p-5 flex flex-col gap-3">
            <span className="text-xs text-on-surface-variant font-bold uppercase tracking-widest">Current Reward</span>
            <span className={`text-2xl font-mono font-bold
                              ${currentReward >= 0 ? 'text-primary' : 'text-error'}`}>
              {currentReward >= 0 ? '+' : ''}{currentReward.toFixed(4)}
            </span>
          </div>
          {/* Difficulty */}
          <div className="bg-surface-container rounded-2xl p-5 flex flex-col gap-3">
            <span className="text-xs text-on-surface-variant font-bold uppercase tracking-widest">Difficulty</span>
            <span className="text-xl font-headline font-bold text-on-surface">{difficulty}</span>
          </div>
          {/* Episodes */}
          <div className="bg-surface-container rounded-2xl p-5 flex flex-col gap-3">
            <div className="flex justify-between items-center">
              <span className="text-xs text-on-surface-variant font-bold uppercase tracking-widest">Total Episodes</span>
              <span className="text-lg font-mono text-on-surface">{totalEpisodes}</span>
            </div>
            <div className="h-2 bg-surface-highest rounded-full overflow-hidden">
              <div className="h-full bg-secondary-container rounded-full transition-all duration-500"
                   style={{ width: `${Math.min(totalEpisodes, 100)}%` }} />
            </div>
          </div>
        </div>

        {/* ── Training Log ─────────────────────────────────────────────── */}
        <div>
          <div className="flex justify-between items-center mb-3">
            <h3 className="font-headline font-bold text-on-surface flex items-center gap-2">
              <span className="material-symbols-outlined text-primary">terminal</span>
              Training Log
            </h3>
            <GhostButton onClick={() => setLogs([])} danger>Clear Log</GhostButton>
          </div>
          <div
            ref={logRef}
            className="bg-[#0A0E14] rounded-2xl border border-outline-variant/20 p-6 h-56
                       overflow-y-auto font-mono text-sm leading-relaxed shadow-inner"
          >
            {logs.map((entry, i) => (
              <div key={i} className="flex gap-4 border-b border-white/5 pb-2 mb-2 last:border-0 last:mb-0">
                <span className="text-on-surface-variant/40 shrink-0 text-[11px]">[{entry.ts}]</span>
                <span className={`text-[11px] ${LOG_COLORS[entry.variant]}`}>{entry.text}</span>
              </div>
            ))}
            {isTraining && (
              <div className="flex gap-2 items-center mt-1">
                <span className="w-2 h-4 bg-primary/50 animate-blink" />
                <span className="text-[10px] text-on-surface-variant/30">TRAINING_ACTIVE</span>
              </div>
            )}
          </div>
        </div>

      </div>
    </Layout>
  );
}

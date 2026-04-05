/**
 * Training page with RL metrics and controls.
 */

import { useEffect, useState } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { useSimulationStore } from '@stores/simulationStore';
import Card from '@components/ui/Card';
import StatBox from '@components/ui/StatBox';
import Badge from '@components/ui/Badge';
import Button from '@components/ui/Button';
import LoadingSpinner from '@components/ui/LoadingSpinner';
import type { DifficultyLevel } from '@types/api';

interface TrainingMetrics {
  step: number;
  episodeReward: number;
  meanReward: number;
  sloScore: number;
}

export default function TrainingPage() {
  const { resetSimulation, stepSimulation, fetchTopology, getSystemHealth } = useSimulationStore();
  const [isTraining, setIsTraining] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [currentReward, setCurrentReward] = useState(0);
  const [episodeReward, setEpisodeReward] = useState(0);
  const [metricsHistory, setMetricsHistory] = useState<TrainingMetrics[]>([]);
  const [difficulty, setDifficulty] = useState<DifficultyLevel>('SIMPLE');

  const handleStartTraining = async () => {
    setIsTraining(true);
    await resetSimulation(difficulty);
    setCurrentStep(0);
    setEpisodeReward(0);
    setMetricsHistory([]);

    // Simulate training steps
    for (let i = 0; i < 50; i++) {
      const action = Math.floor(Math.random() * 9);
      const response = await stepSimulation(action);
      if (response) {
        setCurrentStep(i + 1);
        setCurrentReward(response.reward);
        setEpisodeReward((prev) => prev + response.reward);

        setMetricsHistory((prev) => [
          ...prev,
          {
            step: i + 1,
            episodeReward: episodeReward + response.reward,
            meanReward: (episodeReward + response.reward) / (i + 1),
            sloScore: response.sloScore,
          },
        ]);
      }
      await new Promise((resolve) => setTimeout(resolve, 200));
    }

    setIsTraining(false);
  };

  const handleReset = async () => {
    await resetSimulation(difficulty);
    setCurrentStep(0);
    setEpisodeReward(0);
    setMetricsHistory([]);
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-sentinel-text">Training Console</h1>
          <p className="text-sentinel-muted mt-1">RL agent training and evaluation</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value as DifficultyLevel)}
            className="bg-sentinel-card border border-sentinel-border text-sentinel-text rounded-md px-3 py-2 text-sm"
            disabled={isTraining}
          >
            <option value="SIMPLE">Simple</option>
            <option value="MEDIUM">Medium</option>
            <option value="COMPLEX">Complex</option>
          </select>
          <Button onClick={handleReset} variant="secondary" size="sm" disabled={isTraining}>
            Reset
          </Button>
          <Button onClick={handleStartTraining} isLoading={isTraining} variant="primary">
            Start Training
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <StatBox
          label="Current Step"
          value={currentStep}
          color="blue"
        />
        <StatBox
          label="Episode Reward"
          value={episodeReward.toFixed(3)}
          color={episodeReward > 0 ? 'green' : 'yellow'}
        />
        <StatBox
          label="System Health"
          value={(getSystemHealth() * 100).toFixed(1) + '%'}
          color={getSystemHealth() > 0.8 ? 'green' : 'red'}
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="Episode Reward" subtitle="Cumulative reward over steps">
          <div className="h-64">
            {metricsHistory.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={metricsHistory}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                  <XAxis dataKey="step" stroke="#9ca3af" fontSize={12} />
                  <YAxis stroke="#9ca3af" fontSize={12} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#111827',
                      border: '1px solid #1f2937',
                      borderRadius: '8px',
                    }}
                  />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="episodeReward"
                    stroke="#10b981"
                    strokeWidth={2}
                    dot={false}
                    name="Episode Reward"
                  />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-full text-sentinel-muted">
                Start training to see metrics
              </div>
            )}
          </div>
        </Card>

        <Card title="SLO Score" subtitle="Service level objective compliance">
          <div className="h-64">
            {metricsHistory.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={metricsHistory}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                  <XAxis dataKey="step" stroke="#9ca3af" fontSize={12} />
                  <YAxis stroke="#9ca3af" fontSize={12} domain={[0, 1]} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#111827',
                      border: '1px solid #1f2937',
                      borderRadius: '8px',
                    }}
                  />
                  <Line
                    type="monotone"
                    dataKey="sloScore"
                    stroke="#3b82f6"
                    strokeWidth={2}
                    dot={false}
                    name="SLO Score"
                  />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-full text-sentinel-muted">
                Start training to see metrics
              </div>
            )}
          </div>
        </Card>
      </div>

      {/* Action Distribution */}
      <Card title="Training Status" className="mt-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-4 bg-sentinel-dark rounded border border-sentinel-border">
            <p className="text-sm text-sentinel-muted">Status</p>
            <p className="text-lg font-semibold text-sentinel-text mt-1">
              {isTraining ? (
                <span className="flex items-center gap-2">
                  <LoadingSpinner size="sm" /> Training...
                </span>
              ) : (
                'Idle'
              )}
            </p>
          </div>
          <div className="p-4 bg-sentinel-dark rounded border border-sentinel-border">
            <p className="text-sm text-sentinel-muted">Current Reward</p>
            <p className="text-lg font-semibold text-sentinel-text mt-1">
              {currentReward.toFixed(4)}
            </p>
          </div>
          <div className="p-4 bg-sentinel-dark rounded border border-sentinel-border">
            <p className="text-sm text-sentinel-muted">Difficulty</p>
            <p className="text-lg font-semibold text-sentinel-text mt-1">{difficulty}</p>
          </div>
        </div>
      </Card>
    </div>
  );
}

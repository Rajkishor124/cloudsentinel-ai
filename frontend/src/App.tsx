/**
 * Main application component with routing.
 */

import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/layout/Layout';
import DashboardPage from './pages/DashboardPage';
import HealingPage from './pages/HealingPage';
import TrainingPage from './pages/TrainingPage';
import TopologyPage from './pages/TopologyPage';
import LoginPage from './components/auth/LoginPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<DashboardPage />} />
      <Route
        path="/healing"
        element={
          <Layout title="Self-Healing" subtitle="Autonomous failure detection & recovery">
            <HealingPage />
          </Layout>
        }
      />
      <Route
        path="/training"
        element={
          <Layout title="Training" subtitle="RL agent training & evaluation">
            <TrainingPage />
          </Layout>
        }
      />
      <Route
        path="/topology"
        element={
          <Layout title="Topology" subtitle="Service dependency graph">
            <TopologyPage />
          </Layout>
        }
      />
      <Route path="/dashboard" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;

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
      <Route
        path="/dashboard"
        element={
          <Layout>
            <DashboardPage />
          </Layout>
        }
      />
      <Route
        path="/healing"
        element={
          <Layout>
            <HealingPage />
          </Layout>
        }
      />
      <Route
        path="/training"
        element={
          <Layout>
            <TrainingPage />
          </Layout>
        }
      />
      <Route
        path="/topology"
        element={
          <Layout>
            <TopologyPage />
          </Layout>
        }
      />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}

export default App;

/**
 * Sidebar navigation component.
 */

import { Link, useLocation } from 'react-router-dom';
import { useHealingStore } from '@stores/healingStore';

interface SidebarProps {
  isOpen: boolean;
  onToggle: () => void;
}

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: '📊' },
  { path: '/healing', label: 'Self-Healing', icon: '🩹' },
  { path: '/training', label: 'Training', icon: '🧠' },
  { path: '/topology', label: 'Topology', icon: '🔗' },
];

export default function Sidebar({ isOpen, onToggle }: SidebarProps) {
  const location = useLocation();
  const alertCount = useHealingStore((state) => state.alerts.length);

  return (
    <aside
      className={`${
        isOpen ? 'w-64' : 'w-16'
      } bg-sentinel-card border-r border-sentinel-border transition-all duration-300 flex flex-col`}
    >
      {/* Logo */}
      <div className="p-4 border-b border-sentinel-border">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-sentinel-blue rounded flex items-center justify-center text-white font-bold">
            CS
          </div>
          {isOpen && (
            <div>
              <h1 className="text-lg font-bold text-sentinel-text">CloudSentinel</h1>
              <p className="text-xs text-sentinel-muted">AI Platform</p>
            </div>
          )}
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 p-3">
        <ul className="space-y-1">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <li key={item.path}>
                <Link
                  to={item.path}
                  className={`flex items-center gap-3 px-3 py-2 rounded-md transition-colors ${
                    isActive
                      ? 'bg-sentinel-blue/20 text-sentinel-blue'
                      : 'text-sentinel-muted hover:bg-sentinel-border hover:text-sentinel-text'
                  }`}
                >
                  <span className="text-lg">{item.icon}</span>
                  {isOpen && (
                    <>
                      <span className="flex-1">{item.label}</span>
                      {item.path === '/healing' && alertCount > 0 && (
                        <span className="bg-sentinel-red text-white text-xs rounded-full px-2 py-0.5">
                          {alertCount}
                        </span>
                      )}
                    </>
                  )}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Toggle button */}
      <button
        onClick={onToggle}
        className="p-3 border-t border-sentinel-border text-sentinel-muted hover:text-sentinel-text transition-colors"
      >
        <span className="flex items-center justify-center">
          {isOpen ? '← Collapse' : '→'}
        </span>
      </button>
    </aside>
  );
}

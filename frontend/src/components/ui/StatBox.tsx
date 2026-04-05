/**
 * StatBox component for displaying key metrics.
 */

import { ReactNode } from 'react';

interface StatBoxProps {
  label: string;
  value: string | number;
  icon?: ReactNode;
  trend?: 'up' | 'down' | 'stable';
  trendValue?: string;
  color?: 'green' | 'red' | 'yellow' | 'blue';
  className?: string;
}

export default function StatBox({
  label,
  value,
  icon,
  trend,
  trendValue,
  color = 'blue',
  className = '',
}: StatBoxProps) {
  const colorClasses = {
    green: 'text-sentinel-green',
    red: 'text-sentinel-red',
    yellow: 'text-sentinel-yellow',
    blue: 'text-sentinel-blue',
  };

  const trendIcons = {
    up: '↑',
    down: '↓',
    stable: '→',
  };

  const trendColors = {
    up: 'text-sentinel-green',
    down: 'text-sentinel-red',
    stable: 'text-sentinel-muted',
  };

  return (
    <div className={`bg-sentinel-card border border-sentinel-border rounded-lg p-4 ${className}`}>
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-sm text-sentinel-muted">{label}</p>
          <p className={`text-2xl font-bold mt-1 ${colorClasses[color]}`}>{value}</p>
          {trend && trendValue && (
            <div className="flex items-center gap-1 mt-2">
              <span className={`text-sm ${trendColors[trend]}`}>
                {trendIcons[trend]} {trendValue}
              </span>
            </div>
          )}
        </div>
        {icon && <div className="text-sentinel-muted">{icon}</div>}
      </div>
    </div>
  );
}

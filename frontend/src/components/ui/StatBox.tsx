/**
 * StatBox component for displaying key metrics.
 */

import { MetricCard } from './primitives';

interface StatBoxProps {
  label: string;
  value: string | number;
  icon?: string;
  trend?: 'up' | 'down' | 'stable';
  trendValue?: string;
  color?: 'green' | 'red' | 'yellow' | 'blue';
  className?: string;
}

const COLOR_MAP: Record<string, 'primary' | 'secondary' | 'tertiary' | 'error'> = {
  green: 'primary',
  red: 'error',
  yellow: 'tertiary',
  blue: 'secondary',
};

const TREND_ICONS: Record<string, string> = {
  up: 'trending_up',
  down: 'trending_down',
  stable: 'trending_flat',
};

export default function StatBox({
  label,
  value,
  icon,
  trend,
  trendValue,
  color = 'blue',
  className = '',
}: StatBoxProps) {
  const accent = COLOR_MAP[color];

  return (
    <MetricCard
      label={label}
      value={value}
      accent={accent}
      icon={icon}
      sub={trendValue}
      subIcon={trend ? TREND_ICONS[trend] : undefined}
      className={className}
    />
  );
}

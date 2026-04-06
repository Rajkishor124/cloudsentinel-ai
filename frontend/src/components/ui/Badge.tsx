/**
 * Badge component for status indicators.
 */

import { StatusBadge } from './primitives';
import type { ReactNode } from 'react';

interface BadgeProps {
  children: ReactNode;
  variant?: 'success' | 'warning' | 'danger' | 'info' | 'neutral';
  size?: 'sm' | 'md';
  className?: string;
}

export default function Badge({
  children,
  variant = 'neutral',
  size = 'sm',
  className = '',
}: BadgeProps) {
  return (
    <StatusBadge variant={variant} size={size}>
      {children}
    </StatusBadge>
  );
}

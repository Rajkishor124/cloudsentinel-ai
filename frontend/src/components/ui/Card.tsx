/**
 * Reusable Card component with consistent styling.
 */

import { ReactNode } from 'react';
import { SectionCard } from './primitives';

interface CardProps {
  children: ReactNode;
  className?: string;
  title?: string;
  subtitle?: string;
  action?: ReactNode;
}

export default function Card({
  children,
  className = '',
  title,
  subtitle,
  action,
}: CardProps) {
  return (
    <SectionCard title={title} headerRight={action} className={className}>
      {subtitle && (
        <p className="text-xs text-on-surface-variant -mt-2 mb-4">{subtitle}</p>
      )}
      {children}
    </SectionCard>
  );
}

/**
 * Badge component for status indicators.
 */

interface BadgeProps {
  children: React.ReactNode;
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
  const variantClasses = {
    success: 'bg-sentinel-green/20 text-sentinel-green border-sentinel-green/30',
    warning: 'bg-sentinel-yellow/20 text-sentinel-yellow border-sentinel-yellow/30',
    danger: 'bg-sentinel-red/20 text-sentinel-red border-sentinel-red/30',
    info: 'bg-sentinel-blue/20 text-sentinel-blue border-sentinel-blue/30',
    neutral: 'bg-sentinel-muted/20 text-sentinel-muted border-sentinel-muted/30',
  };

  const sizeClasses = {
    sm: 'text-xs px-2 py-0.5',
    md: 'text-sm px-3 py-1',
  };

  return (
    <span
      className={`inline-flex items-center border rounded font-medium ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
    >
      {children}
    </span>
  );
}

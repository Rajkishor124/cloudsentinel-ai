/**
 * AlertItem component for displaying anomaly alerts.
 */

import type { AnomalyAlert } from '../../types/api';
import { StatusBadge } from './primitives';

interface AlertItemProps {
  alert: AnomalyAlert;
  onAcknowledge?: (alertId: string) => void;
}

export default function AlertItem({ alert, onAcknowledge }: AlertItemProps) {
  const severityVariant: Record<string, 'info' | 'warning' | 'danger' | 'neutral'> = {
    INFO: 'info',
    LOW: 'info',
    MEDIUM: 'warning',
    HIGH: 'warning',
    CRITICAL: 'danger',
  };

  const severityBorder: Record<string, string> = {
    INFO: 'border-primary',
    LOW: 'border-primary',
    MEDIUM: 'border-warning',
    HIGH: 'border-warning',
    CRITICAL: 'border-error',
  };

  const formatValue = (value: number) => (value * 100).toFixed(1) + '%';

  return (
    <div
      className={`p-4 border-l-4 ${severityBorder[alert.severity]} bg-surface-container rounded-xl mb-3`}
    >
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
            <StatusBadge variant={severityVariant[alert.severity]}>
              {alert.severity}
            </StatusBadge>
            <span className="text-sm font-medium text-on-surface">
              {alert.anomalyType.replace(/_/g, ' ')}
            </span>
          </div>
          <p className="text-sm text-on-surface-variant mb-2">{alert.description}</p>
          <div className="flex items-center gap-4 text-xs text-on-surface-variant">
            <span>Service: {alert.serviceName}</span>
            <span>
              Value: {formatValue(alert.detectedValue)}
            </span>
            <span>
              Threshold: {formatValue(alert.threshold)}
            </span>
          </div>
          {alert.recommendedActions.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {alert.recommendedActions.map((action) => (
                <StatusBadge key={action} variant="info">
                  {action.replace(/_/g, ' ')}
                </StatusBadge>
              ))}
            </div>
          )}
        </div>
        {!alert.acknowledged && onAcknowledge && (
          <button
            onClick={() => onAcknowledge(alert.alertId)}
            className="ml-4 text-xs text-on-surface-variant hover:text-primary transition-colors"
          >
            Acknowledge
          </button>
        )}
      </div>
    </div>
  );
}

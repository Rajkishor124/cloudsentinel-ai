/**
 * AlertItem component for displaying anomaly alerts.
 */

import type { AnomalyAlert } from '@types/api';
import Badge from './Badge';

interface AlertItemProps {
  alert: AnomalyAlert;
  onAcknowledge?: (alertId: string) => void;
}

export default function AlertItem({ alert, onAcknowledge }: AlertItemProps) {
  const severityVariant = {
    INFO: 'info' as const,
    LOW: 'info' as const,
    MEDIUM: 'warning' as const,
    HIGH: 'warning' as const,
    CRITICAL: 'danger' as const,
  };

  const severityBorder = {
    INFO: 'border-sentinel-blue',
    LOW: 'border-sentinel-blue',
    MEDIUM: 'border-sentinel-yellow',
    HIGH: 'border-sentinel-yellow',
    CRITICAL: 'border-sentinel-red',
  };

  const formatValue = (value: number) => (value * 100).toFixed(1) + '%';

  return (
    <div
      className={`p-4 border-l-4 ${severityBorder[alert.severity]} bg-sentinel-card rounded-r-md mb-3`}
    >
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
            <Badge variant={severityVariant[alert.severity]}>
              {alert.severity}
            </Badge>
            <span className="text-sm font-medium text-sentinel-text">
              {alert.anomalyType.replace(/_/g, ' ')}
            </span>
          </div>
          <p className="text-sm text-sentinel-muted mb-2">{alert.description}</p>
          <div className="flex items-center gap-4 text-xs text-sentinel-muted">
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
                <Badge key={action} variant="info" size="sm">
                  {action.replace(/_/g, ' ')}
                </Badge>
              ))}
            </div>
          )}
        </div>
        {!alert.acknowledged && onAcknowledge && (
          <button
            onClick={() => onAcknowledge(alert.alertId)}
            className="ml-4 text-xs text-sentinel-muted hover:text-sentinel-text transition-colors"
          >
            Acknowledge
          </button>
        )}
      </div>
    </div>
  );
}

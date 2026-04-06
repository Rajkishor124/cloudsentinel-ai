/**
 * Button component with variants.
 */

import { ButtonHTMLAttributes, ReactNode } from 'react';
import { GhostButton, PrimaryButton } from './primitives';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
}

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  className = '',
  disabled,
  onClick,
  ...props
}: ButtonProps) {
  if (variant === 'ghost' || variant === 'secondary') {
    return (
      <GhostButton
        onClick={onClick as () => void}
        disabled={disabled || isLoading}
        className={className}
        icon={isLoading ? 'refresh' : undefined}
      >
        {children}
      </GhostButton>
    );
  }

  return (
    <PrimaryButton
      onClick={onClick as () => void}
      loading={isLoading}
      disabled={disabled}
      color={variant === 'danger' ? 'error' : 'primary'}
    >
      {children}
    </PrimaryButton>
  );
}

import type { ReactNode } from 'react';



// ─── MetricCard ───────────────────────────────────────────────────────────────

type Accent = 'primary' | 'secondary' | 'tertiary' | 'error';



const ACCENT_MAP: Record<Accent, { text: string; border: string; trend: string; dot: string }> = {

  primary:   { text: 'text-primary',   border: 'hover:border-primary/30',   trend: 'text-primary',   dot: 'bg-primary' },

  secondary: { text: 'text-secondary', border: 'hover:border-secondary/30', trend: 'text-on-surface-variant', dot: 'bg-secondary' },

  tertiary:  { text: 'text-tertiary',  border: 'hover:border-tertiary/30',  trend: 'text-tertiary',  dot: 'bg-tertiary' },

  error:     { text: 'text-error',     border: 'hover:border-error/30',     trend: 'text-error',     dot: 'bg-error' },

};



interface MetricCardProps {

  label: string;

  value: string | number;

  accent?: Accent;

  icon?: string;

  sub?: string;

  subIcon?: string;

  pulse?: boolean;

  className?: string;

}



export function MetricCard({

  label, value, accent = 'primary', icon, sub, subIcon, pulse, className = '',

}: MetricCardProps) {

  const c = ACCENT_MAP[accent];

  return (

    <div className={`bg-surface-container p-6 rounded-2xl border border-outline-variant/10

                     ${c.border} transition-all duration-200 group ${className}`}>

      <div className="flex items-center justify-between mb-4">

        <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">

          {label}

        </span>

        {pulse ? (

          <div className={`w-2 h-2 rounded-full ${c.dot} animate-pulse`} />

        ) : icon ? (

          <span className={`material-symbols-outlined text-sm ${c.text}`}>{icon}</span>

        ) : null}

      </div>



      <p className={`text-4xl font-headline font-black ${c.text} leading-none`}>{value}</p>



      {sub && (

        <div className={`mt-4 flex items-center gap-2 text-[10px] ${c.trend}`}>

          {subIcon && <span className="material-symbols-outlined text-xs">{subIcon}</span>}

          <span>{sub}</span>

        </div>

      )}

    </div>

  );

}



// ─── SectionCard ─────────────────────────────────────────────────────────────

interface SectionCardProps {

  title?: string;

  headerRight?: ReactNode;

  noPad?: boolean;

  className?: string;

  children: ReactNode;

}



export function SectionCard({ title, headerRight, noPad, className = '', children }: SectionCardProps) {

  return (

    <div className={`bg-surface-container rounded-2xl border border-outline-variant/10 overflow-hidden ${className}`}>

      {title && (

        <div className="px-6 py-5 border-b border-outline-variant/5 flex items-center justify-between">

          <h3 className="font-headline font-bold text-on-surface">{title}</h3>

          {headerRight}

        </div>

      )}

      <div className={noPad ? '' : 'p-6'}>{children}</div>

    </div>

  );

}



// ─── StatusBadge ─────────────────────────────────────────────────────────────

type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral';



const BADGE_MAP: Record<BadgeVariant, string> = {

  success: 'bg-primary/10 text-primary border-primary/20',

  warning: 'bg-tertiary/10 text-tertiary border-tertiary/20',

  danger:  'bg-error/10 text-error border-error/20',

  info:    'bg-secondary/10 text-secondary border-secondary/20',

  neutral: 'bg-surface-highest text-on-surface-variant border-outline-variant/20',

};



interface StatusBadgeProps {

  variant?: BadgeVariant;

  children: ReactNode;

  size?: 'sm' | 'md';

}



export function StatusBadge({ variant = 'neutral', children, size = 'md' }: StatusBadgeProps) {

  return (

    <span className={`inline-flex items-center font-bold uppercase tracking-wide

                      border rounded

                      ${size === 'sm' ? 'px-2 py-0.5 text-[10px]' : 'px-2.5 py-1 text-[10px]'}

                      ${BADGE_MAP[variant]}`}>

      {children}

    </span>

  );

}



// ─── GhostButton ─────────────────────────────────────────────────────────────

interface GhostButtonProps {

  onClick?: () => void;

  icon?: string;

  children?: ReactNode;

  danger?: boolean;

  disabled?: boolean;

  className?: string;

}



export function GhostButton({ onClick, icon, children, danger, disabled, className = '' }: GhostButtonProps) {

  return (

    <button

      onClick={onClick}

      disabled={disabled}

      className={`flex items-center gap-2 px-4 py-2.5 rounded-xl border text-xs font-bold

                  uppercase tracking-tight transition-colors disabled:opacity-40

                  ${danger

                    ? 'border-error/30 text-error hover:bg-error/10'

                    : 'border-outline-variant/30 text-on-surface-variant hover:bg-surface-high hover:text-on-surface'

                  } ${className}`}

    >

      {icon && <span className="material-symbols-outlined text-sm">{icon}</span>}

      {children}

    </button>

  );

}



// ─── PrimaryButton ───────────────────────────────────────────────────────────

interface PrimaryButtonProps {

  onClick?: () => void;

  icon?: string;

  loading?: boolean;

  disabled?: boolean;

  color?: 'primary' | 'purple' | 'error';

  children: ReactNode;

}



const PRIMARY_COLOR: Record<string, string> = {

  primary: 'bg-gradient-to-br from-primary to-primary-container text-on-primary shadow-glow-primary',

  purple:  'bg-[#8B5CF6] hover:bg-[#7c4dff] text-white shadow-[0_4px_15px_rgba(139,92,246,0.25)]',

  error:   'bg-error-container text-on-error-container',

};



export function PrimaryButton({ onClick, icon, loading, disabled, color = 'primary', children }: PrimaryButtonProps) {

  return (

    <button

      onClick={onClick}

      disabled={disabled || loading}

      className={`flex items-center gap-2 px-6 py-2.5 rounded-xl font-bold text-xs

                  transition-transform active:scale-95 disabled:opacity-50

                  ${PRIMARY_COLOR[color]}`}

    >

      {loading ? (

        <span className="material-symbols-outlined text-sm animate-spin">refresh</span>

      ) : icon ? (

        <span className="material-symbols-outlined text-sm icon-filled">{icon}</span>

      ) : null}

      {children}

    </button>

  );

}



// ─── InjectButton ─────────────────────────────────────────────────────────────

type InjectVariant = 'amber' | 'orange' | 'red';



const INJECT_MAP: Record<InjectVariant, string> = {

  amber:  'border-tertiary/30 text-tertiary hover:bg-tertiary/10',

  orange: 'border-tertiary-container/30 text-tertiary-container hover:bg-tertiary-container/10',

  red:    'border-error/30 text-error hover:bg-error/10',

};



export function InjectButton({

  label, icon, variant, onClick,

}: { label: string; icon: string; variant: InjectVariant; onClick?: () => void }) {

  return (

    <button

      onClick={onClick}

      className={`flex items-center gap-2 px-4 py-3 border rounded-xl

                  text-xs font-bold uppercase tracking-tight transition-colors

                  ${INJECT_MAP[variant]}`}

    >

      <span className="material-symbols-outlined text-sm">{icon}</span>

      {label}

    </button>

  );

}



// ─── EmptyState ───────────────────────────────────────────────────────────────

export function EmptyState({ icon, title, sub }: { icon: string; title: string; sub?: string }) {

  return (

    <div className="flex flex-col items-center justify-center py-12 text-center">

      <div className="w-14 h-14 bg-surface-high rounded-full flex items-center justify-center mb-4

                      border border-outline-variant/10">

        <span className="material-symbols-outlined text-3xl text-on-surface-variant">{icon}</span>

      </div>

      <p className="font-headline font-bold text-on-surface mb-1">{title}</p>

      {sub && <p className="text-sm text-on-surface-variant">{sub}</p>}

    </div>

  );

}



// ─── Spinner ─────────────────────────────────────────────────────────────────

export function Spinner({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {

  const sz = { sm: 'text-base', md: 'text-2xl', lg: 'text-4xl' }[size];

  return (

    <span className={`material-symbols-outlined animate-spin text-primary ${sz}`}>refresh</span>

  );

}
